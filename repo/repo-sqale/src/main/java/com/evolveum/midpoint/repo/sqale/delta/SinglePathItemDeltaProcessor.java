/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.repo.sqale.SqaleUpdateContext;
import com.evolveum.midpoint.repo.sqlbase.RepositoryException;

public class SinglePathItemDeltaProcessor<T, P extends Path<T>> extends ItemDeltaProcessor {

    protected final P path;

    public SinglePathItemDeltaProcessor(
            SqaleUpdateContext<?, ?, ?> context, Function<EntityPath<?>, P> rootToQueryItem) {
        super(context);
        this.path = rootToQueryItem.apply(context.path());
    }

    @Override
    public void process(ItemDelta<?, ?> modification) throws RepositoryException {
        if (modification.isDelete()) {
            // Repo does not check deleted value for single-value properties.
            // This should be handled already by narrowing the modifications.
            context.set(path, null);
        } else {
            // We treat add and replace the same way for single-value properties.
            context.set(path, getValue(modification));
        }
    }

    @Nullable
    private T getValue(ItemDelta<?, ?> modification) {
        PrismValue anyValue = modification.getAnyValue();
        return anyValue != null ? transformRealValue(anyValue.getRealValue()) : null;
    }

    @Nullable
    protected T transformRealValue(Object realValue) {
        //noinspection unchecked
        return (T) realValue;
    }
}