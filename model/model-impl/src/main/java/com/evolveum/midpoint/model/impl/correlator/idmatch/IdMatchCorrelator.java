/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.correlator.idmatch;

import com.evolveum.midpoint.model.api.correlator.*;
import com.evolveum.midpoint.model.api.correlator.idmatch.IdMatchService;
import com.evolveum.midpoint.model.api.correlator.idmatch.MatchingResult;
import com.evolveum.midpoint.model.api.correlator.idmatch.PotentialMatch;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.correlator.CorrelatorUtil;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Collection;

import static com.evolveum.midpoint.util.MiscUtil.argCheck;
import static com.evolveum.midpoint.util.MiscUtil.configCheck;
import static com.evolveum.midpoint.util.QNameUtil.qNameToUri;
import static com.evolveum.midpoint.util.QNameUtil.uriToQName;

/**
 * A correlator based on an external service providing ID Match API.
 * (https://spaces.at.internet2.edu/display/cifer/SOR-Registry+Strawman+ID+Match+API)
 */
class IdMatchCorrelator implements Correlator {

    private static final Trace LOGGER = TraceManager.getTrace(IdMatchCorrelator.class);

    /**
     * Configuration of the this correlator.
     */
    @SuppressWarnings({ "FieldCanBeLocal", "unused" }) // temporary
    @NotNull private final IdMatchCorrelatorType configuration;

    /**
     * Configuration of a follow-on correlator (used to find real account owner based on matched identity).
     */
    @NotNull private final CorrelatorConfiguration followOnCorrelatorConfiguration;

    /**
     * Service that resolves "reference ID" for resource objects being correlated.
     */
    @NotNull private final IdMatchService service;

    private final ModelBeans beans;

    /**
     * @param serviceOverride An instance of {@link IdMatchService} that should be used instead of the default one.
     *                        Used for unit testing.
     */
    IdMatchCorrelator(
            @NotNull IdMatchCorrelatorType configuration,
            @Nullable IdMatchService serviceOverride,
            ModelBeans beans) throws ConfigurationException {
        this.configuration = configuration;
        this.service = instantiateService(configuration, serviceOverride);
        this.beans = beans;

        this.followOnCorrelatorConfiguration = getFollowOnConfiguration(configuration);

        LOGGER.trace("Instantiated the correlator with the configuration:\n{}", configuration.debugDumpLazily(1));
    }

    @NotNull
    private IdMatchService instantiateService(
            @NotNull IdMatchCorrelatorType configuration, @Nullable IdMatchService serviceOverride)
            throws ConfigurationException {
        if (serviceOverride != null) {
            return serviceOverride;
        } else if (TemporaryIdMatchServiceImpl.URL.equals(configuration.getUrl())) {
            return TemporaryIdMatchServiceImpl.INSTANCE;
        } else {
            return IdMatchServiceImpl.instantiate(configuration);
        }
    }

    private CorrelatorConfiguration getFollowOnConfiguration(@NotNull IdMatchCorrelatorType configuration)
            throws ConfigurationException {
        configCheck(configuration.getFollowOn() != null,
                "No 'follow on' correlator configured in %s", configuration);
        Collection<CorrelatorConfiguration> followOnConfigs = CorrelatorUtil.getConfigurations(configuration.getFollowOn());
        configCheck(followOnConfigs.size() == 1, "Not a single 'follow on' correlator configured: %s",
                followOnConfigs);
        return followOnConfigs.iterator().next();
    }

    @Override
    public CorrelationResult correlate(@NotNull ShadowType resourceObject, @NotNull CorrelationContext correlationContext,
            @NotNull Task task, @NotNull OperationResult result) throws ConfigurationException, SchemaException,
            ExpressionEvaluationException, CommunicationException, SecurityViolationException, ObjectNotFoundException {

        LOGGER.trace("Correlating the resource object:\n{}", resourceObject.debugDumpLazily(1));

        MatchingResult mResult = service.executeMatch(resourceObject.getAttributes(), result);
        LOGGER.trace("Matching result:\n{}", mResult.debugDumpLazily(1));

        IdMatchCorrelationStateType correlationState = createCorrelationState(mResult);
        correlationContext.setCorrelationState(correlationState);
        resourceObject.setCorrelationState(correlationState.clone()); // we also need the state in the shadow in the case object

        if (mResult.getReferenceId() != null) {
            beans.correlationCaseManager.closeCaseIfExists(resourceObject, result);
            return correlateUsingKnownReferenceId(resourceObject, correlationContext, task, result);
        } else {
            beans.correlationCaseManager.createOrUpdateCase(
                    resourceObject,
                    createSpecificCaseContext(mResult, resourceObject),
                    result);
            return CorrelationResult.uncertain();
        }
    }

    private @NotNull IdMatchCorrelationStateType createCorrelationState(MatchingResult mResult) {
        IdMatchCorrelationStateType state = new IdMatchCorrelationStateType(PrismContext.get());
        state.setReferenceId(mResult.getReferenceId());
        state.setMatchRequestId(mResult.getMatchRequestId());
        return state;
    }

    private CorrelationResult correlateUsingKnownReferenceId(
            ShadowType resourceObject, CorrelationContext correlationContext, Task task, OperationResult result)
            throws ConfigurationException, SchemaException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {

        return beans.correlatorFactoryRegistry
                .instantiateCorrelator(followOnCorrelatorConfiguration, task, result)
                .correlate(resourceObject, correlationContext, task, result);
    }

    /**
     * Converts internal {@link MatchingResult} into "externalized" {@link IdMatchCorrelationContextType} bean
     * to be stored in the correlation case.
     *
     * _Temporarily_ adding also "none of the above" potential match here. (If it is not present among options returned
     * from the ID Match service.)
     */
    private @NotNull IdMatchCorrelationContextType createSpecificCaseContext(
            @NotNull MatchingResult mResult, @NotNull ShadowType resourceObject) {
        IdMatchCorrelationContextType context = new IdMatchCorrelationContextType(PrismContext.get());
        boolean newIdentityOptionPresent = false;
        for (PotentialMatch potentialMatch : mResult.getPotentialMatches()) {
            if (potentialMatch.isNewIdentity()) {
                newIdentityOptionPresent = true;
            }
            context.getPotentialMatch().add(
                    createPotentialMatchBeanFromReturnedMatch(potentialMatch));
        }
        if (!newIdentityOptionPresent) {
            context.getPotentialMatch().add(
                    createPotentialMatchBeanForNewIdentity(resourceObject));
        }
        return context;
    }

    private IdMatchCorrelationPotentialMatchType createPotentialMatchBeanFromReturnedMatch(PotentialMatch potentialMatch) {
        @Nullable String id = potentialMatch.getReferenceId();
        String optionUri = id != null ?
                qNameToUri(new QName(SchemaConstants.CORRELATION_NS, SchemaConstants.CORRELATION_OPTION_PREFIX + id)) :
                SchemaConstants.CORRELATION_NONE_URI;
        return new IdMatchCorrelationPotentialMatchType(PrismContext.get())
                .uri(optionUri)
                .confidence(potentialMatch.getConfidence())
                .referenceId(id)
                .attributes(potentialMatch.getAttributes());
    }

    private IdMatchCorrelationPotentialMatchType createPotentialMatchBeanForNewIdentity(@NotNull ShadowType resourceObject) {
        return new IdMatchCorrelationPotentialMatchType(PrismContext.get())
                .uri(SchemaConstants.CORRELATION_NONE_URI)
                .attributes(
                        CloneUtil.clone(resourceObject.getAttributes()));
    }

    @Override
    public void resolve(
            @NotNull PrismObject<CaseType> aCase,
            @NotNull AbstractWorkItemOutputType output,
            @NotNull Task task,
            @NotNull OperationResult result) throws SchemaException, CommunicationException {
        ShadowType shadow = CorrelatorUtil.getShadowFromCorrelationCase(aCase);
        ShadowAttributesType attributes = MiscUtil.requireNonNull(shadow.getAttributes(),
                () -> new IllegalStateException("No attributes in shadow " + shadow + " in " + aCase));
        IdMatchCorrelationStateType state = MiscUtil.requireNonNull(
                MiscUtil.castSafely(shadow.getCorrelationState(), IdMatchCorrelationStateType.class),
                () -> new IllegalStateException("No correlation state in shadow " + shadow + " in " + aCase));
        String matchRequestId = state.getMatchRequestId();
        String correlatedReferenceId = getCorrelatedReferenceId(aCase, output);

        service.resolve(attributes, matchRequestId, correlatedReferenceId, result);
    }

    private String getCorrelatedReferenceId(PrismObject<CaseType> aCase, AbstractWorkItemOutputType output)
            throws SchemaException {
        String outcomeUri = output.getOutcome();
        argCheck(outcomeUri != null, "No outcome URI for %s", aCase);
        String outcome = uriToQName(outcomeUri, true).getLocalPart();
        if (SchemaConstants.CORRELATION_NONE.equals(outcome)) {
            return null;
        } else if (outcome.startsWith(SchemaConstants.CORRELATION_OPTION_PREFIX)) {
            return outcome.substring(SchemaConstants.CORRELATION_OPTION_PREFIX.length());
        } else {
            throw new SchemaException("Unsupported outcome URI: " + outcomeUri);
        }
    }
}
