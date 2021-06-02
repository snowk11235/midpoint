/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.tasks.handlers.simple;

import static com.evolveum.midpoint.repo.common.tasks.handlers.composite.MockComponentActivityExecution.NS_EXT;
import static com.evolveum.midpoint.schema.util.task.WorkDefinitionWrapper.UntypedWorkDefinitionWrapper.getPcv;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.repo.common.activity.definition.AbstractWorkDefinition;
import com.evolveum.midpoint.schema.util.task.WorkDefinitionSource;
import com.evolveum.midpoint.util.DebugUtil;

public class SimpleMockWorkDefinition extends AbstractWorkDefinition {

    private static final ItemName MESSAGE_NAME = new ItemName(NS_EXT, "message");

    static final QName WORK_DEFINITION_TYPE_QNAME = new QName(NS_EXT, "SimpleMockDefinitionType");

    private final String message;

    SimpleMockWorkDefinition(WorkDefinitionSource source) {
        PrismContainerValue<?> pcv = getPcv(source);
        this.message = pcv != null ? pcv.getPropertyRealValue(MESSAGE_NAME, String.class) : null;
    }

    public String getMessage() {
        return message;
    }

    @Override
    protected void debugDumpContent(StringBuilder sb, int indent) {
        DebugUtil.debugDumpWithLabelLn(sb, "message", message, indent+1);
    }
}
