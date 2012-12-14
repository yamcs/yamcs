package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class ShowTablesStatement extends StreamSqlStatement{

	public ShowTablesStatement() {
		
	}
	
	@Override
	public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
		YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
		final StringBuffer sb=new StringBuffer();
		synchronized(dict) {
		    for(TableDefinition td:dict.getTableDefinitions()) {
		        sb.append(td.toString()).append("\n");
		    }
		}
		return new StreamSqlResult() {
		  @Override
			public String toString() {
				return sb.toString();
			}
		};
	}

}
