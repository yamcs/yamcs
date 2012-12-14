package org.yamcs;

import java.io.Serializable;

import org.yamcs.xtce.Parameter;

/**
 * Definition of a processed parameter - this is a parameter processed by an external entity
 * We have them grouped in groups for indexing and storage (all the parameters from one group are in one partition)
 *  
 * @author mache
 *
 */
public class ProcessedParameterDefinition extends Parameter implements Serializable {
	private static final long serialVersionUID = 200611160634L;
	String group; //umi is a number on 6 bytes

	public ProcessedParameterDefinition(String name, String group) {
		super(name);
		this.group=group;
	}
	
	public String getGroup() {
	    return group;
	}

	@Override
    public String toString(){
		return "ProcessedParameterDef[group: "+group+" name: "+getName()+" aliases: "+getAliasSet()+"]";
	}
}
