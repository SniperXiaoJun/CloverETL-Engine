/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.connection.jdbc.specific.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.regex.Pattern;

import org.jetel.connection.jdbc.DBConnection;
import org.jetel.connection.jdbc.SQLCloverStatement.QueryType;
import org.jetel.connection.jdbc.specific.conn.MSSQLConnection;
import org.jetel.exception.JetelException;
import org.jetel.metadata.DataFieldMetadata;

/**
 * Informix specific behaviour.
 * 
 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created Jun 3, 2008
 */
public class InformixSpecific extends AbstractJdbcSpecific {

	/** the SQL comments pattern specific for Informix */
	private static final Pattern COMMENTS_PATTERN =
			Pattern.compile("--[^\r\n]*|/\\*.*?\\*/|\\{(?!(\\?= )?call)[^}]*\\}", Pattern.DOTALL);

	private static final InformixSpecific INSTANCE = new InformixSpecific();
    final static Pattern PREPARED_STMT_PATTERN = Pattern.compile("\\?");

    protected InformixSpecific() {
		super(AutoGeneratedKeysType.SINGLE);
	}

	public static InformixSpecific getInstance() {
		return INSTANCE;
	}

	@Override
	public Pattern getCommentsPattern() {
		return COMMENTS_PATTERN;
	}

	/* (non-Javadoc)
	 * @see org.jetel.connection.jdbc.specific.impl.AbstractJdbcSpecific#createSQLConnection(org.jetel.connection.jdbc.DBConnection, org.jetel.connection.jdbc.specific.JdbcSpecific.OperationType)
	 */
	@Override
	public Connection createSQLConnection(DBConnection connection, OperationType operationType) throws JetelException {
		//that is intentional usage of MSSQLConnection!!!
		return new MSSQLConnection(connection, operationType);
	}

	@Override
	public String quoteIdentifier(String identifier) {
		return "\"" + identifier + "\"";
	}

    /**
	 * Informix validation is different because Informix does not support selects in from
	 * clause
	 */
	public String getValidateQuery(String query, QueryType queryType)
			throws SQLException {

		if (queryType.equals(QueryType.SELECT)) {

			if (PREPARED_STMT_PATTERN.matcher(query).find()) {
	        	StringBuilder query2 = new StringBuilder(query);
	        	int whereIndex = query.toString().toLowerCase().indexOf("where");
	        	int groupIndex = query.toString().toLowerCase().indexOf("group");
	        	int orderIndex = query.toString().toLowerCase().indexOf("order");
	        	if (whereIndex > -1){
	        		if (groupIndex > -1 || orderIndex > -1){
	        			query2.delete(whereIndex, groupIndex);
	        		}else{
	        			query2.setLength(whereIndex);
	        		}
	        	}
	        	query = query2.toString();
			}
			
			return "select * from table(multiset(" + query + ")) wrapper_table where 1=0";
		} else {
			return super.getValidateQuery(query, queryType);
		}

	}

	public String sqlType2str(int sqlType) {
		switch(sqlType) {
		case Types.TIMESTAMP :
			return "DATETIME YEAR TO SECOND";
		case Types.TIME :
			return "DATETIME HOUR TO SECOND";
		case Types.NUMERIC :
			return "FLOAT";
		case Types.BINARY :
		case Types.VARBINARY :
		case Types.LONGVARBINARY :
			return "BYTE";
		case Types.BIGINT :
			return "INT8";
		}
		return super.sqlType2str(sqlType);
	}
	
	@Override
	public int jetelType2sql(DataFieldMetadata field) {
		switch (field.getType()) {
		case DataFieldMetadata.BYTE_FIELD:
        case DataFieldMetadata.BYTE_FIELD_COMPRESSED:
        	return Types.LONGVARBINARY;
        case DataFieldMetadata.NUMERIC_FIELD:
        	return Types.DOUBLE;
		default: 
        	return super.jetelType2sql(field);
		}
	}

	@Override
	public String jetelType2sqlDDL(DataFieldMetadata field) {
		int type = jetelType2sql(field);
		switch (type) {
		case Types.BINARY:
		case Types.VARBINARY:
			return sqlType2str(type);

		case Types.DOUBLE:
			return "FLOAT";
		}
		return super.jetelType2sqlDDL(field);
	}

	@Override
	public ResultSet getTables(Connection connection, String dbName) throws SQLException {
		return connection.getMetaData().getTables(null, dbName, "%", new String[] {"TABLE", "VIEW"});
	}

	@Override
	public boolean isSchemaRequired() {
		return true;
	}
	
	
}
