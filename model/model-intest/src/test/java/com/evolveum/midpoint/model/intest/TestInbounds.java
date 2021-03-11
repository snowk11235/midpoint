/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.intest;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author katkav
 * @author semancik
 */
@ContextConfiguration(locations = { "classpath:ctx-model-intest-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestInbounds extends AbstractInitializedModelIntegrationTest {

    public static final File TEST_DIR = new File("src/test/resources/contract");

    private String jackEmployeeNumber;
    private String guybrushShadowOrangeOid;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.RELATIVE);
        setDefaultUserTemplate(USER_TEMPLATE_INBOUNDS_OID);
        assumeResourceAssigmentPolicy(RESOURCE_DUMMY_GREEN_OID, AssignmentPolicyEnforcementType.RELATIVE, false);
    }

    @Test
    public void test000Sanity() throws Exception {
        // GIVEN
        Task task = getTestTask();

        // WHEN
        when();
        OperationResult testResult = modelService.testResource(RESOURCE_DUMMY_ORANGE_OID, task);

        // THEN
        then();
        TestUtil.assertSuccess("Test orange resource", testResult);

        DummyResource dummyResourceOrange = getDummyResource(RESOURCE_DUMMY_ORANGE_NAME);
        assertEquals("Wrong ORANGE useless string", IntegrationTestTools.CONST_USELESS, dummyResourceOrange.getUselessString());
    }

    @Test
    public void test101ModifyUserSubtypePirate() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object().createModificationReplaceProperty(UserType.class,
                USER_JACK_OID, UserType.F_SUBTYPE, "PIRATE");
        // Make sure that the user has no employeeNumber so it will be generated by userTemplate
        userDelta.addModificationReplaceProperty(UserType.F_EMPLOYEE_NUMBER);
        userDelta.addModificationAddProperty(SchemaConstants.PATH_ACTIVATION_VALID_FROM, XmlTypeConverter
                .createXMLGregorianCalendar(System.currentTimeMillis()));

        // WHEN
        when();
        executeChanges(userDelta, null, task, result);

        // THEN
        then();
        assertSuccess(result);

        // @formatter:off
        jackEmployeeNumber = assertUserAfter(USER_JACK_OID)
                .assertDescription("Where's the rum?")
                .assignments()
                    .single()
                        .assertRole(ROLE_PIRATE_GREEN_OID)
                        .metadata()
                            .assertOriginMappingName("pirate-assignment")
                            .end()
                        .end()
                    .end()
                .assertLiveLinks(1)
                .assertOrganizationalUnits("The crew of pirate")
                .assertEmployeeNumber()
                .getObject().asObjectable().getEmployeeNumber();
        // @formatter:on
    }

    /**
     * Switch subtype from PIRATE to BUCCANEER. This makes one condition to go false and the other to go
     * true. For the same role assignment value. So nothing should be changed.
     */
    @Test
    public void test102ModifyUserSubtypeBuccaneer() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object().createModificationReplaceProperty(UserType.class,
                USER_JACK_OID, UserType.F_SUBTYPE, "BUCCANEER");

        // WHEN
        when();
        executeChanges(userDelta, null, task, result);

        // THEN
        then();
        assertSuccess(result);

        // @formatter:off
        assertUserAfter(USER_JACK_OID)
                .assertDescription("Where's the rum?")
                .assignments()
                    .single()
                        .assertRole(ROLE_BUCCANEER_GREEN_OID)
                        .metadata()
                            .assertOriginMappingName("buccaneer-assignment")
                            .end()
                        .end()
                    .end()
                .assertLiveLinks(1)
                .assertOrganizationalUnits("The crew of buccaneer")
                .assertEmployeeNumber(jackEmployeeNumber);
        // @formatter:on
    }

    @Test
    public void test103DeleteUserEmployeeTypeBartender() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
        ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object().createModificationDeleteProperty(UserType.class,
                USER_JACK_OID, UserType.F_SUBTYPE, "BUCCANEER");
        deltas.add(userDelta);

        // WHEN
        modelService.executeChanges(deltas, null, task, result);

        // THEN
        PrismObject<UserType> userJack = modelService.getObject(UserType.class, USER_JACK_OID, null, task, result);
        display("User after", userJack);

        PrismAsserts.assertPropertyValue(userJack, UserType.F_DESCRIPTION, "Where's the rum?");
        assertNotAssignedRole(userJack, ROLE_PIRATE_GREEN_OID);
        assertNotAssignedRole(userJack, ROLE_BUCCANEER_GREEN_OID);
        assertNoAssignments(userJack);

        UserType userJackType = userJack.asObjectable();
        assertEquals("Unexpected number of accountRefs", 0, userJackType.getLinkRef().size());

        result.computeStatus();
        TestUtil.assertSuccess(result);

        assertEquals("Employee number has changed", jackEmployeeNumber, userJackType.getEmployeeNumber());
    }

    /**
     * Not much happens here. Just ordinary account assign. Just make sure
     * that the inbound mappings do not fail for empty values and that
     * we have a good environment for the following tests.
     * MID-2689
     */
    @Test
    public void test200AssignAccountOrange() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        when();
        assignAccountToUser(USER_GUYBRUSH_OID, RESOURCE_DUMMY_ORANGE_OID, null, task, result);

        // THEN
        then();
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertLiveLinks(userAfter, 2);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Modify 'gossip' on account (through shadow). That attribute has an inbound
     * expression that creates an assignment. Make sure it is processed properly.
     * MID-2689
     */
    @Test
    public void test202ModifyAccountOrangeGossip() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        modifyObjectAddProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME),
                task, result, ROLE_PIRATE_OID);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        AssignmentType pirateAssignment = assertAssignedRole(userAfter, ROLE_PIRATE_OID);
        assertEquals("Wrong originMappingName", "gossip-inbound", pirateAssignment.getMetadata().getOriginMappingName());
        assertAssignments(userAfter, 2);
        assertLiveLinks(userAfter, 2);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Discovered by accident. Just make sure that another change will not destroy anything.
     * MID-3080
     */
    @Test
    public void test204AssignAccountOrangeAgain() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        assignAccountToUser(USER_GUYBRUSH_OID, RESOURCE_DUMMY_ORANGE_OID, null, task, result);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_PIRATE_OID);
        assertAssignments(userAfter, 2);
        assertLiveLinks(userAfter, 2);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Remove the value of 'gossip' attribute on account (through shadow).
     * That attribute has an inbound expression that removes an assignment.
     * Make sure it is processed properly.
     * MID-2689
     */
    @Test
    public void test209ModifyAccountOrangeGossipRemove() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        modifyObjectDeleteProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME),
                task, result, ROLE_PIRATE_OID);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertLiveLinks(userAfter, 2);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Modify 'quote' on account (through shadow). That attribute has an inbound
     * expression that passes some values to description user property.
     * This will not pass.
     * MID-2421
     */
    @Test
    public void test210ModifyAccountOrangeQuoteMonkey() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        modifyObjectReplaceProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME),
                task, result, "Look behind you, a Three-Headed Monkey!");

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertNull("Wrong description", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertLiveLinks(userAfter, 2);

        // Second inbound expression should work here
        PrismAsserts.assertPropertyValue(userAfter, getExtensionPath(PIRACY_TALES), "Look behind you, a Three-Headed Monkey!");

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Modify 'quote' on account (through shadow). That attribute has an inbound
     * expression that passes some values to description user property.
     * This should pass.
     * MID-2421
     */
    @Test
    public void test211ModifyAccountOrangeQuotePirate() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        modifyObjectReplaceProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME),
                task, result, "I wanna be a pirrrrrrate!");

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertEquals("Wrong description", "I wanna be a pirrrrrrate!", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertLiveLinks(userAfter, 2);

        // Second inbound expression should work here
        PrismAsserts.assertPropertyValue(userAfter, getExtensionPath(PIRACY_TALES), "I wanna be a pirrrrrrate!");

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Modify 'quote' on account (through shadow). That attribute has an inbound
     * expression that passes some values to description user property.
     * This will not pass. Is should remove the previous value of description.
     * MID-2421
     */
    @Test
    public void test214ModifyAccountOrangeQuoteWoodchuck() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        // WHEN
        modifyObjectReplaceProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME),
                task, result, "How much wood could a woodchuck chuck if a woodchuck could chuck wood?");

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertNull("Wrong description", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertLiveLinks(userAfter, 2);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Not much happens here. We just need to remove the dummy account to avoid
     * inbound expression interferences. We wanted to have the dummy account in
     * text20x because these were relative. But now it will ruin the setup.
     */
    @Test
    public void test250UnlinkAccountDefaultDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        ObjectDelta<UserType> unlinkDelta = createModifyUserUnlinkAccount(USER_GUYBRUSH_OID, getDummyResourceObject());

        // WHEN
        modelService.executeChanges(MiscSchemaUtil.createCollection(unlinkDelta), null, task, result);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedNoRole(userAfter);
        assertAssignments(userAfter, 1);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);

    }

    /**
     * Similar to test202ModifyAccountOrangeGossip, but uses direct account modification
     * and reconciliation.
     * MID-2689
     */
    @Test
    public void test252ModifyAccountOrangeGossipRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME,
                ROLE_THIEF_OID);
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        traced(() -> reconcileUser(USER_GUYBRUSH_OID, task, result));

        // THEN
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 2);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Similar to test209ModifyAccountOrangeGossipRemove, but uses direct account modification
     * and reconciliation.
     * MID-3080
     */
    @Test
    public void test259ModifyAccountOrangeGossipRemoveRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME /* no value */);
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        reconcileUser(USER_GUYBRUSH_OID, task, result);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        // The inbound mapping is tolerant. It will NOT remove the value.
        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 2);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Similar to test210ModifyAccountOrangeQuoteMonkey, but uses direct account modification
     * and reconciliation.
     * MID-2421
     */
    @Test
    public void test260ModifyAccountOrangeQuoteMonkeyRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);
        assertNull("Wrong description", userBefore.asObjectable().getDescription());

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "Look behind you, a Three-Headed Monkey!");
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        reconcileUser(USER_GUYBRUSH_OID, task, result);

        // WHEN
        modifyObjectReplaceProperty(ShadowType.class, guybrushShadowOrangeOid,
                getDummyResourceController(RESOURCE_DUMMY_ORANGE_NAME).getAttributePath(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME),
                task, result, "Look behind you, a Three-Headed Monkey!");

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertNull("Wrong description", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 2);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Similar to test211ModifyAccountOrangeQuotePirate, but uses direct account modification
     * and reconciliation.
     * MID-2421
     */
    @Test
    public void test261ModifyAccountOrangeQuotePirateRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "I wanna be a pirrrrrrate!");
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        reconcileUser(USER_GUYBRUSH_OID, task, result);

        // THEN
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertEquals("Wrong description", "I wanna be a pirrrrrrate!", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 2);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Account orange after", dummyAccount);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Similar to test214ModifyAccountOrangeQuoteWoodchuck, but uses direct account modification
     * and reconciliation.
     * MID-2421
     */
    @Test
    public void test264ModifyAccountOrangeQuoteWoodchuckRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "How much wood could a woodchuck chuck if a woodchuck could chuck wood?");
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        when();
        reconcileUser(USER_GUYBRUSH_OID, task, result);

        // THEN
        then();
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertNull("Wrong description", userAfter.asObjectable().getDescription());

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 2);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    /**
     * Cleanup
     */
    @Test
    public void test297ModifyAccountOrangeGossipRecon() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);

        DummyAccount dummyAccountBefore = getDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);
        dummyAccountBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME /* no value */);
        displayDumpable("Account orange before", dummyAccountBefore);

        // WHEN
        when();
        unassignRole(USER_GUYBRUSH_OID, ROLE_THIEF_OID, task, result);

        // THEN
        then();
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);

        assertAssignedAccount(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        assertNotAssignedRole(userAfter, ROLE_THIEF_OID);
        assertAssignments(userAfter, 1);
        assertUser(userAfter, "after")
                .assertLinks(1, 1);

        DummyAccount dummyAccount = assertDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME, USER_GUYBRUSH_FULL_NAME, true);
        displayDumpable("Orange account", dummyAccount);

        guybrushShadowOrangeOid = getLiveLinkRefOid(userAfter, RESOURCE_DUMMY_ORANGE_OID);
        PrismObject<ShadowType> shadowOrange = getShadowModel(guybrushShadowOrangeOid);
        display("Orange shadow", shadowOrange);
    }

    @Test
    public void test299UnassignAccountOrange() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);
        assertAssignments(userBefore, 1);

        // WHEN
        when();
        unassignAccountFromUser(USER_GUYBRUSH_OID, RESOURCE_DUMMY_ORANGE_OID, null, task, result);

        // THEN
        then();
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertUser(userAfter, USER_GUYBRUSH_OID, USER_GUYBRUSH_USERNAME,
                USER_GUYBRUSH_FULL_NAME, USER_GUYBRUSH_GIVEN_NAME, USER_GUYBRUSH_FAMILY_NAME);
        assertAssignments(userAfter, 0);
        assertUser(userAfter, "after")
                .assertLinks(0, 1);

        assertNoDummyAccount(RESOURCE_DUMMY_ORANGE_NAME, USER_GUYBRUSH_USERNAME);

        assertNoShadow(guybrushShadowOrangeOid);
    }

}
