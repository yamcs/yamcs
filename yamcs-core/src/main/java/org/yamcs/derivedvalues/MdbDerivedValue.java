package org.yamcs.derivedvalues;

import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * argument is the input parameters for computing the derived value
 * @author nm
 *
 */
public abstract class MdbDerivedValue extends DerivedValue {

    public MdbDerivedValue(String name, Parameter... argOpsnames) {
	super(name, getAliasSet(name), argOpsnames);
    }

    static private XtceAliasSet getAliasSet(String opsName) {
	XtceAliasSet aliasSet=new XtceAliasSet();
	aliasSet.addAlias(MdbMappings.MDB_OPSNAME, opsName);
	return aliasSet;
    }

    public void updateTime (long d){
	setAcquisitionTime(d);
    }
}
