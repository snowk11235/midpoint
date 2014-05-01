/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.testing.consistency;

import static com.evolveum.midpoint.test.IntegrationTestTools.assertAttribute;
import static com.evolveum.midpoint.test.IntegrationTestTools.assertAttributeNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.assertIcfsNameAttribute;
import static com.evolveum.midpoint.test.IntegrationTestTools.assertNoRepoCache;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayJaxb;
import static com.evolveum.midpoint.test.IntegrationTestTools.waitFor;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import org.apache.commons.lang.StringUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.EmbeddedUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.test.AbstractModelIntegrationTest;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.delta.DiffUtil;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorFactoryIcfImpl;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainer;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.Checker;
import com.evolveum.midpoint.test.ldap.OpenDJController;
import com.evolveum.midpoint.test.util.MidPointAsserts;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FailedOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GenericObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationalStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SchemaHandlingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.midpoint.xml.ns._public.common.fault_3.FaultMessage;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.ActivationCapabilityType;
import com.evolveum.prism.xml.ns._public.types_3.ItemDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

/**
 * Consistency test suite. It tests consistency mechanisms. It works as end-to-end integration test accross all subsystems.
 * 
 * @author Katarina Valalikova
 */
@ContextConfiguration(locations = { "classpath:ctx-consistency-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class ConsistencyTest extends AbstractModelIntegrationTest {
	
	private static final String REPO_DIR_NAME = "src/test/resources/repo/";
	private static final String REQUEST_DIR_NAME = "src/test/resources/request/";

	private static final String SYSTEM_CONFIGURATION_FILENAME = REPO_DIR_NAME + "system-configuration.xml";
//	private static final String SYSTEM_CONFIGURATION_OID = "00000000-0000-0000-0000-000000000001";
	
	private static final String ROLE_SUPERUSER_FILENAME = REPO_DIR_NAME + "role-superuser.xml";
    private static final String ROLE_SUPERUSER_OID = "00000000-0000-0000-0000-000000000004";

	private static final String SAMPLE_CONFIGURATION_OBJECT_FILENAME = REPO_DIR_NAME + "sample-configuration-object.xml";
    private static final String SAMPLE_CONFIGURATION_OBJECT_OID = "c0c010c0-d34d-b33f-f00d-999111111111";
	
	private static final String RESOURCE_OPENDJ_FILENAME = REPO_DIR_NAME + "resource-opendj.xml";
	private static final String RESOURCE_OPENDJ_OID = "ef2bc95b-76e0-59e2-86d6-3d4f02d3ffff";

	private static final String CONNECTOR_LDAP_NAMESPACE = "http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/bundle/org.forgerock.openicf.connectors.ldap-connector/org.identityconnectors.ldap.LdapConnector";

	private static final String USER_TEMPLATE_FILENAME = REPO_DIR_NAME + "user-template.xml";

	private static final String USER_ADMINISTRATOR_FILENAME = REPO_DIR_NAME + "user-administrator.xml";
	private static final String USER_ADMINISTRATOR_NAME = "administrator";

	private static final String USER_JACK_FILENAME = REPO_DIR_NAME + "user-jack.xml";
	private static final String USER_JACK_OID = "c0c010c0-d34d-b33f-f00d-111111111111";

	private static final String USER_DENIELS_FILENAME = REPO_DIR_NAME + "user-deniels.xml";
	private static final String USER_DENIELS_OID = "c0c010c0-d34d-b33f-f00d-222111111111";

	private static final String USER_JACK2_FILENAME = REPO_DIR_NAME + "user-jack2.xml";
	private static final String USER_JACK2_OID = "c0c010c0-d34d-b33f-f00d-111111114444";

	private static final String USER_WILL_FILENAME = REPO_DIR_NAME + "user-will.xml";
	private static final String USER_WILL_OID = "c0c010c0-d34d-b33f-f00d-111111115555";

	private static final String USER_JACK_LDAP_UID = "jackie";
	private static final String USER_JACK_LDAP_DN = "uid=" + USER_JACK_LDAP_UID + "," + OPENDJ_PEOPLE_SUFFIX;

	private static final String USER_GUYBRUSH_FILENAME = REPO_DIR_NAME + "user-guybrush.xml";
	private static final String USER_GUYBRUSH_OID = "c0c010c0-d34d-b33f-f00d-111111111222";

	private static final String USER_GUYBRUSH_NOT_FOUND_FILENAME = REPO_DIR_NAME + "user-guybrush-modify-not-found.xml";
	private static final String USER_GUYBRUSH_NOT_FOUND_OID = "c0c010c0-d34d-b33f-f00d-111111111333";
	
	private static final String USER_HECTOR_NOT_FOUND_FILENAME = REPO_DIR_NAME + "user-hector.xml";
	private static final String USER_HECTOR_NOT_FOUND_OID = "c0c010c0-d34d-b33f-f00d-111111222333";	

	private static final String USER_E_FILENAME = REPO_DIR_NAME + "user-e.xml";
	private static final String USER_E_OID = "c0c010c0-d34d-b33f-f00d-111111111100";
	
	private static final String USER_ELAINE_FILENAME = REPO_DIR_NAME + "user-elaine.xml";
	private static final String USER_ELAINE_OID = "c0c010c0-d34d-b33f-f00d-111111116666";
	
	private static final String USER_HERMAN_FILENAME = REPO_DIR_NAME + "user-herman.xml";
	private static final String USER_HERMAN_OID = "c0c010c0-d34d-b33f-f00d-111111119999";
	
	private static final String USER_MORGAN_FILENAME = REQUEST_DIR_NAME + "user-morgan.xml";
	private static final String USER_MORGAN_OID = "c0c010c0-d34d-b33f-f00d-171171117777";
	
	private static final String USER_CHUCK_FILENAME = REQUEST_DIR_NAME + "user-chuck.xml";
	private static final String USER_CHUCK_OID = "c0c010c0-d34d-b33f-f00d-171171118888";
	
	private static final String USER_ANGELIKA_FILENAME = REPO_DIR_NAME + "user-angelika.xml";
	private static final String USER_ANGELIKA_OID = "c0c010c0-d34d-b33f-f00d-111111111888";
	
	private static final String USER_ALICE_FILENAME = REPO_DIR_NAME + "user-alice.xml";
	private static final String USER_ALICE_OID = "c0c010c0-d34d-b33f-f00d-111111111999";
	
	private static final String USER_BOB_NO_FAMILY_NAME_FILENAME = REPO_DIR_NAME + "user-bob-no-family-name.xml";
	private static final String USER_BOB_NO_FAMILY_NAME_OID = "c0c010c0-d34d-b33f-f00d-222111222999";
	
	private static final String USER_JOHN_WEAK_FILENAME = REPO_DIR_NAME + "user-john.xml";
	private static final String USER_JOHN_WEAK_OID = "c0c010c0-d34d-b33f-f00d-999111111888";
	
	private static final String USER_DONALD_FILENAME = REPO_DIR_NAME + "user-donald.xml";
	private static final String USER_DONALD_OID = "c0c010c0-d34d-b33f-f00d-999111111777";
	
	private static final String USER_DISCOVERY_FILENAME = REPO_DIR_NAME + "user-discovery.xml";
	private static final String USER_DISCOVERY_OID = "c0c010c0-d34d-b33f-f00d-111112226666";
	
	private static final String USER_ABOMBA_FILENAME = REPO_DIR_NAME + "user-abomba.xml";
	private static final String USER_ABOMBA_OID = "c0c010c0-d34d-b33f-f00d-016016111111";
	
	private static final String USER_ABOM_FILENAME = REPO_DIR_NAME + "user-abom.xml";
	private static final String USER_ABOM_OID = "c0c010c0-d34d-b33f-f00d-111111016016";
	
	private static final String ACCOUNT_GUYBRUSH_FILENAME = REPO_DIR_NAME + "account-guybrush.xml";
	private static final String ACCOUNT_GUYBRUSH_OID = "a0c010c0-d34d-b33f-f00d-111111111222";
	
	private static final String ACCOUNT_HECTOR_FILENAME = REPO_DIR_NAME + "account-hector-not-found.xml";
	private static final String ACCOUNT_HECTOR_OID = "a0c010c0-d34d-b33f-f00d-111111222333";

	private static final String ACCOUNT_GUYBRUSH_MODIFY_DELETE_FILENAME = REPO_DIR_NAME + "account-guybrush-not-found.xml";
	private static final String ACCOUNT_GUYBRUSH_MODIFY_DELETE_OID = "a0c010c0-d34d-b33f-f00d-111111111333";

	private static final String ACCOUNT_DENIELS_FILENAME = REPO_DIR_NAME + "account-deniels.xml";
	private static final String ACCOUNT_DENIELS_OID = "a0c010c0-d34d-b33f-f00d-111111111555";
	
	private static final String ACCOUNT_CHUCK_FILENAME = REPO_DIR_NAME + "account-chuck.xml";
	
	private static final String ACCOUNT_HERMAN_FILENAME = REPO_DIR_NAME + "account-herman.xml";
	private static final String ACCOUNT_HERMAN_OID = "22220000-2200-0000-0000-333300003333";

	private static final String REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT = "src/test/resources/request/user-modify-assign-account.xml";
	private static final String REQUEST_USER_MODIFY_ADD_ACCOUNT_DIRECTLY = "src/test/resources/request/user-modify-add-account-directly.xml";
		private static final String REQUEST_USER_MODIFY_DELETE_ACCOUNT = "src/test/resources/request/user-modify-delete-account.xml";
	private static final String REQUEST_USER_MODIFY_DELETE_ACCOUNT_COMMUNICATION_PROBLEM = "src/test/resources/request/user-modify-delete-account-communication-problem.xml";
	
	
	private static final String REQUEST_ACCOUNT_MODIFY_NOT_FOUND_DELETE_ACCOUNT = "src/test/resources/request/account-guybrush-modify-attributes.xml";
	private static final String REQUEST_ACCOUNT_MODIFY_COMMUNICATION_PROBLEM = "src/test/resources/request/account-modify-attrs-communication-problem.xml";
	private static final String REQUEST_ADD_ACCOUNT_JACKIE = "src/test/resources/request/add-account-jack.xml";
	private static final String REQUEST_USER_MODIFY_WEAK_MAPPING_COMMUNICATION_PROBLEM = "src/test/resources/request/user-modify-employeeType.xml";
	private static final String REQUEST_USER_MODIFY_WEAK_STRONG_MAPPING_COMMUNICATION_PROBLEM = "src/test/resources/request/user-modify-employeeType-givenName.xml";
	private static final String REQUEST_RESOURCE_MODIFY_RESOURCE_SCHEMA = "src/test/resources/request/resource-modify-resource-schema.xml";
	private static final String REQUEST_RESOURCE_MODIFY_SYNCHRONIZATION = "src/test/resources/request/resource-modify-synchronization.xml";

	private static final String TASK_OPENDJ_RECONCILIATION_FILENAME = "src/test/resources/repo/task-opendj-reconciliation.xml";
	private static final String TASK_OPENDJ_RECONCILIATION_OID = "91919191-76e0-59e2-86d6-3d4f02d30000";

	private static final String LDIF_WILL_FILENAME = "src/test/resources/request/will.ldif";
	private static final String LDIF_ELAINE_FILENAME = "src/test/resources/request/elaine.ldif";
	private static final String LDIF_MORGAN_FILENAME = "src/test/resources/request/morgan.ldif";
	private static final String LDIF_DISCOVERY_FILENAME = "src/test/resources/request/discovery.ldif";
	
	private static final String LDIF_MODIFY_RENAME_FILENAME = "src/test/resources/request/modify-rename.ldif";

//	private static final QName IMPORT_OBJECTCLASS = new QName(
//			"http://midpoint.evolveum.com/xml/ns/public/resource/instance/ef2bc95b-76e0-59e2-86d6-3d4f02d3ffff",
//			"AccountObjectClass");

	private static final Trace LOGGER = TraceManager.getTrace(ConsistencyTest.class);

	private static final String NS_MY = "http://whatever.com/my";
	private static final QName MY_SHIP_STATE = new QName(NS_MY, "shipState");

	/**
	 * Unmarshalled resource definition to reach the embedded OpenDJ instance.
	 * Used for convenience - the tests method may find it handy.
	 */
	private static ResourceType resourceTypeOpenDjrepo;
	private static String accountShadowOidOpendj;
//	private static String originalJacksPassword;

	// private int lastSyncToken;
	
	// This will get called from the superclass to init the repository
	// It will be called only once
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		LOGGER.trace("initSystem");
		super.initSystem(initTask, initResult);
		
		repoAddObjectFromFile(ROLE_SUPERUSER_FILENAME, RoleType.class, initResult);
		repoAddObjectFromFile(USER_ADMINISTRATOR_FILENAME, UserType.class, initResult);

		// This should discover the connectors
		LOGGER.trace("initSystem: trying modelService.postInit()");
		modelService.postInit(initResult);
		LOGGER.trace("initSystem: modelService.postInit() done");
		
		login(USER_ADMINISTRATOR_NAME);

		// We need to add config after calling postInit() so it will not be
		// applied.
		// we want original logging configuration from the test logback config
		// file, not
		// the one from the system config.
		repoAddObjectFromFile(SYSTEM_CONFIGURATION_FILENAME, SystemConfigurationType.class, initResult);

		// Add broken connector before importing resources
		// addObjectFromFile(CONNECTOR_BROKEN_FILENAME, initResult);

		// Need to import instead of add, so the (dynamic) connector reference
		// will be resolved
		// correctly
		importObjectFromFile(RESOURCE_OPENDJ_FILENAME, initResult);
		// importObjectFromFile(RESOURCE_DERBY_FILENAME, initResult);
		// importObjectFromFile(RESOURCE_BROKEN_FILENAME, initResult);

		repoAddObjectFromFile(SAMPLE_CONFIGURATION_OBJECT_FILENAME, GenericObjectType.class, initResult);
		repoAddObjectFromFile(USER_TEMPLATE_FILENAME, ObjectTemplateType.class, initResult);
		
		assumeAssignmentPolicy(AssignmentPolicyEnforcementType.POSITIVE);
	}

	/**
	 * Initialize embedded OpenDJ instance Note: this is not in the abstract
	 * superclass so individual tests may avoid starting OpenDJ.
	 */
	@Override
	public void startResources() throws Exception {
		openDJController.startCleanServer();
		derbyController.startCleanServer();
	}

	/**
	 * Shutdown embedded OpenDJ instance Note: this is not in the abstract
	 * superclass so individual tests may avoid starting OpenDJ.
	 */
	@AfterClass
	public static void stopResources() throws Exception {
		openDJController.stop();
		derbyController.stop();
	}

	/**
	 * Test integrity of the test setup.
	 * 
	 * @throws SchemaException
	 * @throws ObjectNotFoundException
	 * @throws CommunicationException
	 */
	@Test
	public void test000Integrity() throws ObjectNotFoundException, SchemaException, CommunicationException {
		TestUtil.displayTestTile(this, "test000Integrity");
		assertNotNull(modelWeb);
		assertNotNull(modelService);
		assertNotNull(repositoryService);
		assertTrue(isSystemInitialized());
		assertNotNull(taskManager);

		assertNotNull(prismContext);
		SchemaRegistry schemaRegistry = prismContext.getSchemaRegistry();
		assertNotNull(schemaRegistry);
		// This is defined in extra schema. So this effectively checks whether
		// the extra schema was loaded
		PrismPropertyDefinition shipStateDefinition = schemaRegistry
				.findPropertyDefinitionByElementName(MY_SHIP_STATE);
		assertNotNull("No my:shipState definition", shipStateDefinition);
		assertEquals("Wrong maxOccurs in my:shipState definition", 1, shipStateDefinition.getMaxOccurs());

		assertNoRepoCache();

		OperationResult result = new OperationResult(ConsistencyTest.class.getName() + ".test000Integrity");

		// Check if OpenDJ resource was imported correctly

		PrismObject<ResourceType> openDjResource = repositoryService.getObject(ResourceType.class,
				RESOURCE_OPENDJ_OID, null, result);
		display("Imported OpenDJ resource (repository)", openDjResource);
		AssertJUnit.assertEquals(RESOURCE_OPENDJ_OID, openDjResource.getOid());
		assertNoRepoCache();

		String ldapConnectorOid = openDjResource.asObjectable().getConnectorRef().getOid();
		PrismObject<ConnectorType> ldapConnector = repositoryService.getObject(ConnectorType.class,
				ldapConnectorOid, null, result);
		display("LDAP Connector: ", ldapConnector);

//		repositoryService.getObject(GenericObjectType.class, SAMPLE_CONFIGURATION_OBJECT_OID, null, result);
	}

	/**
	 * Test the testResource method. Expect a complete success for now.
	 */
	@Test
	public void test001TestConnectionOpenDJ() throws FaultMessage, JAXBException, ObjectNotFoundException,
			SchemaException, CommunicationException, ConfigurationException, SecurityViolationException {
		TestUtil.displayTestTile("test001TestConnectionOpenDJ");

		Task task = taskManager.createTaskInstance();
		// GIVEN

		assertNoRepoCache();

		// WHEN
		OperationResultType result = modelWeb.testResource(RESOURCE_OPENDJ_OID);

		// THEN

		assertNoRepoCache();

		displayJaxb("testResource result:", result, SchemaConstants.C_RESULT);

		TestUtil.assertSuccess("testResource has failed", result);

		OperationResult opResult = new OperationResult(ConsistencyTest.class.getName()
				+ ".test001TestConnectionOpenDJ");

		PrismObject<ResourceType> resourceOpenDjRepo = repositoryService.getObject(ResourceType.class,
				RESOURCE_OPENDJ_OID, null, opResult);
		resourceTypeOpenDjrepo = resourceOpenDjRepo.asObjectable();

		assertNoRepoCache();
		assertEquals(RESOURCE_OPENDJ_OID, resourceTypeOpenDjrepo.getOid());
		display("Initialized OpenDJ resource (respository)", resourceTypeOpenDjrepo);
		assertNotNull("Resource schema was not generated", resourceTypeOpenDjrepo.getSchema());
		Element resourceOpenDjXsdSchemaElement = ResourceTypeUtil
				.getResourceXsdSchema(resourceTypeOpenDjrepo);
		assertNotNull("Resource schema was not generated", resourceOpenDjXsdSchemaElement);

		PrismObject<ResourceType> openDjResourceProvisioninig = provisioningService.getObject(
				ResourceType.class, RESOURCE_OPENDJ_OID, null, task, opResult);
		display("Initialized OpenDJ resource resource (provisioning)", openDjResourceProvisioninig);

		PrismObject<ResourceType> openDjResourceModel = provisioningService.getObject(ResourceType.class,
				RESOURCE_OPENDJ_OID, null, task, opResult);
		display("Initialized OpenDJ resource OpenDJ resource (model)", openDjResourceModel);

		checkOpenDjResource(resourceTypeOpenDjrepo, "repository");

		System.out.println("------------------------------------------------------------------");
		display("OpenDJ resource schema (repo XML)",
				DOMUtil.serializeDOMToString(ResourceTypeUtil.getResourceXsdSchema(resourceOpenDjRepo)));
		System.out.println("------------------------------------------------------------------");

		checkOpenDjResource(openDjResourceProvisioninig.asObjectable(), "provisioning");
		checkOpenDjResource(openDjResourceModel.asObjectable(), "model");
		// TODO: model web

	}

	private void checkRepoOpenDjResource() throws ObjectNotFoundException, SchemaException {
		OperationResult result = new OperationResult(ConsistencyTest.class.getName()
				+ ".checkRepoOpenDjResource");
		PrismObject<ResourceType> resource = repositoryService.getObject(ResourceType.class,
				RESOURCE_OPENDJ_OID, null, result);
		checkOpenDjResource(resource.asObjectable(), "repository");
	}

	
	/**
	 * Checks if the resource is internally consistent, if it has everything it
	 * should have.
	 * 
	 * @throws SchemaException
	 */
	private void checkOpenDjResource(ResourceType resource, String source) throws SchemaException {
		assertNotNull("Resource from " + source + " is null", resource);
		assertNotNull("Resource from " + source + " has null configuration", resource.getConnectorConfiguration());
		assertNotNull("Resource from " + source + " has null schema", resource.getSchema());
		checkOpenDjSchema(resource, source);
		assertNotNull("Resource from " + source + " has null schemahandling", resource.getSchemaHandling());
		assertNotNull("Resource from " + source + " has null capabilities", resource.getCapabilities());
		if (!source.equals("repository")) {
			// This is generated on the fly in provisioning
			assertNotNull("Resource from " + source + " has null native capabilities",
					resource.getCapabilities().getNative());
			assertFalse("Resource from " + source + " has empty native capabilities", resource
					.getCapabilities().getNative().getAny().isEmpty());
		}
		assertNotNull("Resource from " + source + " has null configured capabilities", resource.getCapabilities().getConfigured());
		assertFalse("Resource from " + source + " has empty configured capabilities", resource.getCapabilities().getConfigured()
				.getAny().isEmpty());
		assertNotNull("Resource from " + source + " has null synchronization", resource.getSynchronization());
		checkOpenDjConfiguration(resource.asPrismObject(), source);
	}

	private void checkOpenDjSchema(ResourceType resource, String source) throws SchemaException {
		ResourceSchema schema = RefinedResourceSchema.getResourceSchema(resource, prismContext);
		ObjectClassComplexTypeDefinition accountDefinition = schema.findDefaultObjectClassDefinition(ShadowKindType.ACCOUNT);
		assertNotNull("Schema does not define any account (resource from " + source + ")", accountDefinition);
		Collection<? extends ResourceAttributeDefinition> identifiers = accountDefinition.getIdentifiers();
		assertFalse("No account identifiers (resource from " + source + ")", identifiers == null
				|| identifiers.isEmpty());
		// TODO: check for naming attributes and display names, etc

		ActivationCapabilityType capActivation = ResourceTypeUtil.getEffectiveCapability(resource,
				ActivationCapabilityType.class);
		if (capActivation != null && capActivation.getEnableDisable() != null
				&& capActivation.getEnableDisable().getAttribute() != null) {
			// There is simulated activation capability, check if the attribute
			// is in schema.
			QName enableAttrName = capActivation.getEnableDisable().getAttribute();
			ResourceAttributeDefinition enableAttrDef = accountDefinition
					.findAttributeDefinition(enableAttrName);
			display("Simulated activation attribute definition", enableAttrDef);
			assertNotNull("No definition for enable attribute " + enableAttrName
					+ " in account (resource from " + source + ")", enableAttrDef);
			assertTrue("Enable attribute " + enableAttrName + " is not ignored (resource from " + source
					+ ")", enableAttrDef.isIgnored());
		}
	}

	private void checkOpenDjConfiguration(PrismObject<ResourceType> resource, String source) {
		checkOpenResourceConfiguration(resource, CONNECTOR_LDAP_NAMESPACE, "credentials", 7, source);
	}

	private void checkOpenResourceConfiguration(PrismObject<ResourceType> resource,
			String connectorNamespace, String credentialsPropertyName, int numConfigProps, String source) {
		PrismContainer<Containerable> configurationContainer = resource
				.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);
		assertNotNull("No configuration container in " + resource + " from " + source, configurationContainer);
		PrismContainer<Containerable> configPropsContainer = configurationContainer
				.findContainer(SchemaTestConstants.ICFC_CONFIGURATION_PROPERTIES);
		assertNotNull("No configuration properties container in " + resource + " from " + source,
				configPropsContainer);
		List<Item<?>> configProps = configPropsContainer.getValue().getItems();
		assertEquals("Wrong number of config properties in " + resource + " from " + source, numConfigProps,
				configProps.size());
		PrismProperty<Object> credentialsProp = configPropsContainer.findProperty(new QName(
				connectorNamespace, credentialsPropertyName));
		if (credentialsProp == null) {
			// The is the heisenbug we are looking for. Just dump the entire
			// damn thing.
			display("Configuration with the heisenbug", configurationContainer.debugDump());
		}
		assertNotNull("No credentials property in " + resource + " from " + source, credentialsProp);
		assertEquals("Wrong number of credentials property value in " + resource + " from " + source, 1,
				credentialsProp.getValues().size());
		PrismPropertyValue<Object> credentialsPropertyValue = credentialsProp.getValues().iterator().next();
		assertNotNull("No credentials property value in " + resource + " from " + source,
				credentialsPropertyValue);
		Object rawElement = credentialsPropertyValue.getRawElement();
		}

	private UserType testAddUserToRepo(String displayMessage, String fileName, String userOid)
            throws IOException, ObjectNotFoundException, SchemaException, EncryptionException,
            ObjectAlreadyExistsException, ExpressionEvaluationException, CommunicationException,
            ConfigurationException, PolicyViolationException, SecurityViolationException {

		checkRepoOpenDjResource();
		assertNoRepoCache();

		PrismObject<UserType> user = PrismTestUtil.parseObject(new File(fileName));
		UserType userType = user.asObjectable();
		PrismAsserts.assertParentConsistency(user);

		protector.encrypt(userType.getCredentials().getPassword().getValue());
		PrismAsserts.assertParentConsistency(user);

		OperationResult result = new OperationResult("add user");
	
		display("Adding user object", userType);

		Task task = taskManager.createTaskInstance();
		// WHEN
		ObjectDelta delta = ObjectDelta.createAddDelta(user);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltaCollection(delta);
		modelService.executeChanges(deltas, null, task, result);
		// THEN

		assertNoRepoCache();
	
		return userType;
	}

	private Collection<ObjectDelta<? extends ObjectType>> createDeltaCollection(ObjectDelta delta) {
		Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<ObjectDelta<? extends ObjectType>>();
		deltas.add(delta);
		return deltas;
	}

	/**
	 * Attempt to add new user. It is only added to the repository, so check if
	 * it is in the repository after the operation.
	 */
	@Test
	public void test010AddUser() throws Exception {

		UserType userType = testAddUserToRepo("test010AddUser", USER_JACK_FILENAME, USER_JACK_OID);

		OperationResult repoResult = new OperationResult("getObject");
		PropertyReferenceListType resolve = new PropertyReferenceListType();

		PrismObject<UserType> uObject = repositoryService
				.getObject(UserType.class, USER_JACK_OID, null, repoResult);
		UserType repoUser = uObject.asObjectable();

		repoResult.computeStatus();
		display("repository.getObject result", repoResult);
		TestUtil.assertSuccess("getObject has failed", repoResult);
		AssertJUnit.assertEquals(USER_JACK_OID, repoUser.getOid());
		PrismAsserts.assertEqualsPolyString("User full name not equals as expected.", userType.getFullName(),
				repoUser.getFullName());

		// TODO: better checks
	}

//	private OperationResultType modifyUserAddAccount(String modifyUserRequest) throws FileNotFoundException,
//			JAXBException, FaultMessage, ObjectNotFoundException, SchemaException, DirectoryException, ObjectAlreadyExistsException {
//		checkRepoOpenDjResource();
//		assertNoRepoCache();
//
//		ObjectModificationType objectChange = unmarshallJaxbFromFile(modifyUserRequest,
//				ObjectModificationType.class);
//
//		// WHEN
//		OperationResultType result = modelWeb.modifyObject(ObjectTypes.USER.getObjectTypeUri(), objectChange);
//
//		// THEN
//		assertNoRepoCache();
//		return result;
//	}
	
	private String assertUserOneAccountRef(String userOid) throws Exception{
		OperationResult parentResult = new OperationResult("getObject from repo");
		
		PrismObject<UserType> repoUser = repositoryService.getObject(UserType.class, userOid,
				null, parentResult);
		UserType repoUserType = repoUser.asObjectable();

		parentResult.computeStatus();
		TestUtil.assertSuccess("getObject has failed", parentResult);
		display("User (repository)", repoUser);

		List<ObjectReferenceType> accountRefs = repoUserType.getLinkRef();
		assertEquals("No accountRefs", 1, accountRefs.size());
		ObjectReferenceType accountRef = accountRefs.get(0);
		
		return accountRef.getOid();

	}

	private String assertOneAccountRef(PrismObject<UserType> user) throws Exception{

		UserType repoUserType = user.asObjectable();
		display("User (repository)", user);

		List<ObjectReferenceType> accountRefs = repoUserType.getLinkRef();
		assertEquals("No accountRefs", 1, accountRefs.size());
		ObjectReferenceType accountRef = accountRefs.get(0);
		
		return accountRef.getOid();

	}

	

	/**
	 * Add account to user. This should result in account provisioning. Check if
	 * that happens in repo and in LDAP.
	 */
	@Test
	public void test013prepareOpenDjWithAccounts() throws Exception {
		TestUtil.displayTestTile("test013prepareOpenDjWithAccounts");
		OperationResult parentResult = new OperationResult("test013prepareOpenDjWithAccounts");

		ShadowType jackeAccount = unmarshallJaxbFromFile(REQUEST_ADD_ACCOUNT_JACKIE,
				ShadowType.class);

		Task task = taskManager.createTaskInstance();
		String oid = provisioningService.addObject(jackeAccount.asPrismObject(), null, null, task, parentResult);
		PrismObject<ShadowType> jackFromRepo = repositoryService.getObject(ShadowType.class,
				oid, null, parentResult);
		LOGGER.debug("account jack after provisioning: {}", jackFromRepo.debugDump());

		PrismObject<UserType> jackUser = repositoryService.getObject(UserType.class, USER_JACK_OID,
				null, parentResult);
		ObjectReferenceType ort = new ObjectReferenceType();
		ort.setOid(oid);
		ort.setType(ShadowType.COMPLEX_TYPE);

		jackUser.asObjectable().getLinkRef().add(ort);

		PrismObject<UserType> jackUserRepo = repositoryService.getObject(UserType.class, USER_JACK_OID,
				null, parentResult);
		ObjectDelta delta = DiffUtil.diff(jackUserRepo, jackUser);

		repositoryService.modifyObject(UserType.class, USER_JACK_OID, delta.getModifications(), parentResult);

		// GIVEN

		OperationResult repoResult = new OperationResult("getObject");

		
		// Check if user object was modified in the repo
		accountShadowOidOpendj = assertUserOneAccountRef(USER_JACK_OID);
		assertFalse(accountShadowOidOpendj.isEmpty());

		// Check if shadow was created in the repo

		repoResult = new OperationResult("getObject");

		PrismObject<ShadowType> repoShadow = repositoryService.getObject(ShadowType.class,
				accountShadowOidOpendj, null, repoResult);
		ShadowType repoShadowType = repoShadow.asObjectable();
		repoResult.computeStatus();
		TestUtil.assertSuccess("getObject has failed", repoResult);
		display("Shadow (repository)", repoShadow);
		assertNotNull(repoShadowType);
		assertEquals(RESOURCE_OPENDJ_OID, repoShadowType.getResourceRef().getOid());

		assertNotNull("Shadow stored in repository has no name", repoShadowType.getName());
		// Check the "name" property, it should be set to DN, not entryUUID
		assertEquals("Wrong name property", USER_JACK_LDAP_DN.toLowerCase(), repoShadowType.getName()
				.getOrig().toLowerCase());

		// check attributes in the shadow: should be only identifiers (ICF UID)
		String uid = checkRepoShadow(repoShadow);

		// check if account was created in LDAP

		SearchResultEntry entry = openDJController.searchAndAssertByEntryUuid(uid);

		display("LDAP account", entry);
	
		OpenDJController.assertAttribute(entry, "uid", "jackie");
		OpenDJController.assertAttribute(entry, "givenName", "Jack");
		OpenDJController.assertAttribute(entry, "sn", "Sparrow");
		OpenDJController.assertAttribute(entry, "cn", "Jack Sparrow");
		// The "l" attribute is assigned indirectly through schemaHandling and
		// config object
		// OpenDJController.assertAttribute(entry, "l", "middle of nowhere");

		// originalJacksPassword = OpenDJController.getAttributeValue(entry,
		// "userPassword");
		// assertNotNull("Pasword was not set on create",
		// originalJacksPassword);
		// System.out.println("password after create: " +
		// originalJacksPassword);

		// Use getObject to test fetch of complete shadow

		assertNoRepoCache();

		Holder<OperationResultType> resultHolder = new Holder<OperationResultType>();
		Holder<ObjectType> objectHolder = new Holder<ObjectType>();

		// WHEN
		PropertyReferenceListType resolve = new PropertyReferenceListType();
//		List<ObjectOperationOptions> options = new ArrayList<ObjectOperationOptions>();
		modelWeb.getObject(ObjectTypes.SHADOW.getTypeQName(), accountShadowOidOpendj, null,
				objectHolder, resultHolder);

		// THEN
		assertNoRepoCache();
		displayJaxb("getObject result", resultHolder.value, SchemaConstants.C_RESULT);
		TestUtil.assertSuccess("getObject has failed", resultHolder.value);

		ShadowType modelShadow = (ShadowType) objectHolder.value;
		display("Shadow (model)", modelShadow);

		AssertJUnit.assertNotNull(modelShadow);
		AssertJUnit.assertEquals(RESOURCE_OPENDJ_OID, modelShadow.getResourceRef().getOid());

		assertAttributeNotNull(modelShadow, ConnectorFactoryIcfImpl.ICFS_UID);
		assertAttributes(modelShadow, "jackie", "Jack", "Sparrow", "Jack Sparrow");
		// "middle of nowhere");
		assertNull("carLicense attribute sneaked to LDAP",
				OpenDJController.getAttributeValue(entry, "carLicense"));

		assertNotNull("Activation is null", modelShadow.getActivation());
		assertNotNull("No 'enabled' in the shadow", modelShadow.getActivation().getAdministrativeStatus());
		assertEquals("The account is not enabled in the shadow", ActivationStatusType.ENABLED, modelShadow.getActivation().getAdministrativeStatus());

		TestUtil.displayTestTile("test013prepareOpenDjWithAccounts - add second account");

		OperationResult secondResult = new OperationResult(
				"test013prepareOpenDjWithAccounts - add second account");

		ShadowType shadow = unmarshallJaxbFromFile(ACCOUNT_DENIELS_FILENAME, ShadowType.class);

		provisioningService.addObject(shadow.asPrismObject(), null, null, task, secondResult);

		repoAddObjectFromFile(USER_DENIELS_FILENAME, UserType.class, secondResult);

		// GIVEN
		// result =
		// modifyUserAddAccount(REQUEST_USER_MODIFY_ADD_ACCOUNT_OPENDJ_FILENAME);
		// displayJaxb("modifyObject result", parentResult,
		// SchemaConstants.C_RESULT);
		// assertSuccess("modifyObject has failed", parentResult);

		// Check if user object was modified in the repo

	}

	private void assertUserNoAccountRef(String userOid, OperationResult parentResult) throws Exception{
		PrismObject<UserType> user = repositoryService
				.getObject(UserType.class, userOid, null, parentResult);
		assertEquals(0, user.asObjectable().getLinkRef().size());

	}
	
	@Test
	public void test014addAccountAlreadyExistLinked() throws Exception {
		TestUtil.displayTestTile("test014addAccountAlreadyExistLinked");
		Task task = taskManager.createTaskInstance();
		// GIVEN
		OperationResult parentResult = new OperationResult("Add account already exist linked");
		testAddUserToRepo("test014testAssAccountAlreadyExistLinked", USER_JACK2_FILENAME, USER_JACK2_OID);

		assertUserNoAccountRef(USER_JACK2_OID, parentResult);

//		//check if the jackie account already exists on the resource
		String accountRef = assertUserOneAccountRef(USER_JACK_OID);

		PrismObject<ShadowType> jackUserAccount = repositoryService.getObject(ShadowType.class, accountRef, null, parentResult);
		display("Jack's account: ", jackUserAccount.debugDump());
					
		// WHEN REQUEST_USER_MODIFY_ADD_ACCOUNT_ALERADY_EXISTS_LINKED_OPENDJ_FILENAME
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_JACK2_OID, UserType.class, task, null, parentResult);

		// THEN		
		//expected thet the dn and ri:uid will be jackie1 because jackie already exists and is liked to another user..
		String accountOid = checkUser(USER_JACK2_OID, task, parentResult);
		
		checkAccount(accountOid, "jackie1", "Jack", "Russel", "Jack Russel", task, parentResult);

	}
	
	
	@Test
	public void test015addAccountAlreadyExistUnlinked() throws Exception {
		final String TEST_NAME = "test015addAccountAlreadyExistUnlinked";
		TestUtil.displayTestTile(TEST_NAME);

		// GIVEN
		OperationResult parentResult = new OperationResult("Add account already exist unlinked.");
		Entry entry = openDJController.addEntryFromLdifFile(LDIF_WILL_FILENAME);
		SearchResultEntry searchResult = openDJController.searchByUid("wturner");
		OpenDJController.assertAttribute(searchResult, "l", "Caribbean");
		OpenDJController.assertAttribute(searchResult, "givenName", "Will");
		OpenDJController.assertAttribute(searchResult, "sn", "Turner");
		OpenDJController.assertAttribute(searchResult, "cn", "Will Turner");
		OpenDJController.assertAttribute(searchResult, "mail", "will.turner@blackpearl.com");
		OpenDJController.assertAttribute(searchResult, "telephonenumber", "+1 408 555 1234");
		OpenDJController.assertAttribute(searchResult, "facsimiletelephonenumber", "+1 408 555 4321");
		String dn = searchResult.getDN().toString();
		assertEquals("DN attribute " + dn + " not equals", dn, "uid=wturner,ou=People,dc=example,dc=com");

		testAddUserToRepo("add user - test015 account already exist unlinked", USER_WILL_FILENAME,
				USER_WILL_OID);
		assertUserNoAccountRef(USER_WILL_OID, parentResult);

		Task task = taskManager.createTaskInstance();
		
		//WHEN REQUEST_USER_MODIFY_ADD_ACCOUNT_ALERADY_EXISTS_UNLINKED_OPENDJ_FILENAME
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_WILL_OID, UserType.class, task, null, parentResult);
//		TestUtil.displayWhen(TEST_NAME);

		// THEN
		TestUtil.displayThen(TEST_NAME);
		String accountOid = checkUser(USER_WILL_OID, task, parentResult);
//		MidPointAsserts.assertAssignments(user, 1);

		PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class,
				accountOid, null, task, parentResult);

		ResourceAttributeContainer attributes = ShadowUtil.getAttributesContainer(account);

		assertEquals("shadow secondary identifier not equal with the account dn. ", dn, attributes
				.getSecondaryIdentifier().getRealValue(String.class));

		String identifier = attributes.getIdentifier().getRealValue(String.class);

		openDJController.searchAndAssertByEntryUuid(identifier);

	}
	
	//MID-1595, MID-1577
	@Test
	public void test016addAccountDirrectAlreadyExists() throws Exception {

		TestUtil.displayTestTile("test016addAccountDirrectAlreadyExists");
		OperationResult parentResult = new OperationResult(
				"test016addAccountDirrectAlreadyExists");
		Task task = taskManager.createTaskInstance();

		SchemaHandlingType oldSchemaHandlig = resourceTypeOpenDjrepo
				.getSchemaHandling();
		SynchronizationType oldSynchronization = resourceTypeOpenDjrepo
				.getSynchronization();
		try {

			// we will reapply this schema handling after this test finish
			ItemDefinition syncDef = resourceTypeOpenDjrepo.asPrismObject().getDefinition().findItemDefinition(ResourceType.F_SYNCHRONIZATION);
			assertNotNull("null definition for sync delta", syncDef);

			ObjectDeltaType omt = unmarshallJaxbFromFile(REQUEST_RESOURCE_MODIFY_SYNCHRONIZATION, ObjectDeltaType.class);
			ObjectDelta objectDelta = DeltaConvertor.createObjectDelta(omt, prismContext);
//			assertEquals(1, omt.getItemDelta().size());
//			ItemDeltaType syncItemType = omt.getItemDelta().get(0);
//			ItemDelta sd = DeltaConvertor.createItemDelta(syncItemType, resourceTypeOpenDjrepo.asPrismObject().getDefinition());
//			Collection resSyncDelta = new ArrayList();
//			resSyncDelta.add(sd);
//			ObjectDelta resSyncDelta = DeltaConvertor.createObjectDelta(omt, resourceTypeOpenDjrepo.asPrismObject().getDefinition());
			
			repositoryService.modifyObject(ResourceType.class, RESOURCE_OPENDJ_OID, objectDelta.getModifications(), parentResult);
			requestToExecuteChanges(REQUEST_RESOURCE_MODIFY_RESOURCE_SCHEMA,
					RESOURCE_OPENDJ_OID, ResourceType.class, task, null,
					parentResult);

			PrismObject<ResourceType> res = repositoryService
					.getObject(ResourceType.class, RESOURCE_OPENDJ_OID, null,
							parentResult);
			// LOGGER.trace("resource schema handling after modify: {}",
			// prismContext.silentMarshalObject(res.asObjectable(), LOGGER));

			repoAddObjectFromFile(USER_ABOMBA_FILENAME, UserType.class,
					parentResult);
			requestToExecuteChanges(REQUEST_USER_MODIFY_ADD_ACCOUNT_DIRECTLY,
					USER_ABOMBA_OID, UserType.class, task, null, parentResult);

			String abombaOid = assertUserOneAccountRef(USER_ABOMBA_OID);

			ShadowType abombaShadow = repositoryService.getObject(
					ShadowType.class, abombaOid, null, parentResult)
					.asObjectable();
			assertShadowName(abombaShadow,
					"uid=abomba,OU=people,DC=example,DC=com");

			repoAddObjectFromFile(USER_ABOM_FILENAME, UserType.class,
					parentResult);
			requestToExecuteChanges(REQUEST_USER_MODIFY_ADD_ACCOUNT_DIRECTLY,
					USER_ABOM_OID, UserType.class, task, null, parentResult);

			String abomOid = assertUserOneAccountRef(USER_ABOM_OID);

			ShadowType abomShadow = repositoryService.getObject(
					ShadowType.class, abomOid, null, parentResult)
					.asObjectable();
			assertShadowName(abomShadow,
					"uid=abomba1,OU=people,DC=example,DC=com");

			ReferenceDelta abombaDeleteAccDelta = ReferenceDelta
					.createModificationDelete(ShadowType.class,
							UserType.F_LINK_REF, prismContext,
							new PrismReferenceValue(abombaOid));
			ObjectDelta d = ObjectDelta.createModifyDelta(USER_ABOMBA_OID,
					abombaDeleteAccDelta, UserType.class, prismContext);
			modelService.executeChanges(createDeltaCollection(d), null, task,
					parentResult);

			assertUserNoAccountRef(USER_ABOMBA_OID, parentResult);

			repositoryService.getObject(ShadowType.class, abombaOid, null,
					parentResult);

			ReferenceDelta abomDeleteAccDelta = ReferenceDelta
					.createModificationDelete(ShadowType.class,
							UserType.F_LINK_REF, prismContext,
							abomShadow.asPrismObject());
			ObjectDelta d2 = ObjectDelta.createModifyDelta(USER_ABOM_OID,
					abomDeleteAccDelta, UserType.class, prismContext);
			modelService.executeChanges(createDeltaCollection(d2), null, task,
					parentResult);

			assertUserNoAccountRef(USER_ABOM_OID, parentResult);
			try {
				repositoryService.getObject(ShadowType.class, abomOid, null,
						parentResult);
				fail("Expected that shadow abom does not exist, but it is");
			} catch (ObjectNotFoundException ex) {
				// this is expected
			} catch (Exception ex) {
				fail("Expected object not found exception but got " + ex);
			}

			LOGGER.info("starting second execution request for user abomba");
			OperationResult result = new OperationResult("Add account already exist result.");
			requestToExecuteChanges(REQUEST_USER_MODIFY_ADD_ACCOUNT_DIRECTLY,
					USER_ABOMBA_OID, UserType.class, task, null, result);

			
			String abombaOid2 = assertUserOneAccountRef(USER_ABOMBA_OID);
			ShadowType abombaShadow2 = repositoryService.getObject(
					ShadowType.class, abombaOid2, null, result)
					.asObjectable();
			assertShadowName(abombaShadow2,
					"uid=abomba,OU=people,DC=example,DC=com");

			
			result.computeStatus();
			
			LOGGER.info("Displaying execute changes result");
			display(result);
			
//			assertEquals("Expected partial error. ", OperationResultStatus.PARTIAL_ERROR, result.getStatus());
			
			// return the previous changes of resource back
			Collection<? extends ItemDelta> schemaHandlingDelta = PropertyDelta
					.createModificationReplacePropertyCollection(
							ResourceType.F_SCHEMA_HANDLING,
							resourceTypeOpenDjrepo.asPrismObject()
									.getDefinition(), oldSchemaHandlig);
			PropertyDelta syncDelta = PropertyDelta
					.createModificationReplaceProperty(
							ResourceType.F_SYNCHRONIZATION,
							resourceTypeOpenDjrepo.asPrismObject()
									.getDefinition(), oldSynchronization);
			((Collection) schemaHandlingDelta).add(syncDelta);
			repositoryService.modifyObject(ResourceType.class,
					RESOURCE_OPENDJ_OID, schemaHandlingDelta, parentResult);
		} catch (Exception ex) {
			LOGGER.info("error: " + ex.getMessage(), ex);
			throw ex;
		}
	}

	@Test
	public void test017deleteObjectNotFound() throws Exception {
		TestUtil.displayTestTile("test017deleteObjectNotFound");
		OperationResult parentResult = new OperationResult("Delete object not found");

		repoAddObjectFromFile(ACCOUNT_GUYBRUSH_FILENAME, ShadowType.class, parentResult);
		repoAddObjectFromFile(USER_GUYBRUSH_FILENAME, UserType.class, parentResult);

		
		Task task = taskManager.createTaskInstance();
		requestToExecuteChanges(REQUEST_USER_MODIFY_DELETE_ACCOUNT, USER_GUYBRUSH_OID, UserType.class, task, null, parentResult);

		// WHEN
		ObjectDelta deleteDelta = ObjectDelta.createDeleteDelta(ShadowType.class, ACCOUNT_GUYBRUSH_OID, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltaCollection(deleteDelta);
		modelService.executeChanges(deltas, null, task, parentResult);

		try {
			repositoryService.getObject(ShadowType.class, ACCOUNT_GUYBRUSH_OID, null, parentResult);
		} catch (Exception ex) {
			if (!(ex instanceof ObjectNotFoundException)) {
				fail("Expected ObjectNotFoundException but got " + ex);
			}
		}
		
		assertUserNoAccountRef(USER_GUYBRUSH_OID, parentResult);

		repositoryService.deleteObject(UserType.class, USER_GUYBRUSH_OID, parentResult);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test018AmodifyObjectNotFound() throws Exception {
		TestUtil.displayTestTile("test018AmodifyObjectNotFound");
		OperationResult parentResult = new OperationResult(
				"Modify account not found => reaction: Delete account");

		repoAddObjectFromFile(ACCOUNT_GUYBRUSH_FILENAME, ShadowType.class, parentResult);
		repoAddObjectFromFile(USER_GUYBRUSH_FILENAME, UserType.class, parentResult);

		assertUserOneAccountRef(USER_GUYBRUSH_OID);
		
		Task task = taskManager.createTaskInstance();
		
		// WHEN
		requestToExecuteChanges(REQUEST_ACCOUNT_MODIFY_NOT_FOUND_DELETE_ACCOUNT, ACCOUNT_GUYBRUSH_OID, ShadowType.class, task, null, parentResult);

		// THEN
		try {
			repositoryService.getObject(ShadowType.class, ACCOUNT_GUYBRUSH_OID, null, parentResult);
			fail("Expected ObjectNotFound but did not get one.");
		} catch (Exception ex) {
			if (!(ex instanceof ObjectNotFoundException)) {
				fail("Expected ObjectNotFoundException but got " + ex);
			}
		}

		assertUserNoAccountRef(USER_GUYBRUSH_OID, parentResult);

		repositoryService.deleteObject(UserType.class, USER_GUYBRUSH_OID, parentResult);

	}

	@Test
	public void test018BModifyObjectNotFoundAssignedAccount() throws Exception {
		final String TEST_NAME = "test018BModifyObjectNotFoundAssignedAccount";
		TestUtil.displayTestTile(TEST_NAME);
		
		// GIVEN
		OperationResult parentResult = new OperationResult(
				"Modify account not found => reaction: Re-create account, apply changes.");

		repoAddObjectFromFile(ACCOUNT_GUYBRUSH_MODIFY_DELETE_FILENAME, ShadowType.class, parentResult);
		repoAddObjectFromFile(USER_GUYBRUSH_NOT_FOUND_FILENAME, UserType.class, parentResult);

		assertUserOneAccountRef(USER_GUYBRUSH_NOT_FOUND_OID);
		
		Task task = taskManager.createTaskInstance();
		
		//WHEN
		requestToExecuteChanges(REQUEST_ACCOUNT_MODIFY_NOT_FOUND_DELETE_ACCOUNT, ACCOUNT_GUYBRUSH_MODIFY_DELETE_OID, ShadowType.class, task, null, parentResult);

		// THEN
		TestUtil.displayThen(TEST_NAME);
		String accountOid = assertUserOneAccountRef(USER_GUYBRUSH_NOT_FOUND_OID);
		
		PrismObject<ShadowType> modifiedAccount = provisioningService.getObject(
				ShadowType.class, accountOid, null, task, parentResult);
		assertNotNull(modifiedAccount);
		assertShadowName(modifiedAccount.asObjectable(), "uid=guybrush123,ou=people,dc=example,dc=com");
//		PrismAsserts.assertEqualsPolyString("Wrong shadow name", "uid=guybrush123,ou=people,dc=example,dc=com", modifiedAccount.asObjectable().getName());
		ResourceAttributeContainer attributeContainer = ShadowUtil
				.getAttributesContainer(modifiedAccount);
		assertAttribute(modifiedAccount.asObjectable(),
				new QName(ResourceTypeUtil.getResourceNamespace(resourceTypeOpenDjrepo), "roomNumber"),
				"cabin");
		assertNotNull(attributeContainer.findProperty(new QName(ResourceTypeUtil
				.getResourceNamespace(resourceTypeOpenDjrepo), "businessCategory")));

	}

	@Test
	public void test018CgetObjectNotFoundAssignedAccount() throws Exception {
		TestUtil.displayTestTile("test018CgetObjectNotFoundAssignedAccount");
		
		// GIVEN
		OperationResult parentResult = new OperationResult(
				"Get account not found => reaction: Re-create account, return re-created.");

		repoAddObjectFromFile(ACCOUNT_HECTOR_FILENAME, ShadowType.class, parentResult);
		repoAddObjectFromFile(USER_HECTOR_NOT_FOUND_FILENAME, UserType.class, parentResult);

		assertUserOneAccountRef(USER_HECTOR_NOT_FOUND_OID);

		Task task = taskManager.createTaskInstance();

		//WHEN
		PrismObject<UserType> modificatedUser = modelService.getObject(UserType.class, USER_HECTOR_NOT_FOUND_OID, null, task, parentResult);
		
		// THEN
		String accountOid = assertOneAccountRef(modificatedUser);
		
		PrismObject<ShadowType> modifiedAccount = modelService.getObject(ShadowType.class, accountOid, null, task, parentResult);
		assertNotNull(modifiedAccount);
		assertShadowName(modifiedAccount.asObjectable(), "uid=hector,ou=people,dc=example,dc=com");
		

	}

	private void assertShadowName(ShadowType shadow, String name){
		PrismAsserts.assertEqualsPolyString("Wrong shadw name", name, shadow.getName());
	}
	
	@Test
	public void test019StopOpenDj() throws Exception {
		TestUtil.displayTestTile("test019TestConnectionOpenDJ");
		openDJController.stop();

		assertEquals("Resource is running", false, EmbeddedUtils.isRunning());

	}

	@Test
	public void test020addObjectCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test020 add object - communication problem");
		OperationResult parentResult = new OperationResult("add object communication error.");
		repoAddObjectFromFile(USER_E_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_E_OID, parentResult);
		
		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_E_OID, UserType.class, task, null, parentResult);
		

		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected handled error but got: " + parentResult.getStatus(), OperationResultStatus.HANDLED_ERROR, parentResult.getStatus());
		
		String accountOid = checkRepoUser(USER_E_OID, parentResult); 

		checkPostponedAccountWithAttributes(accountOid, "e", "e", "e", "e", FailedOperationTypeType.ADD, false, task, parentResult);

	}

	@SuppressWarnings("unchecked")
	@Test
	public void test021addModifyObjectCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test021 add modify object - communication problem");
		OperationResult parentResult = new OperationResult("add object communication error.");

		String accountOid = assertUserOneAccountRef(USER_E_OID);

		 Task task = taskManager.createTaskInstance();
		 
		 //WHEN
		 requestToExecuteChanges(REQUEST_ACCOUNT_MODIFY_COMMUNICATION_PROBLEM, accountOid, ShadowType.class, task, null, parentResult);

		 //THEN
		 checkPostponedAccountWithAttributes(accountOid, "e", "Jackkk", "e", "e", "emp4321", FailedOperationTypeType.ADD, false, task, parentResult);
		

	}

	@Test
	public void test022modifyObjectCommunicationProblem() throws Exception {

		TestUtil.displayTestTile("test022 modify object - communication problem");
		OperationResult parentResult = new OperationResult("modify object - communication problem");
		String accountOid = assertUserOneAccountRef(USER_JACK_OID);

		Task task = taskManager.createTaskInstance();
		//WHEN
		requestToExecuteChanges(REQUEST_ACCOUNT_MODIFY_COMMUNICATION_PROBLEM, accountOid, ShadowType.class, task, null, parentResult);
		
		checkPostponedAccountBasic(accountOid, FailedOperationTypeType.MODIFY, true, parentResult);

	}

	@Test
	public void test023deleteObjectCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test023 delete object - communication problem");
		OperationResult parentResult = new OperationResult("modify object - communication problem");
		
		String accountOid = assertUserOneAccountRef(USER_DENIELS_OID);
		
		Task task = taskManager.createTaskInstance();
		
		//WHEN
		requestToExecuteChanges(REQUEST_USER_MODIFY_DELETE_ACCOUNT_COMMUNICATION_PROBLEM, USER_DENIELS_OID, UserType.class, task, null, parentResult);
		
		assertUserNoAccountRef(USER_DENIELS_OID, parentResult);
		
		ObjectDelta deleteDelta = ObjectDelta.createDeleteDelta(ShadowType.class, ACCOUNT_DENIELS_OID, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltaCollection(deleteDelta);
		modelService.executeChanges(deltas, null, task, parentResult);

		// THEN
		checkPostponedAccountBasic(accountOid, FailedOperationTypeType.DELETE, false, parentResult);

	}

	@Test
	public void test024getAccountCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test024getAccountCommunicationProblem");
		OperationResult result = new OperationResult("test024 get account communication problem");
		ShadowType account = modelService.getObject(ShadowType.class, ACCOUNT_DENIELS_OID,
				null, null, result).asObjectable();
		assertNotNull("Get method returned null account.", account);
		assertNotNull("Fetch result was not set in the shadow.", account.getFetchResult());
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void test025addObjectCommunicationProblemAlreadyExists() throws Exception{
		
		OperationResult parentResult = new OperationResult("Add account already exist unlinked.");
		
		openDJController.start();
	
		Entry entry = openDJController.addEntryFromLdifFile(LDIF_ELAINE_FILENAME);
		SearchResultEntry searchResult = openDJController.searchByUid("elaine");
		OpenDJController.assertAttribute(searchResult, "l", "Caribbean");
		OpenDJController.assertAttribute(searchResult, "givenName", "Elaine");
		OpenDJController.assertAttribute(searchResult, "sn", "Marley");
		OpenDJController.assertAttribute(searchResult, "cn", "Elaine Marley");
		OpenDJController.assertAttribute(searchResult, "mail", "governor.marley@deep.in.the.caribbean.com");
		OpenDJController.assertAttribute(searchResult, "employeeType", "governor");
		OpenDJController.assertAttribute(searchResult, "title", "Governor");
		String dn = searchResult.getDN().toString();
		assertEquals("DN attribute " + dn + " not equals", dn, "uid=elaine,ou=people,dc=example,dc=com");

		openDJController.stop();
		
		
		testAddUserToRepo("add user - test025 account already exists communication problem", USER_ELAINE_FILENAME,
				USER_ELAINE_OID);
		assertUserNoAccountRef(USER_ELAINE_OID, parentResult);

		Task task = taskManager.createTaskInstance();
		
		//WHEN REQUEST_USER_MODIFY_ADD_ACCOUNT_ALERADY_EXISTS_COMMUNICATION_PROBLEM_OPENDJ_FILENAME
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_ELAINE_OID, UserType.class, task, null, parentResult);

		//THEN
		String accountOid = assertUserOneAccountRef(USER_ELAINE_OID);
		
		checkPostponedAccountWithAttributes(accountOid, "elaine", "Elaine", "Marley", "Elaine Marley", FailedOperationTypeType.ADD, false, task, parentResult);
		
	}
	
	@Test
	public void test026modifyObjectTwoTimesCommunicationProblem() throws Exception{
		final String TEST_NAME = "test026modifyObjectTwoTimesCommunicationProblem";
        TestUtil.displayTestTile(this, TEST_NAME);
        
		OperationResult parentResult = new OperationResult(TEST_NAME);
		
		assertUserOneAccountRef(USER_JACK2_OID);
		
		
//		PrismObjectDefinition accountDef = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ResourceObjectShadowType.class);
//		assertNotNull("Account definition must not be null.", accountDef);
		
//		PropertyDelta delta = PropertyDelta.createModificationReplaceProperty(SchemaConstants.PATH_ACTIVATION, accountDef, true);
		Collection<PropertyDelta> modifications = new ArrayList<PropertyDelta>();
//		modifications.add(delta);
		
		PropertyDelta fullNameDelta = PropertyDelta.createModificationReplaceProperty(new ItemPath(UserType.F_FULL_NAME), getUserDefinition(), new PolyString("jackNew2"));
		modifications.add(fullNameDelta);
		
		PrismPropertyValue<ActivationStatusType> enabledUserAction = new PrismPropertyValue<ActivationStatusType>(ActivationStatusType.ENABLED, OriginType.USER_ACTION, null);
		PropertyDelta<ActivationStatusType> enabledDelta = PropertyDelta.createDelta(SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS, getUserDefinition());
		enabledDelta.addValueToAdd(enabledUserAction);
		modifications.add(enabledDelta);
		
		ObjectDelta objectDelta = ObjectDelta.createModifyDelta(USER_JACK2_OID, modifications, UserType.class, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltaCollection(objectDelta);
		
		
		Task task = taskManager.createTaskInstance();
		
		modelService.executeChanges(deltas, null, task, parentResult);
		parentResult.computeStatus();
		String accountOid = assertUserOneAccountRef(USER_JACK2_OID);

		PrismObject<ShadowType> account = modelService.getObject(ShadowType.class, accountOid, null, task, parentResult);
		assertNotNull(account);
		ShadowType shadow = account.asObjectable();
		assertNotNull(shadow.getObjectChange());
		display("shadow after communication problem", shadow);
		
		
		Collection<PropertyDelta> newModifications = new ArrayList<PropertyDelta>();
		PropertyDelta fullNameDeltaNew = PropertyDelta.createModificationReplaceProperty(new ItemPath(UserType.F_FULL_NAME), getUserDefinition(), new PolyString("jackNew2a"));
		newModifications.add(fullNameDeltaNew);
		
		
		PropertyDelta givenNameDeltaNew = PropertyDelta.createModificationReplaceProperty(new ItemPath(UserType.F_GIVEN_NAME), getUserDefinition(), new PolyString("jackNew2a"));
		newModifications.add(givenNameDeltaNew);
		
		PrismPropertyValue<ActivationStatusType> enabledOutboundAction = new PrismPropertyValue<ActivationStatusType>(ActivationStatusType.ENABLED, OriginType.USER_ACTION, null);
		PropertyDelta<ActivationStatusType> enabledDeltaNew = PropertyDelta.createDelta(SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS, getUserDefinition());
		enabledDeltaNew.addValueToAdd(enabledOutboundAction);
		newModifications.add(enabledDeltaNew);
		
		ObjectDelta newObjectDelta = ObjectDelta.createModifyDelta(USER_JACK2_OID, newModifications, UserType.class, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> newDeltas = createDeltaCollection(newObjectDelta);
		
		
		modelService.executeChanges(newDeltas, null, task, parentResult);
		
		account = modelService.getObject(ShadowType.class, accountOid, null, task, parentResult);
		assertNotNull(account);
		shadow = account.asObjectable();
		assertNotNull(shadow.getObjectChange());
		display("shadow after communication problem", shadow);
//		parentResult.computeStatus();
//		assertEquals("expected handled error in the result", OperationResultStatus.HANDLED_ERROR, parentResult.getStatus());
		
		
	}
	
	/**
	 * this test simulates situation, when someone tries to add account while
	 * resource is down and this account is created by next get call on this
	 * account
	 * 
	 * @throws Exception
	 */
	@Test
	public void test027getDiscoveryAddCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test027getDiscoveryAddCommunicationProblem");
		OperationResult parentResult = new OperationResult("test027getDiscoveryAddCommunicationProblem");
		repoAddObjectFromFile(USER_ANGELIKA_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_ANGELIKA_OID, parentResult);
		
		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_ANGELIKA_OID, UserType.class, task, null, parentResult);
		
		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected handled error but got: " + parentResult.getStatus(), OperationResultStatus.HANDLED_ERROR, parentResult.getStatus());
		
		
		String accountOid = assertUserOneAccountRef(USER_ANGELIKA_OID);

		checkPostponedAccountWithAttributes(accountOid, "angelika", "angelika", "angelika", "angelika", FailedOperationTypeType.ADD, false, task, parentResult);
		
		//start openDJ
		openDJController.start();
		//and set the resource availability status to UP
		modifyResourceAvailabilityStatus(AvailabilityStatusType.UP, parentResult);
		
		checkNormalizedShadowWithAttributes(accountOid, "angelika", "angelika", "angelika", "angelika", false, task, parentResult);
	}
	
	private void modifyResourceAvailabilityStatus(AvailabilityStatusType status, OperationResult parentResult) throws Exception {
		PropertyDelta resourceStatusDelta = PropertyDelta.createModificationReplaceProperty(new ItemPath(
				ResourceType.F_OPERATIONAL_STATE, OperationalStateType.F_LAST_AVAILABILITY_STATUS),
				resourceTypeOpenDjrepo.asPrismObject().getDefinition(), status);
		Collection<PropertyDelta> modifications = new ArrayList<PropertyDelta>();
		modifications.add(resourceStatusDelta);
		repositoryService.modifyObject(ResourceType.class, resourceTypeOpenDjrepo.getOid(), modifications, parentResult);
	}
	
	@Test
	public void test028getDiscoveryModifyCommunicationProblem() throws Exception{
		TestUtil.displayTestTile("test028getDiscoveryModifyCommunicationProblem");
		OperationResult parentResult = new OperationResult("test028getDiscoveryModifyCommunicationProblem");
		
		//prepare user 
		repoAddObjectFromFile(USER_ALICE_FILENAME, UserType.class, parentResult);
		
		assertUserNoAccountRef(USER_ALICE_OID, parentResult);

		Task task = taskManager.createTaskInstance();
		//and add account to the user while resource is UP
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_ALICE_OID, UserType.class, task, null, parentResult);
		
		//then stop openDJ
		openDJController.stop();
		
		String accountOid = assertUserOneAccountRef(USER_ALICE_OID);

		//and make some modifications to the account while resource is DOWN
		requestToExecuteChanges(REQUEST_ACCOUNT_MODIFY_COMMUNICATION_PROBLEM, accountOid, ShadowType.class, task, null, parentResult);

		//check the state after execution
		checkPostponedAccountBasic(accountOid, FailedOperationTypeType.MODIFY, true, parentResult);

		//start openDJ
		openDJController.start();
		//and set the resource availability status to UP
		modifyResourceAvailabilityStatus(AvailabilityStatusType.UP, parentResult);
		
		//and then try to get account -> result is that the modifications will be applied to the account
		ShadowType aliceAccount = checkNormalizedShadowWithAttributes(accountOid, "alice", "Jackkk", "alice", "alice", true, task, parentResult);
		assertAttribute(aliceAccount, resourceTypeOpenDjrepo, "employeeNumber", "emp4321");

		//and finally stop openDJ
		openDJController.stop();
	}
	
	/**
	 * this test simulates situation, when someone tries to add account while
	 * resource is down and this account is created by next get call on this
	 * account
	 * 
	 * @throws Exception
	 */
	@Test
	public void test029modifyDiscoveryAddCommunicationProblem() throws Exception {
		TestUtil.displayTestTile("test029modifyDiscoveryAddCommunicationProblem");
		OperationResult parentResult = new OperationResult("test029modifyDiscoveryAddCommunicationProblem");
		repoAddObjectFromFile(USER_BOB_NO_FAMILY_NAME_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_BOB_NO_FAMILY_NAME_OID, parentResult);

		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_BOB_NO_FAMILY_NAME_OID, UserType.class, task, null, parentResult);
		
		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected handled error but got: " + parentResult.getStatus(), OperationResultStatus.HANDLED_ERROR, parentResult.getStatus());
		
		String accountOid = assertUserOneAccountRef(USER_BOB_NO_FAMILY_NAME_OID);

		checkPostponedAccountWithAttributes(accountOid, "bob", "Bob", null,  "Bob Dylan", FailedOperationTypeType.ADD, false, task, parentResult);
		
		//start openDJ
		openDJController.start();
		//and set the resource availability status to UP
		modifyResourceAvailabilityStatus(AvailabilityStatusType.UP, parentResult);
		
		try {
			modelService.getObject(ShadowType.class, accountOid, null, task,
					parentResult);
			fail("expected schema exception was not thrown");
		} catch (SchemaException ex) {
			LOGGER.info("schema exeption while trying to re-add account after communication problem without family name..this is expected.");
			parentResult.muteLastSubresultError();
			parentResult.recordSuccess();

		}
		
		OperationResult modifyFamilyNameResult = new OperationResult("execute changes -> modify user's family name");
		LOGGER.trace("execute changes -> modify user's family name");
		Collection<? extends ItemDelta> familyNameDelta = PropertyDelta.createModificationReplacePropertyCollection(UserType.F_FAMILY_NAME, getUserDefinition(), new PolyString("Dylan"));
		ObjectDelta familyNameD = ObjectDelta.createModifyDelta(USER_BOB_NO_FAMILY_NAME_OID, familyNameDelta, UserType.class, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> modifyFamilyNameDelta = createDeltaCollection(familyNameD);
		modelService.executeChanges(modifyFamilyNameDelta, null, task, modifyFamilyNameResult);
		
		modifyFamilyNameResult.computeStatus();
		display("add object communication problem result: ", modifyFamilyNameResult);
		assertEquals("Expected handled error but got: " + modifyFamilyNameResult.getStatus(), OperationResultStatus.SUCCESS, modifyFamilyNameResult.getStatus());
		
		PrismObject<ShadowType> bobRepoAcc = repositoryService.getObject(ShadowType.class, accountOid, null, modifyFamilyNameResult);
		assertNotNull(bobRepoAcc);
		ShadowType bobRepoAccount = bobRepoAcc.asObjectable();
		displayJaxb("Shadow after discovery: ", bobRepoAccount, ShadowType.COMPLEX_TYPE);
		assertNull("Bob's account after discovery must not have failed opertion.", bobRepoAccount.getFailedOperationType());
		assertNull("Bob's account after discovery must not have result.", bobRepoAccount.getResult());
		assertNotNull("Bob's account must contain reference on the resource", bobRepoAccount.getResourceRef());
		
		checkNormalizedShadowWithAttributes(accountOid, "bob", "Bob", "Dylan", "Bob Dylan", false, task, parentResult);
		
//		openDJController.stop();
	}
	
	private void checkNormalizedShadowWithAttributes(String accountOid, String uid, String givenName, String sn, String cn, String employeeType, boolean modify, Task task, OperationResult parentResult) throws Exception{
		ShadowType resourceAccount = checkNormalizedShadowBasic(accountOid, uid, modify, null, task, parentResult);
		assertAttributes(resourceAccount, uid, givenName, sn, cn);
		assertAttribute(resourceAccount, resourceTypeOpenDjrepo, "employeeType", employeeType);
	}
	
	private ShadowType checkNormalizedShadowWithAttributes(String accountOid, String uid, String givenName, String sn, String cn, boolean modify, Task task, OperationResult parentResult) throws Exception{
		ShadowType resourceAccount = checkNormalizedShadowBasic(accountOid, uid, modify, null, task, parentResult);
		assertAttributes(resourceAccount, uid, givenName, sn, cn);
		return resourceAccount;
	}
	
	private ShadowType checkNormalizedShadowBasic(String accountOid, String name, boolean modify, Collection<SelectorOptions<GetOperationOptions>> options,Task task, OperationResult parentResult) throws Exception{
		PrismObject<ShadowType> resourceAcc = modelService.getObject(ShadowType.class, accountOid, options, task, parentResult);
		assertNotNull(resourceAcc);
		ShadowType resourceAccount = resourceAcc.asObjectable();
		displayJaxb("Shadow after discovery: ", resourceAccount, ShadowType.COMPLEX_TYPE);
		assertNull(name + "'s account after discovery must not have failed opertion.", resourceAccount.getFailedOperationType());
		assertNull(name + "'s account after discovery must not have result.", resourceAccount.getResult());
		assertNotNull(name + "'s account must contain reference on the resource", resourceAccount.getResourceRef());
		assertEquals(resourceTypeOpenDjrepo.getOid(), resourceAccount.getResourceRef().getOid());
		
//		if (modify){
//			assertNull(name + "'s account must not have object change", resourceAccount.getObjectChange());
//		}
		
		return resourceAccount;
//		assertNotNull("Identifier in the angelica's account after discovery must not be null.",ResourceObjectShadowUtil.getAttributesContainer(faieldAccount).getIdentifier().getRealValue());
		
	}
	
	@Test
	public void test030modifyObjectCommunicationProblemWeakMapping() throws Exception{
//		openDJController.start();
		TestUtil.displayTestTile("test030modifyObjectCommunicationProblemWeakMapping");
		OperationResult parentResult = new OperationResult("test30modifyObjectCommunicationProblemWeakMapping");
		repoAddObjectFromFile(USER_JOHN_WEAK_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_JOHN_WEAK_OID, parentResult);
		
		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_JOHN_WEAK_OID, UserType.class, task, null, parentResult);
		
		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected success but got: " + parentResult.getStatus(), OperationResultStatus.SUCCESS, parentResult.getStatus());
		
		String accountOid = assertUserOneAccountRef(USER_JOHN_WEAK_OID);
		
		checkNormalizedShadowWithAttributes(accountOid, "john", "john", "weak", "john weak", "manager", false, task, parentResult);
		
		
		//stop opendj and try to modify employeeType (weak mapping)
		openDJController.stop();
		
		
		LOGGER.info("start modifying user - account with weak mapping after stopping opendj.");
		
		requestToExecuteChanges(REQUEST_USER_MODIFY_WEAK_MAPPING_COMMUNICATION_PROBLEM, USER_JOHN_WEAK_OID, UserType.class, task, null, parentResult);

		checkNormalizedShadowBasic(accountOid, "john", true, SelectorOptions.createCollection(GetOperationOptions.createDoNotDiscovery()), task, parentResult);
		
	}

	
	@Test
	public void test031modifyObjectCommunicationProblemWeakAndStrongMapping() throws Exception{
		openDJController.start();
		TestUtil.displayTestTile("test031modifyObjectCommunicationProblemWeakAndStrongMapping");
		OperationResult parentResult = new OperationResult("test31modifyObjectCommunicationProblemWeakAndStrongMapping");
		repoAddObjectFromFile(USER_DONALD_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_DONALD_OID, parentResult);
				
		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_DONALD_OID, UserType.class, task, null, parentResult);
		
		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected success but got: " + parentResult.getStatus(), OperationResultStatus.SUCCESS, parentResult.getStatus());
		
		String accountOid = assertUserOneAccountRef(USER_DONALD_OID);
				
		ShadowType johnAccountType = checkNormalizedShadowWithAttributes(accountOid, "donald", "donald", "trump", "donald trump", false, task, parentResult);
		assertAttribute(johnAccountType, resourceTypeOpenDjrepo, "employeeType", "manager");

		//stop opendj and try to modify employeeType (weak mapping)
		openDJController.stop();
		
		
		LOGGER.info("start modifying user - account with weak mapping after stopping opendj.");
		
		requestToExecuteChanges(REQUEST_USER_MODIFY_WEAK_STRONG_MAPPING_COMMUNICATION_PROBLEM, USER_DONALD_OID, UserType.class, task, null, parentResult);

		johnAccountType = checkPostponedAccountBasic(accountOid, FailedOperationTypeType.MODIFY, true, parentResult);
		ObjectDelta deltaInAccount = DeltaConvertor.createObjectDelta(johnAccountType.getObjectChange(), prismContext);
		assertTrue("Delta stored in account must contain given name modification", deltaInAccount.hasItemDelta(new ItemPath(ShadowType.F_ATTRIBUTES, new QName(resourceTypeOpenDjrepo.getNamespace(), "givenName"))));
		assertFalse("Delta stored in account must not contain employeeType modification", deltaInAccount.hasItemDelta(new ItemPath(ShadowType.F_ATTRIBUTES, new QName(resourceTypeOpenDjrepo.getNamespace(), "employeeType"))));
		assertNotNull("Donald's account must contain reference on the resource", johnAccountType.getResourceRef());
	
		//TODO: check on user if it was processed successfully (add this check also to previous (30) test..
	}
	
	//TODO: enable after notify failure will be implemented..
	@Test(enabled = false)
	public void test032getDiscoveryAddCommunicationProblemAlreadyExists() throws Exception{
		TestUtil.displayTestTile("test032getDiscoveryAddCommunicationProblemAlreadyExists");
		OperationResult parentResult = new OperationResult("test032getDiscoveryAddCommunicationProblemAlreadyExists");
		repoAddObjectFromFile(USER_DISCOVERY_FILENAME, UserType.class, parentResult);

		assertUserNoAccountRef(USER_DISCOVERY_OID, parentResult);
				
		Task task = taskManager.createTaskInstance();
		
		//REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
		requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_DISCOVERY_OID, UserType.class, task, null, parentResult);
		
		parentResult.computeStatus();
		display("add object communication problem result: ", parentResult);
		assertEquals("Expected success but got: " + parentResult.getStatus(), OperationResultStatus.HANDLED_ERROR, parentResult.getStatus());
		
		String accountOid = assertUserOneAccountRef(USER_DISCOVERY_OID);
		
		openDJController.start();
		assertTrue(EmbeddedUtils.isRunning());
		
		Entry entry = openDJController.addEntryFromLdifFile(LDIF_DISCOVERY_FILENAME);
		SearchResultEntry searchResult = openDJController.searchByUid("discovery");
		OpenDJController.assertAttribute(searchResult, "l", "Caribbean");
		OpenDJController.assertAttribute(searchResult, "givenName", "discovery");
		OpenDJController.assertAttribute(searchResult, "sn", "discovery");
		OpenDJController.assertAttribute(searchResult, "cn", "discovery");
		OpenDJController.assertAttribute(searchResult, "mail", "discovery@deep.in.the.caribbean.com");
		String dn = searchResult.getDN().toString();
		assertEquals("DN attribute " + dn + " not equals", dn, "uid=discovery,ou=people,dc=example,dc=com");
		
		modifyResourceAvailabilityStatus(AvailabilityStatusType.UP, parentResult);

		modelService.getObject(ShadowType.class, accountOid, null, task, parentResult);
		
	}

	/**
	 * Adding a user (morgan) that has an OpenDJ assignment. But the equivalent account already exists on
	 * OpenDJ. The account should be linked.
	 */	
	@Test
    public void test100AddUserMorganWithAssignment() throws Exception {
		final String TEST_NAME = "test100AddUserMorganWithAssignment";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        
        openDJController.start();
		assertTrue(EmbeddedUtils.isRunning());
		
        Task task = taskManager.createTaskInstance(ConsistencyTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        dummyAuditService.clear();
        
        Entry entry = openDJController.addEntryFromLdifFile(LDIF_MORGAN_FILENAME);
        display("Entry from LDIF", entry);
        
        PrismObject<UserType> user = PrismTestUtil.parseObject(new File(USER_MORGAN_FILENAME));
        display("Adding user", user);
        ObjectDelta<UserType> userDelta = ObjectDelta.createAddDelta(user);
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);
                
		// WHEN
        TestUtil.displayWhen(TEST_NAME);
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		TestUtil.displayThen(TEST_NAME);
		result.computeStatus();
//		assertEquals("Expected handled error but got: " + result.getStatus(), OperationResultStatus.HANDLED_ERROR, result.getStatus());
        
		PrismObject<UserType> userMorgan = modelService.getObject(UserType.class, USER_MORGAN_OID, null, task, result);
		display("User morgan after", userMorgan);
        UserType userMorganType = userMorgan.asObjectable();
        assertEquals("Unexpected number of accountRefs", 1, userMorganType.getLinkRef().size());
        ObjectReferenceType accountRefType = userMorganType.getLinkRef().get(0);
        String accountOid = accountRefType.getOid();
        assertFalse("No accountRef oid", StringUtils.isBlank(accountOid));
        
		// Check shadow
        PrismObject<ShadowType> accountShadow = repositoryService.getObject(ShadowType.class, accountOid, null, result);
        assertAccountShadowRepo(accountShadow, accountOid, "uid=morgan,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        
        // Check account
        PrismObject<ShadowType> accountModel = modelService.getObject(ShadowType.class, accountOid, null, task, result);
        assertAccountShadowModel(accountModel, accountOid, "uid=morgan,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        
        // TODO: check OpenDJ Account        
	}
	
	/**
	 * Adding a user (morgan) that has an OpenDJ assignment. But the equivalent account already exists on
	 * OpenDJ and there is also corresponding shadow in the repo. The account should be linked.
	 */	
	@Test
    public void test101AddUserChuckWithAssignment() throws Exception {
		final String TEST_NAME = "test101AddUserChuckWithAssignment";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN	
        Task task = taskManager.createTaskInstance(ConsistencyTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        dummyAuditService.clear();
        
//        Entry entry = openDJController.addEntryFromLdifFile(LDIF_MORGAN_FILENAME);
//        display("Entry from LDIF", entry);
        
        PrismObject<ShadowType> account = PrismTestUtil.parseObject(new File(ACCOUNT_CHUCK_FILENAME));
        String accOid = provisioningService.addObject(account, null, null, task, result);
//        
        PrismObject<UserType> user = PrismTestUtil.parseObject(new File(USER_CHUCK_FILENAME));
        display("Adding user", user);
        ObjectDelta<UserType> userDelta = ObjectDelta.createAddDelta(user);
        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);
                
		// WHEN
        TestUtil.displayWhen(TEST_NAME);
		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		TestUtil.displayThen(TEST_NAME);
		result.computeStatus();
//		assertEquals("Expected handled error but got: " + result.getStatus(), OperationResultStatus.HANDLED_ERROR, result.getStatus());
        
		PrismObject<UserType> userMorgan = modelService.getObject(UserType.class, USER_CHUCK_OID, null, task, result);
		display("User morgan after", userMorgan);
        UserType userMorganType = userMorgan.asObjectable();
        assertEquals("Unexpected number of accountRefs", 1, userMorganType.getLinkRef().size());
        ObjectReferenceType accountRefType = userMorganType.getLinkRef().get(0);
        String accountOid = accountRefType.getOid();
        assertFalse("No accountRef oid", StringUtils.isBlank(accountOid));
        assertEquals("old oid not used..", accOid, accountOid);
        
		// Check shadow
        PrismObject<ShadowType> accountShadow = repositoryService.getObject(ShadowType.class, accountOid, null, result);
        assertAccountShadowRepo(accountShadow, accountOid, "uid=chuck,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        
        // Check account
        PrismObject<ShadowType> accountModel = modelService.getObject(ShadowType.class, accountOid, null, task, result);
        assertAccountShadowModel(accountModel, accountOid, "uid=chuck,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        ShadowType accountTypeModel = accountModel.asObjectable();
        
        assertAttribute(accountTypeModel, resourceTypeOpenDjrepo, "uid", "chuck");
		assertAttribute(accountTypeModel, resourceTypeOpenDjrepo, "givenName", "Chuck");
		assertAttribute(accountTypeModel, resourceTypeOpenDjrepo, "sn", "Norris");
		assertAttribute(accountTypeModel, resourceTypeOpenDjrepo, "cn", "Chuck Norris");
		
        // TODO: check OpenDJ Account        
	}

	
	/**
	 * Assigning accoun to user, account with the same identifier exist on the resource and there is also shadow in the repository. The account should be linked.
	 */	
	@Test
    public void test102assignAccountToHerman() throws Exception {
		final String TEST_NAME = "test102assignAccountToHerman";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN	
        Task task = taskManager.createTaskInstance(ConsistencyTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        dummyAuditService.clear();
        
//        Entry entry = openDJController.addEntryFromLdifFile(LDIF_MORGAN_FILENAME);
//        display("Entry from LDIF", entry);
        
        PrismObject<ShadowType> account = PrismTestUtil.parseObject(new File(ACCOUNT_HERMAN_FILENAME));
        String accOid = provisioningService.addObject(account, null, null, task, result);
//        
        repoAddObjectFromFile(USER_HERMAN_FILENAME, UserType.class, result);
        
        PrismObject<UserType> user = PrismTestUtil.parseObject(new File(USER_HERMAN_FILENAME));
        display("Adding user", user);
        
        //REQUEST_USER_MODIFY_ADD_ACCOUNT_COMMUNICATION_PROBLEM
        requestToExecuteChanges(REQUEST_USER_MODIFY_ASSIGNE_ACCOUNT, USER_HERMAN_OID, UserType.class, task, null, result);
        

//        ObjectDelta<UserType> userDelta = ObjectDelta.createAddDelta(user);
//        Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(userDelta);
//                
//		// WHEN
//        displayWhen(TEST_NAME);
//		modelService.executeChanges(deltas, null, task, result);
		
		// THEN
		TestUtil.displayThen(TEST_NAME);
		result.computeStatus();
//		assertEquals("Expected handled error but got: " + result.getStatus(), OperationResultStatus.HANDLED_ERROR, result.getStatus());
        
		PrismObject<UserType> userMorgan = modelService.getObject(UserType.class, USER_HERMAN_OID, null, task, result);
		display("User morgan after", userMorgan);
        UserType userMorganType = userMorgan.asObjectable();
        assertEquals("Unexpected number of accountRefs", 1, userMorganType.getLinkRef().size());
        ObjectReferenceType accountRefType = userMorganType.getLinkRef().get(0);
        String accountOid = accountRefType.getOid();
        assertFalse("No accountRef oid", StringUtils.isBlank(accountOid));
        assertEquals("old oid not used..", accOid, accountOid);
        assertEquals("old oid not used..", ACCOUNT_HERMAN_OID, accountOid);
        
		// Check shadow
        PrismObject<ShadowType> accountShadow = repositoryService.getObject(ShadowType.class, accountOid, null, result);
        assertAccountShadowRepo(accountShadow, accountOid, "uid=ht,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        
        // Check account
        PrismObject<ShadowType> accountModel = modelService.getObject(ShadowType.class, accountOid, null, task, result);
        assertAccountShadowModel(accountModel, accountOid, "uid=ht,ou=people,dc=example,dc=com", resourceTypeOpenDjrepo);
        ShadowType accountTypeModel = accountModel.asObjectable();
        
        // Check account's attributes
        assertAttributes(accountTypeModel, "ht", "Herman", "Toothrot", "Herman Toothrot");
		
        // TODO: check OpenDJ Account        
	}


	
	// This should run last. It starts a task that may interfere with other tests
	@Test
	public void test800Reconciliation() throws Exception {
		final String TEST_NAME = "test800Reconciliation";
        TestUtil.displayTestTile(this, TEST_NAME);

		final OperationResult result = new OperationResult(ConsistencyTest.class.getName() + "." + TEST_NAME);

		// TODO: remove this if the previous test is enabled
//		openDJController.start();
		
		// rename eobject dirrectly on resource before the recon start ..it tests the rename + recon situation (MID-1594)
		
		// precondition
		assertTrue(EmbeddedUtils.isRunning());
		UserType userJack = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result).asObjectable();
		display("Jack before", userJack);
		
		
		LOGGER.info("start running task");
		// WHEN
		repoAddObjectFromFile(TASK_OPENDJ_RECONCILIATION_FILENAME, TaskType.class, result);
		waitForTaskNextRun(TASK_OPENDJ_RECONCILIATION_OID, false, 60000);

		// THEN
		
		// STOP the task. We don't need it any more and we don't want to give it
		// a chance to run more than once
		taskManager.deleteTask(TASK_OPENDJ_RECONCILIATION_OID, result);

		// check if the account was added after reconciliation
		UserType userE = repositoryService.getObject(UserType.class, USER_E_OID, null, result).asObjectable();
		String accountOid = assertUserOneAccountRef(USER_E_OID);
		
		ShadowType eAccount = checkNormalizedShadowWithAttributes(accountOid, "e", "Jackkk", "e", "e", true, null, result);
		assertAttribute(eAccount, resourceTypeOpenDjrepo, "employeeNumber", "emp4321");
		ResourceAttributeContainer attributeContainer = ShadowUtil
				.getAttributesContainer(eAccount);
		Collection<ResourceAttribute<?>> identifiers = attributeContainer.getIdentifiers();
		assertNotNull(identifiers);
		assertFalse(identifiers.isEmpty());
		assertEquals(1, identifiers.size());
		
		
		// check if the account was modified during reconciliation process
		String jackAccountOid = assertUserOneAccountRef(USER_JACK_OID);
		ShadowType modifiedAccount = checkNormalizedShadowBasic(jackAccountOid, "jack", true, SelectorOptions.createCollection(GetOperationOptions.createDoNotDiscovery()), null, result);
		assertAttribute(modifiedAccount, resourceTypeOpenDjrepo, "givenName", "Jackkk");
		assertAttribute(modifiedAccount, resourceTypeOpenDjrepo, "employeeNumber", "emp4321");

		
		// check if the account was deleted during the reconciliation process
		try {
			modelService.getObject(ShadowType.class, ACCOUNT_DENIELS_OID, null, null, result);
			fail("Expected ObjectNotFoundException but haven't got one.");
		} catch (Exception ex) {
			if (!(ex instanceof ObjectNotFoundException)) {
				fail("Expected ObjectNotFoundException but got " + ex);
			}

		}
		
		String elaineAccountOid = assertUserOneAccountRef(USER_ELAINE_OID);
		modifiedAccount = checkNormalizedShadowBasic(elaineAccountOid, "elaine", true, SelectorOptions.createCollection(GetOperationOptions.createDoNotDiscovery()), null, result);
		assertIcfsNameAttribute(modifiedAccount, "uid=elaine,ou=people,dc=example,dc=com");

		accountOid = assertUserOneAccountRef(USER_JACK2_OID);
		ShadowType jack2Shadow = checkNormalizedShadowBasic(accountOid, "jack2", true, SelectorOptions.createCollection(GetOperationOptions.createDoNotDiscovery()), null, result);
		assertAttribute(jack2Shadow, resourceTypeOpenDjrepo, "givenName", "jackNew2a");
		assertAttribute(jack2Shadow, resourceTypeOpenDjrepo, "cn", "jackNew2a");

	}
	
	@Test
	public void test801testReconciliationRename() throws Exception{
		final String TEST_NAME = "test801testReconciliationRename";
        TestUtil.displayTestTile(this, TEST_NAME);

		final OperationResult result = new OperationResult(ConsistencyTest.class.getName() + "." + TEST_NAME);

LOGGER.info("starting rename");
		
		openDJController.executeRenameChange(LDIF_MODIFY_RENAME_FILENAME);
		LOGGER.info("rename ended");
//		SearchResultEntry res = openDJController.searchByUid("e");
//		LOGGER.info("E OBJECT AFTER RENAME " + res.toString());
		
		LOGGER.info("start running task");
		// WHEN
		repoAddObjectFromFile(TASK_OPENDJ_RECONCILIATION_FILENAME, TaskType.class, result);
		waitForTaskNextRun(TASK_OPENDJ_RECONCILIATION_OID, false, 60000);

		// THEN
		
		// STOP the task. We don't need it any more and we don't want to give it
		// a chance to run more than once
		taskManager.deleteTask(TASK_OPENDJ_RECONCILIATION_OID, result);

		// check if the account was added after reconciliation
		UserType userE = repositoryService.getObject(UserType.class, USER_E_OID, null, result).asObjectable();
		String accountOid = assertUserOneAccountRef(USER_E_OID);
		
		ShadowType eAccount = checkNormalizedShadowWithAttributes(accountOid, "e123", "Jackkk", "e", "e", true, null, result);
		assertAttribute(eAccount, resourceTypeOpenDjrepo, "employeeNumber", "emp4321");
		ResourceAttributeContainer attributeContainer = ShadowUtil
				.getAttributesContainer(eAccount);
		Collection<ResourceAttribute<?>> identifiers = attributeContainer.getIdentifiers();
		assertNotNull(identifiers);
		assertFalse(identifiers.isEmpty());
		assertEquals(1, identifiers.size());

		
		ResourceAttribute icfNameAttr = attributeContainer.findAttribute(new QName(SchemaConstants.NS_ICF_SCHEMA, "name"));
		assertEquals("Wrong secondary indetifier.", "uid=e123,ou=people,dc=example,dc=com", icfNameAttr.getRealValue());
		
		assertEquals("Wrong shadow name. ", "uid=e123,ou=people,dc=example,dc=com", eAccount.getName().getOrig());
		
		PrismObject<ShadowType> repoShadow = repositoryService.getObject(ShadowType.class, accountOid, null, result);
		
		provisioningService.applyDefinition(repoShadow, result);
		
		ResourceAttributeContainer repoAttributeContainer = ShadowUtil.getAttributesContainer(repoShadow);
		ResourceAttribute repoIcfNameAttr = repoAttributeContainer.findAttribute(new QName(SchemaConstants.NS_ICF_SCHEMA, "name"));
		assertEquals("Wrong secondary indetifier.", "uid=e123,ou=people,dc=example,dc=com", repoIcfNameAttr.getRealValue());
		
		assertEquals("Wrong shadow name. ", "uid=e123,ou=people,dc=example,dc=com", repoShadow.asObjectable().getName().getOrig());
		
	}
	
	
	
	@Test
	public void test999Shutdown() throws Exception {
		taskManager.shutdown();
		waitFor("waiting for task manager shutdown", new Checker() {
			@Override
			public boolean check() throws Exception {
				return taskManager.getLocallyRunningTasks(new OperationResult("dummy")).isEmpty();
			}

			@Override
			public void timeout() {
				// No reaction, the test will fail right after return from this
			}
		}, 10000);
		AssertJUnit.assertEquals("Some tasks left running after shutdown", new HashSet<Task>(),
				taskManager.getLocallyRunningTasks(new OperationResult("dummy")));
	}

	// TODO: test for missing/corrupt system configuration
	// TODO: test for missing sample config (bad reference in expression
	// arguments)

	private String checkRepoShadow(PrismObject<ShadowType> repoShadow) {
		ShadowType repoShadowType = repoShadow.asObjectable();
		String uid = null;
		boolean hasOthers = false;
		List<Object> xmlAttributes = repoShadowType.getAttributes().getAny();
		for (Object element : xmlAttributes) {
			if (ConnectorFactoryIcfImpl.ICFS_UID.equals(JAXBUtil.getElementQName(element))) {
				if (uid != null) {
					AssertJUnit.fail("Multiple values for ICF UID in shadow attributes");
				} else {
					uid = ((Element) element).getTextContent();
				}
			} else if (ConnectorFactoryIcfImpl.ICFS_NAME.equals(JAXBUtil.getElementQName(element))) {
				// This is OK
			} else {
				hasOthers = true;
			}
		}

		assertFalse("Shadow " + repoShadow + " has unexpected elements", hasOthers);
		assertNotNull(uid);

		return uid;
	}
	
	private String checkUser(String userOid, Task task, OperationResult parentResult) throws Exception{
		PrismObject<UserType> user = modelService.getObject(UserType.class, userOid, null, task, parentResult);
		return checkUser(user);
	}
	
	private String checkRepoUser(String userOid, OperationResult parentResult) throws Exception{
		PrismObject<UserType> user = repositoryService.getObject(UserType.class, userOid, null, parentResult);
		return checkUser(user);
	}
	
	private String checkUser(PrismObject<UserType> user){
		assertNotNull("User must not be null", user);
		UserType userType = user.asObjectable();
		assertEquals("User must have one link ref, ", 1, userType.getLinkRef().size());
		MidPointAsserts.assertAssignments(user, 1);
		
		String accountOid = userType.getLinkRef().get(0).getOid();
		
		return accountOid;
	}
	
	private void checkAccount(String accountOid, String uid, String givenName, String sn, String cn, Task task, OperationResult parentResult) throws Exception{
		PrismObject<ShadowType> newAccount = modelService.getObject(ShadowType.class, accountOid, null, task, parentResult);
		assertNotNull("Shadow must not be null", newAccount);
		ShadowType createdShadow = newAccount.asObjectable();
		display("Created account: ", createdShadow);

		AssertJUnit.assertNotNull(createdShadow);
		AssertJUnit.assertEquals(RESOURCE_OPENDJ_OID, createdShadow.getResourceRef().getOid());
		assertAttributeNotNull(createdShadow, ConnectorFactoryIcfImpl.ICFS_UID);
		
		assertAttributes(createdShadow, uid, givenName, sn, cn);
		
	}
		
	private void assertAttributes(ShadowType shadow, String uid, String givenName, String sn, String cn){
		assertAttribute(shadow, resourceTypeOpenDjrepo, "uid", uid);
		assertAttribute(shadow, resourceTypeOpenDjrepo, "givenName", givenName);
		if (sn != null) {
			assertAttribute(shadow, resourceTypeOpenDjrepo, "sn", sn);
		}
		assertAttribute(shadow, resourceTypeOpenDjrepo, "cn", cn);
	}
	
	
	private void checkPostponedAccountWithAttributes(String accountOid, String uid, String givenName, String sn, String cn, String employeeNumber, FailedOperationTypeType failedOperation, boolean modify, Task task, OperationResult parentResult) throws Exception{
		ShadowType account = checkPostponedAccountWithAttributes(accountOid, uid, givenName, sn, cn, failedOperation, modify, task, parentResult);
		assertAttribute(account, resourceTypeOpenDjrepo, "employeeNumber", employeeNumber);
	}
	
	private ShadowType checkPostponedAccountWithAttributes(String accountOid, String uid, String givenName, String sn, String cn, FailedOperationTypeType failedOperation, boolean modify, Task task, OperationResult parentResult) throws Exception{
		ShadowType failedAccountType = checkPostponedAccountBasic(accountOid, failedOperation, modify, parentResult);
		
		// assertNull(ResourceObjectShadowUtil.getAttributesContainer(faieldAccount).getIdentifier().getRealValue());
		assertAttributes(failedAccountType, uid, givenName, sn, cn);
		return failedAccountType;
	}
	
	private ShadowType checkPostponedAccountBasic(String accountOid, FailedOperationTypeType failedOperation, boolean modify, OperationResult parentResult) throws Exception{
		PrismObject<ShadowType> faieldAccount = repositoryService.getObject(ShadowType.class, accountOid, null, parentResult);
		assertNotNull("Shadow must not be null", faieldAccount);
		ShadowType failedAccountType = faieldAccount.asObjectable();
		assertNotNull(failedAccountType);
		displayJaxb("shadow from the repository: ", failedAccountType, ShadowType.COMPLEX_TYPE);
		assertEquals("Failed operation saved with account differt from  the expected value.",
				failedOperation, failedAccountType.getFailedOperationType());
		assertNotNull("Result of failed shadow must not be null.", failedAccountType.getResult());
		assertNotNull("Shadow does not contain resource ref.", failedAccountType.getResourceRef());
		assertEquals("Wrong resource ref in shadow", resourceTypeOpenDjrepo.getOid(), failedAccountType.getResourceRef().getOid());
		if (modify){
			assertNotNull("Null object change in shadow", failedAccountType.getObjectChange());
		}
		
		return failedAccountType;
	}
	
	private void requestToExecuteChanges(String requestFilename, String objectOid,
			Class type, Task task, ModelExecuteOptions options, OperationResult parentResult)
			throws Exception {
		LOGGER.info("start unmarshaling delta: {}", requestFilename);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltas(type, requestFilename, objectOid);
		
		// WHEN
		modelService.executeChanges(deltas, options, task, parentResult);
	}
	
	private Collection<ObjectDelta<? extends ObjectType>> createDeltas(Class type, String requestFilename, String objectOid) throws FileNotFoundException, SchemaException, JAXBException{
		
		try{
		ObjectDeltaType objectChange = unmarshallJaxbFromFile(
				requestFilename, ObjectDeltaType.class);
		LOGGER.info("unmarshalled delta: {}" + objectChange);
		LOGGER.info("object type in delta {}", objectChange.getObjectType());
		objectChange.setOid(objectOid);

		ObjectDelta delta = DeltaConvertor.createObjectDelta(objectChange, prismContext);

//		ObjectDelta modifyDelta = ObjectDelta.createModifyDelta(objectOid,
//				delta.getModifications(), type, prismContext);
		Collection<ObjectDelta<? extends ObjectType>> deltas = createDeltaCollection(delta);

		return deltas;
		} catch (Exception ex){
			LOGGER.error("ERROR while unmarshalling: {}", ex);
			throw ex;
		}
		
	}


}
