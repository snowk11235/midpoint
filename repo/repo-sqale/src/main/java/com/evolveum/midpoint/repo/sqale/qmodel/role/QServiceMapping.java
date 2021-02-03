/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.role;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType.F_DISPLAY_ORDER;

import com.evolveum.midpoint.repo.sqale.qmodel.object.ObjectSqlTransformer;
import com.evolveum.midpoint.repo.sqlbase.SqlRepoContext;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerContext;
import com.evolveum.midpoint.repo.sqlbase.mapping.item.StringItemFilterProcessor;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType;

/**
 * Mapping between {@link QService} and {@link ServiceType}.
 */
public class QServiceMapping
        extends QAbstractRoleMapping<ServiceType, QService, MService> {

    public static final String DEFAULT_ALIAS_NAME = "svc";

    public static final QServiceMapping INSTANCE = new QServiceMapping();

    private QServiceMapping() {
        super(QService.TABLE_NAME, DEFAULT_ALIAS_NAME,
                ServiceType.class, QService.class);

        addItemMapping(F_DISPLAY_ORDER,
                StringItemFilterProcessor.mapper(path(q -> q.displayOrder)));
    }

    @Override
    protected QService newAliasInstance(String alias) {
        return new QService(alias);
    }

    @Override
    public ObjectSqlTransformer<ServiceType, QService, MService>
    createTransformer(SqlTransformerContext transformerContext, SqlRepoContext sqlRepoContext) {
        // TODO create specific transformer
        return new ObjectSqlTransformer<>(transformerContext, this);
    }

    @Override
    public MService newRowObject() {
        return new MService();
    }
}