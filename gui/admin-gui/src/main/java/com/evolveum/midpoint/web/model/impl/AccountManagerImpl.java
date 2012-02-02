/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.web.model.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;

import com.evolveum.midpoint.common.Utils;
import com.evolveum.midpoint.common.diff.CalculateXmlDiff;
import com.evolveum.midpoint.common.diff.DiffException;
import com.evolveum.midpoint.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.delta.ObjectDelta;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.holder.XPathSegment;
import com.evolveum.midpoint.schema.processor.DiffUtil;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.DebugUtil;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.bean.ResourceCapability;
import com.evolveum.midpoint.web.controller.util.ControllerUtil;
import com.evolveum.midpoint.web.model.AccountManager;
import com.evolveum.midpoint.web.model.WebModelException;
import com.evolveum.midpoint.web.model.dto.AccountShadowDto;
import com.evolveum.midpoint.web.model.dto.ObjectReferenceDto;
import com.evolveum.midpoint.web.model.dto.PropertyChange;
import com.evolveum.midpoint.web.model.dto.ResourceDto;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;

/**
 * 
 * @author lazyman
 * 
 */
public class AccountManagerImpl extends ObjectManagerImpl<AccountShadowType, AccountShadowDto> implements
		AccountManager {

	private static final long serialVersionUID = 3793939681394774533L;
	private static final Trace LOGGER = TraceManager.getTrace(AccountManagerImpl.class);
	
	@Autowired(required = true)
    private SchemaRegistry schemaRegistry;

	@Override
	public Collection<AccountShadowDto> list(PagingType paging) {
		return list(paging, ObjectTypes.ACCOUNT);
	}

	@Override
	protected Class<? extends ObjectType> getSupportedObjectClass() {
		return AccountShadowType.class;
	}

	@Override
	protected AccountShadowDto createObject(AccountShadowType objectType) {
		return new AccountShadowDto(objectType);
	}

	@Override
	public Set<PropertyChange> submit(AccountShadowDto changedObject, Task task, OperationResult parentResult) {
		Validate.notNull(changedObject, "Changed account must not be null.");

		// Don't resolve resource. We don't need it and we would "unresolve" it anyway.
		AccountShadowDto oldObject = get(changedObject.getOid(), null);

		if (changedObject.getActivation() != null) {
			changedObject.getXmlObject().setActivation(changedObject.getActivation());
		}

		OperationResult result = parentResult.createSubresult(AccountManager.SUBMIT);
		try {
			PropertyModificationType passwordChange = null;
			// detect if password was changed
			if (changedObject.getCredentials() != null) {
				// if password was changed, create modification change
				PasswordType password = changedObject.getXmlObject().getCredentials().getPassword();
                // if password was changed, create modification change
                List<XPathSegment> segments = new ArrayList<XPathSegment>();
                segments.add(new XPathSegment(SchemaConstants.I_CREDENTIALS));
                segments.add(new XPathSegment(SchemaConstants.I_PASSWORD));
                XPathHolder xpath = new XPathHolder(segments);
                passwordChange = ObjectTypeUtil.createPropertyModificationType(
                        PropertyModificationTypeType.replace, xpath, SchemaConstants.R_PROTECTED_STRING,
                        password.getProtectedString());
                // now when modification change of password was made, clear
                // credentials from changed user and also from old account to be not used by diff..
                changedObject.getXmlObject().setCredentials(null);
                oldObject.getXmlObject().setCredentials(null);
			}

			AccountShadowType accountOld = oldObject.getXmlObject();
			AccountShadowType accountNew = changedObject.getXmlObject();
			
			unresolveResource(accountOld);
			unresolveResource(accountNew);
			
			LOGGER.trace("Old account:\n{}",JAXBUtil.marshalWrap(accountOld));
        	LOGGER.trace("New account:\n{}",JAXBUtil.marshalWrap(accountNew));
        	ObjectDelta<AccountShadowType> accountDelta = DiffUtil.diff(accountOld, accountNew,
        			AccountShadowType.class, schemaRegistry.getObjectSchema());
        	
        	LOGGER.trace("Account delta:\n{}",accountDelta.dump());
        	ObjectModificationType changes = null;
        	if (accountDelta != null && !accountDelta.isEmpty()) {
        		changes = accountDelta.toObjectModificationType();
        	}
			
//			// detect other changes
//			ObjectModificationType changes = CalculateXmlDiff.calculateChanges(oldObject.getXmlObject(),
//					);
//			// if there is a password change, add it to other changes and
//			// process it.

			if (changes != null || passwordChange != null) {
				if (changes == null) {
					changes = new ObjectModificationType();
					changes.setOid(accountOld.getOid());
				}
				if (passwordChange != null) {
					if (changes.getOid() == null) {
						changes.setOid(changedObject.getOid());
					}
					changes.getPropertyModification().add(passwordChange);
				}
				if (changes.getOid() != null) {
					LOGGER.debug("Modifying account submited in gui. {}",
							ObjectTypeUtil.toShortString(changedObject.getXmlObject()));
					getModel().modifyObject(AccountShadowType.class, changes, task, result);
				}
			} else {
				LOGGER.debug("No account changes detected.");
			}
			result.recordSuccess();
		} catch (SchemaException ex) {
			LoggingUtils.logException(LOGGER, "Couldn't update account {}, schema error", ex,
					changedObject.getName());
			result.recordFatalError("Couldn't update account '" + changedObject.getName()
					+ "', schema error.", ex);

		} catch (ObjectNotFoundException ex) {
			LoggingUtils.logException(LOGGER, "Couldn't update account {}, because it doesn't exists", ex,
					changedObject.getName());
			result.recordFatalError("Couldn't update account '" + changedObject.getName()
					+ "', because it doesn't exists.", ex);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't update account {}, reason: {}", ex, new Object[] {
					changedObject.getName(), ex.getMessage() });
			result.recordFatalError("Couldn't update account '" + changedObject.getName() + "', reason: "
					+ ex.getMessage() + ".", ex);
		}

		result.computeStatus("Couldn't submit user '" + changedObject.getName() + "'.");
//		ControllerUtil.printResults(LOGGER, result);
		return new HashSet<PropertyChange>();
	}


	private void unresolveResource(AccountShadowType account) {
    	// Convert resource to resourceRef, so it will not create phantom changes in comparison
    	if (account.getResource() != null) {
    		account.setResourceRef(ObjectTypeUtil.createObjectRef(account.getResource()));
    		account.setResource(null);
    	}
	}

	@Override
	public UserType listOwner(String oid) {
		Validate.notNull(oid, "Account oid must not be null.");

		UserType user = null;
		OperationResult result = new OperationResult(AccountManager.SUBMIT);
		try {
			user = getModel().listAccountShadowOwner(oid, result);
			result.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list owner of account oid {}", ex, oid);
			result.recordFatalError("Couldn't list owner of account oid '" + oid + "'.", ex);
		}

		return user;
	}

	@Override
	public ResourceCapability getResourceCapability(AccountShadowDto account) {
		Validate.notNull(account, "Account shadow dto must not be null.");

		ResourceCapability capability = new ResourceCapability();
		try {
			ResourceDto resource = account.getResource();
			if (resource == null) {
				ObjectReferenceDto ref = account.getResourceRef();
				ResourceType resourceType = get(ResourceType.class, ref.getOid(),
						new PropertyReferenceListType());
				resource = new ResourceDto(resourceType);
			}

			capability
					.setAccount(account, ResourceTypeUtil.getEffectiveCapabilities(resource.getXmlObject()));
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't get resource capabilities for account {}", ex,
					account.getName());
		}

		return capability;
	}
}
