/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.intest.tasks;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.intest.AbstractEmptyModelIntegrationTest;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.TestResource;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TestCleanupTask extends AbstractEmptyModelIntegrationTest {

    public static final File TEST_DIR = new File("src/test/resources/tasks/cleanup");

    private static final File FILE_OBJECTS_TO_BE_CLEANED_UP = new File(TEST_DIR, "objects-to-be-cleaned-up.xml");

    private static final TestResource<RoleType> ROLE_LIMITED = new TestResource<>(TEST_DIR, "role-limited.xml", "1a90eaf4-03ed-4f23-a64b-1aaebc57ae57");
    private static final TestResource<UserType> USER_LIMITED = new TestResource<>(TEST_DIR, "user-limited.xml", "da14f7bc-f6ba-4f40-90d4-816e0e37f252");

    private static final TestResource<TaskType> TASK_CLEANUP_LEGACY_ADMIN = new TestResource<>(TEST_DIR, "task-cleanup-legacy-admin.xml", "0726d8b4-641e-4a01-9878-a11cabace465");
    private static final TestResource<TaskType> TASK_CLEANUP_NEW_LIMITED = new TestResource<>(TEST_DIR, "task-cleanup-new-limited.xml", "08f630d0-0459-49c7-9c70-a813ba2e9da6");

    private static final String TEST_OBJECT_PREFIX = "test";

    private static final long HISTORIC_AUDIT_TIMESTAMP = System.currentTimeMillis() - 86400L * 1000L;

    @Autowired AuditService auditService;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        addObject(ROLE_LIMITED, initTask, initResult);
        addObject(USER_LIMITED, initTask, initResult);
    }

    /**
     * We run the task with the following authorizations:
     *
     * 1. audit: none
     * 2. tasks: partial (only those containing '[cleanable]')
     * 3. nodes: partial (only those containing '[cleanable]')
     *
     * Please see objects-to-be-cleaned-up.xml for expected results - [l:will be deleted] vs [l:will not be deleted].
     *
     * Clean-up of cases, campaigns, and reports is not checked here.
     */
    @Test
    public void test100RunWithLimitedAuthorizations() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // must be done in the test method, because audit service is cleared before each test method run
        createHistoricAuditRecord(task, result);

        repoAddObjectsFromFile(FILE_OBJECTS_TO_BE_CLEANED_UP, result);

        List<PrismObject<TaskType>> tasksBefore =
                repositoryService.searchObjects(TaskType.class, testObjectsQuery(), null, result);
        List<PrismObject<NodeType>> nodesBefore =
                repositoryService.searchObjects(NodeType.class, testObjectsQuery(), null, result);

        when();
        addTask(TASK_CLEANUP_NEW_LIMITED, result);
        waitForTaskCloseOrSuspend(TASK_CLEANUP_NEW_LIMITED.oid);

        then();
        assertTask(TASK_CLEANUP_NEW_LIMITED.oid, "after")
                .display()
                .assertClosed()
                .assertPartialError()
                .rootActivityState()
                    .display(); // TODO check progress and statistics when implemented

        assertHistoricAuditRecordPresence(true);
        assertCleanedUpObjects("l", TaskType.class, tasksBefore, result);
        assertCleanedUpObjects("l", NodeType.class, nodesBefore, result);
    }

    /**
     * This time we run the task (legacy one) under administrator.
     *
     * Please see objects-to-be-cleaned-up.xml for expected results - [a:will be deleted] vs [a:will not be deleted].
     *
     * Again, only audit records, tasks, and nodes are cleaned up.
     */
    @Test
    public void test200RunLegacyWithFullAuthorizations() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // must be done in the test method, because audit service is cleared before each test method run
        createHistoricAuditRecord(task, result);

        repoDeleteObjectsFromFile(FILE_OBJECTS_TO_BE_CLEANED_UP, result);
        repoAddObjectsFromFile(FILE_OBJECTS_TO_BE_CLEANED_UP, result);

        List<PrismObject<TaskType>> tasksBefore =
                repositoryService.searchObjects(TaskType.class, testObjectsQuery(), null, result);
        List<PrismObject<NodeType>> nodesBefore =
                repositoryService.searchObjects(NodeType.class, testObjectsQuery(), null, result);

        when();
        addTask(TASK_CLEANUP_LEGACY_ADMIN, result);
        waitForTaskCloseOrSuspend(TASK_CLEANUP_LEGACY_ADMIN.oid);

        then();
        assertTask(TASK_CLEANUP_LEGACY_ADMIN.oid, "after")
                .display()
                .assertClosed()
                .assertSuccess()
                .rootActivityState()
                    .display(); // TODO check progress and statistics when implemented

        assertHistoricAuditRecordPresence(false);
        assertCleanedUpObjects("a", TaskType.class, tasksBefore, result);
        assertCleanedUpObjects("a", NodeType.class, nodesBefore, result);
    }

    private void createHistoricAuditRecord(Task task, OperationResult result) {
        AuditEventRecord auditRecord = new AuditEventRecord(AuditEventType.ADD_OBJECT);
        auditRecord.setTimestamp(HISTORIC_AUDIT_TIMESTAMP);
        auditService.audit(auditRecord, task, result);

        assertHistoricAuditRecordPresence(true);
    }

    private void assertHistoricAuditRecordPresence(boolean shouldExist) {
        displayDumpable("dummy audit", dummyAuditService);

        var records = dummyAuditService.getRecords().stream()
                .filter(r -> r.getTimestamp() == HISTORIC_AUDIT_TIMESTAMP)
                .collect(Collectors.toList());
        if (shouldExist) {
            assertThat(records).as("historic records").hasSize(1);
        } else {
            assertThat(records).as("historic records").isEmpty();
        }
    }

    private ObjectQuery testObjectsQuery() {
        return queryFor(ObjectType.class)
                .item(ObjectType.F_NAME)
                .startsWithPoly(TEST_OBJECT_PREFIX)
                .build();
    }

    /** Scenario is "l" for limited user, and "a" for administrator. */
    private <T extends ObjectType> void assertCleanedUpObjects(String scenario, Class<T> type, List<PrismObject<T>> before,
            OperationResult result) throws CommonException {
        List<PrismObject<T>> after =
                repositoryService.searchObjects(type, testObjectsQuery(), null, result);
        Set<String> oidsAfter = new HashSet<>(ObjectTypeUtil.getOidsFromPrismObjects(after));

        List<PrismObject<T>> deleted = before.stream()
                .filter(t -> !oidsAfter.contains(t.getOid()))
                .collect(Collectors.toList());
        List<PrismObject<T>> notDeleted = before.stream()
                .filter(t -> oidsAfter.contains(t.getOid()))
                .collect(Collectors.toList());

        display("deleted", deleted);
        display("not deleted", notDeleted);

        assertThat(deleted)
                .allMatch(willBeDeleted(scenario), "will be deleted")
                .noneMatch(willNotBeDeleted(scenario));
        assertThat(notDeleted)
                .allMatch(willNotBeDeleted(scenario), "will not be deleted")
                .noneMatch(willBeDeleted(scenario));
    }

    private Predicate<? super PrismObject<?>> willBeDeleted(String scenario) {
        return object -> object.getName().getOrig().contains("[" + scenario + ":will be deleted]");
    }

    private Predicate<? super PrismObject<?>> willNotBeDeleted(String scenario) {
        return object -> object.getName().getOrig().contains("[" + scenario + ":will not be deleted]");
    }
}
