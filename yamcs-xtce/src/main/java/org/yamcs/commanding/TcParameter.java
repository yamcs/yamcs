package org.yamcs.commanding;


/**
 * Represents an instantiated tc parameter. It's just a value plus a reference to the 
 *  parameter definition
 *  TODO: replace with an XTCE equivalent
 * @author mache
 *
 */
public class TcParameter {
	Object value;
	TcParameterDefinition def;
}
