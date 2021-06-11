/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.tasks.handlers.iterative;

import com.evolveum.midpoint.repo.common.task.*;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.repo.common.tasks.handlers.MockRecorder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractActivityWorkStateType;

import org.jetbrains.annotations.NotNull;

/**
 * TODO
 */
class IterativeMockActivityExecution
        extends AbstractIterativeActivityExecution<Integer, IterativeMockWorkDefinition, IterativeMockActivityHandler,
        AbstractActivityWorkStateType> {

    private static final Trace LOGGER = TraceManager.getTrace(IterativeMockActivityExecution.class);

    IterativeMockActivityExecution(@NotNull ExecutionInstantiationContext<IterativeMockWorkDefinition, IterativeMockActivityHandler> context) {
        super(context, "Iterative Mock Activity");
    }

    @Override
    public @NotNull ActivityReportingOptions getDefaultReportingOptions() {
        return new ActivityReportingOptions();
    }

    @Override
    protected void processItems(OperationResult result) throws CommonException {
        IterativeMockWorkDefinition workDef = getActivity().getWorkDefinition();
        for (int item = workDef.getFrom(); item <= workDef.getTo(); item++) {
            ItemProcessingRequest<Integer> request = new IterativeMockProcessingRequest(item, this);
            coordinator.submit(request, result);
        }
    }

    @Override
    protected @NotNull ItemProcessor<Integer> createItemProcessor(OperationResult opResult) {
        return (request, workerTask, parentResult) -> {
            String message = activity.getWorkDefinition().getMessage() + request.getItem();
            LOGGER.info("Message: {}", message);
            getRecorder().recordExecution(message);
            return true;
        };
    }

    @Override
    public boolean providesTracingAndDynamicProfiling() {
        return false;
    }

    @Override
    @NotNull
    protected ErrorHandlingStrategyExecutor.FollowUpAction getDefaultErrorAction() {
        return ErrorHandlingStrategyExecutor.FollowUpAction.CONTINUE;
    }

    @Override
    public void debugDumpExtra(StringBuilder sb, int indent) {
        DebugUtil.debugDumpWithLabel(sb, "current recorder state", getRecorder(), indent+1);
    }

    @NotNull
    private MockRecorder getRecorder() {
        return activity.getHandler().getRecorder();
    }

}