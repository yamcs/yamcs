package org.yamcs.derivedvalues;

import org.yamcs.ParameterValue;
import org.yamcs.xtce.NameDescription;
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
    public DerivedValue(String name, String spaceSystemName, XtceAliasSet aliasSet, Parameter... argIds) {
	super(getParameter(name, spaceSystemName));
	Parameter p = getParameter();
	
	if(aliasSet!=null) {
	    p.setAliasSet(aliasSet);
	}
	

	this.argIds=argIds;
	this.args=new ParameterValue[argIds.length];
    }

    static Parameter getParameter(String name, String spaceSystemName) {
        Parameter p = new Parameter(name);
        p.setQualifiedName(spaceSystemName+NameDescription.PATH_SEPARATOR+name);
        return p;
    }
    
    public DerivedValue(String name, String spaceSystemName, Parameter[] params) {
	this(name, spaceSystemName, null, params);
    }

    public Parameter[] getArgumentIds() {
	return argIds;
    }

    public abstract void updateValue();
    public boolean isUpdated() {
	return updated;
    }
}
