package org.yamcs;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * argument is the input parameters for computing the derived value
 * @author nm
 *
 */
public abstract class DerivedValue extends ParameterValue {
	final protected NamedObjectId[] argIds;
	protected ParameterValue[] args;
	protected boolean updated=true;
	
	/**
	 * constructs a derived value with the given names and aliases
	 * @param name
	 * @param aliasSet
	 * @param argIds
	 */
	public DerivedValue(String name, XtceAliasSet aliasSet, NamedObjectId... argIds) {
		super(new Parameter(name),false);
		getParameter().setAliasSet(aliasSet);
		
		this.argIds=argIds;
		this.args=new ParameterValue[argIds.length];
	}
	
	/**
	 * Constructs a derived value using just names, without aliases.
	 * @param name
	 * @param aliasSet
	 * @param argIds
	 */
	public DerivedValue(String name, String... argIds) {
		super(new Parameter(name),false);
		
		this.argIds=new NamedObjectId[argIds.length];
		for(int i=0;i<argIds.length; i++) {
			this.argIds[i]=NamedObjectId.newBuilder().setName(argIds[i]).build();
		}
		this.args=new ParameterValue[argIds.length];
	}
	
	public NamedObjectId[] getArgumentIds() {
		return argIds;
	}
	
	public abstract void updateValue();
	public boolean isUpdated() {
		return updated;
	}
}
