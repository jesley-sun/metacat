/*
 *
 * Copyright 2018 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */
package com.netflix.metacat.connector.snowflake;

import com.google.inject.Inject;
import com.netflix.metacat.common.QualifiedName;
import com.netflix.metacat.common.dto.Pageable;
import com.netflix.metacat.common.dto.Sort;
import com.netflix.metacat.common.server.connectors.ConnectorRequestContext;
import com.netflix.metacat.common.server.connectors.model.AuditInfo;
import com.netflix.metacat.common.server.connectors.model.TableInfo;
import com.netflix.metacat.connector.jdbc.JdbcExceptionMapper;
import com.netflix.metacat.connector.jdbc.JdbcTypeConverter;
import com.netflix.metacat.connector.jdbc.services.JdbcConnectorTableService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Snowflake table service implementation.
 *
 * @author amajumdar
 * @since 1.2.0
 */
@Slf4j
public class SnowflakeConnectorTableService extends JdbcConnectorTableService {
    private static final String COL_CREATED = "CREATED";
    private static final String COL_LAST_ALTERED = "LAST_ALTERED";
    private static final String SQL_GET_AUDIT_INFO
        = "select created, last_altered from information_schema.tables"
        + " where table_catalog=? and table_schema=? and table_name=?";

    /**
     * Constructor.
     *
     * @param dataSource      the datasource to use to connect to the database
     * @param typeConverter   The type converter to use from the SQL type to Metacat canonical type
     * @param exceptionMapper The exception mapper to use
     */
    @Inject
    public SnowflakeConnectorTableService(
        final DataSource dataSource,
        final JdbcTypeConverter typeConverter,
        final JdbcExceptionMapper exceptionMapper
    ) {
        super(dataSource, typeConverter, exceptionMapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(@Nonnull final ConnectorRequestContext context, @Nonnull final QualifiedName name) {
        super.delete(context, getSnowflakeName(name));
    }

    /**
     * Returns the snowflake represented name which is always uppercase.
     * @param name qualified name
     * @return qualified name
     */
    private QualifiedName getSnowflakeName(final QualifiedName name) {
        return name.cloneWithUpperCase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TableInfo get(@Nonnull final ConnectorRequestContext context, @Nonnull final QualifiedName name) {
        return super.get(context, getSnowflakeName(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TableInfo> list(@Nonnull final ConnectorRequestContext context,
                                @Nonnull final QualifiedName name,
                                @Nullable final QualifiedName prefix,
                                @Nullable final Sort sort,
                                @Nullable final Pageable pageable) {
        return super.list(context, getSnowflakeName(name), prefix, sort, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<QualifiedName> listNames(@Nonnull final ConnectorRequestContext context,
                                         @Nonnull final QualifiedName name,
                                         @Nullable final QualifiedName prefix,
                                         @Nullable final Sort sort,
                                         @Nullable final Pageable pageable) {
        return super.listNames(context, name.cloneWithUpperCase(), prefix, sort, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rename(@Nonnull final ConnectorRequestContext context,
                       @Nonnull final QualifiedName oldName,
                       @Nonnull final QualifiedName newName) {
        super.rename(context, getSnowflakeName(oldName), getSnowflakeName(newName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@Nonnull final ConnectorRequestContext context, @Nonnull final QualifiedName name) {
        return super.exists(context, getSnowflakeName(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setTableInfoDetails(final Connection connection, final TableInfo tableInfo) {
        final QualifiedName tableName = getSnowflakeName(tableInfo.getName());
        try (
            final PreparedStatement statement = connection.prepareStatement(SQL_GET_AUDIT_INFO)
        ) {
            statement.setString(1, tableName.getDatabaseName());
            statement.setString(2, tableName.getDatabaseName());
            statement.setString(3, tableName.getTableName());
            try (final ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final AuditInfo auditInfo =
                        AuditInfo.builder().createdDate(resultSet.getDate(COL_CREATED))
                            .lastModifiedDate(resultSet.getDate(COL_LAST_ALTERED)).build();
                    tableInfo.setAudit(auditInfo);
                }
            }
        } catch (final Exception ignored) {
            log.info("Ignoring. Error getting the audit info for table {}", tableName);
        }
    }
}
