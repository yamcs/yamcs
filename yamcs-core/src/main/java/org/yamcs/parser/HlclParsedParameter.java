package org.yamcs.parser;

import org.yamcs.commanding.TcParameterDefinition;

/**
 * holds the value of a tc parameter as parsed from the hlcl command string
 * @author mache
 *
 */
public class HlclParsedParameter {
	public String name;
	public TcParameterDefinition.SwTypes type;
	public Object value;
	public int nameBeginLine;
	public int nameBeginColumn;
	public int valueBeginLine;
	public int valueBeginColumn;
	public String toString() {
		return name+":"+value;
	}
	
}
