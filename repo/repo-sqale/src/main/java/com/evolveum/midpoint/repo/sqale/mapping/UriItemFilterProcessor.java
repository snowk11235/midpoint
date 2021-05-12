/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.mapping;

import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.NumberPath;

import com.evolveum.midpoint.prism.query.PropertyValueFilter;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.ValueFilterValues;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.ItemFilterProcessor;

/**
 * Filter processor for URI item paths - represented by string/QName in schema and by int ID in DB.
 * These paths are generally not ordered by, which is a relief, otherwise JOIN would be needed.
 */
public class UriItemFilterProcessor extends ItemFilterProcessor<PropertyValueFilter<String>> {

    private final NumberPath<Integer> path;

    public UriItemFilterProcessor(
            SqlQueryContext<?, ?, ?> context,
            Function<EntityPath<?>, NumberPath<Integer>> rootToPath) {
        super(context);
        this.path = rootToPath.apply(context.path());
    }

    @Override
    public Predicate process(PropertyValueFilter<String> filter) throws QueryException {
        return createBinaryCondition(filter, path,
                ValueFilterValues.from(filter,
                        u -> ((SqaleRepoContext) context.sqlRepoContext()).searchCachedUriId(u)));
    }
}