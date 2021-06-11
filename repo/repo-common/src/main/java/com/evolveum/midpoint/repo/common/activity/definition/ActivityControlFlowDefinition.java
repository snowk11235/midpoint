/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.activity.definition;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityControlFlowSpecificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityDefinitionType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskErrorHandlingStrategyType;

import org.jetbrains.annotations.NotNull;

public class ActivityControlFlowDefinition implements DebugDumpable {

    @NotNull private final ActivityControlFlowSpecificationType bean;

    private ActivityControlFlowDefinition(ActivityDefinitionType activityDefinitionBean) {
        this.bean = activityDefinitionBean != null && activityDefinitionBean.getControlFlow() != null ?
                activityDefinitionBean.getControlFlow() : new ActivityControlFlowSpecificationType(PrismContext.get());
    }

    @NotNull
    public static ActivityControlFlowDefinition create(ActivityDefinitionType activityDefinitionBean) {
        return new ActivityControlFlowDefinition(activityDefinitionBean);
    }

    @Override
    public String toString() {
        return bean.asPrismContainerValue().size() + " item(s)";
    }

    @Override
    public String debugDump(int indent) {
        return bean.debugDump(indent);
    }

    public TaskErrorHandlingStrategyType getErrorHandlingStrategy() {
        return bean.getErrorHandling();
    }
}