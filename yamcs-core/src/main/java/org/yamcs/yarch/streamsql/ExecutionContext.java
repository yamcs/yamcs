package org.yamcs.yarch.streamsql;
/**
 * Keeps track of attributes associated with an execution context
 * @author nm
 *
 */
public class ExecutionContext {
	String dbname;
	public ExecutionContext(String dbname){
		this.dbname=dbname;
	}
	public String getDbName() {
		return dbname;
	}
}
