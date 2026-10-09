package com.resumint.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.CHAR)
public final class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement,int index,UUID parameter,JdbcType jdbcType) throws SQLException { statement.setString(index,parameter.toString()); }
    @Override public UUID getNullableResult(ResultSet result,String column) throws SQLException { return value(result.getString(column)); }
    @Override public UUID getNullableResult(ResultSet result,int index) throws SQLException { return value(result.getString(index)); }
    @Override public UUID getNullableResult(CallableStatement statement,int index) throws SQLException { return value(statement.getString(index)); }
    private UUID value(String value){return value==null?null:UUID.fromString(value);}
}
