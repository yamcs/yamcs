package org.yamcs.derivedvalues;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.parameter.ParameterValue;
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
    
    // keep track here of all parameters that we generate in order to reuse them.
    // The reuse is necessary when switching clients from one processor to another (replay) processor - this second one has to have the same parameters
    static Map<String,Parameter> dvParameters = new HashMap<>(); 

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

    static synchronized Parameter getParameter(String name, String spaceSystemName) {
        String fqn = spaceSystemName+NameDescription.PATH_SEPARATOR+name;
        Parameter p = dvParameters.get(fqn);
        if(p==null) {
            p = new Parameter(name);
            p.setQualifiedName(fqn);
            dvParameters.put(fqn, p);
        }
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
