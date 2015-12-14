/**
 * Copyright (c) 2015 Evolveum
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
package com.evolveum.midpoint.web.component.prism;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationType;

/**
 * @author semancik
 *
 */
public class AssociationWrapper extends PropertyWrapper {
	
	private static final Trace LOGGER = TraceManager.getTrace(AssociationWrapper.class);

	public AssociationWrapper(ContainerWrapper container, PrismContainer<ShadowAssociationType> property, boolean readonly, ValueStatus status) {
		super(container, property, readonly, status);
	}

	@Override
	public ValueWrapper createAddedValue() {
		ItemDefinition definition = getDefinition();

        ValueWrapper wrapper = new ValueWrapper(this, new PrismContainerValue<ShadowAssociationType>(null), ValueStatus.ADDED);

        return wrapper;
	}
	
}
