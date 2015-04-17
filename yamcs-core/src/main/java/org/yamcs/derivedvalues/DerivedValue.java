package org.yamcs.derivedvalues;

import org.yamcs.ParameterValue;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * argument is the input parameters for computing the derived value
 * @author nm
 *
 */
public abstract class DerivedValue extends ParameterValue {
    final protected Parameter[] argIds;
    protected ParameterValue[] args;
    protected boolean updated=true;

    /**
     * constructs a derived value with the given names and aliases
     * @param name
     * @param aliasSet
     * @param argIds
     */
    public DerivedValue(String name, XtceAliasSet aliasSet, Parameter... argIds) {
	super(new Parameter(name));
	if(aliasSet!=null) {
	    getParameter().setAliasSet(aliasSet);
	}

	this.argIds=argIds;
	this.args=new ParameterValue[argIds.length];
    }

    public DerivedValue(String name, Parameter[] params) {
	this(name, null, params);
    }

    public Parameter[] getArgumentIds() {
	return argIds;
    }

    public abstract void updateValue();
    public boolean isUpdated() {
	return updated;
    }
}
