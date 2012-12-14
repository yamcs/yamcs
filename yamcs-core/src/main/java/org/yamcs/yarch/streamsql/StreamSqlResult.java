package org.yamcs.yarch.streamsql;

import java.util.HashMap;
import java.util.Map.Entry;

public class StreamSqlResult {
	HashMap<String,Object> params;
	
	/**
	 * Constructors for results with 0 parameters
	 */
	public StreamSqlResult() {
		
	}
	
	/**
	 * Constructor for results with one parameter
	 * @param p1name
	 * @param p1value
	 */
	public StreamSqlResult(String p1name, Object p1value) {
		params=new HashMap<String,Object>();
		params.put(p1name,p1value);
	}
	
	public Object getParam(String p) {
		return params.get(p);
	}

	public String toString() {
		if(params==null) return null;
		StringBuffer sb=new StringBuffer();
		for(Entry<String, Object> entry:params.entrySet()) {
			sb.append(entry.getKey()+"="+entry.getValue());
		}
		return sb.toString();
	}
}
