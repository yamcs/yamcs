package org.yamcs;

import org.yamcs.xtce.MdbMappings;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * argument is the input parameters for computing the derived value
 * @author nm
 *
 */
public abstract class MdbDerivedValue extends DerivedValue {

	public MdbDerivedValue(String opsName, String... argOpsnames) {
		super(opsName,getAliasSet(opsName), MdbMappings.getParameterIdsForOpsnames(argOpsnames).toArray(new NamedObjectId[0]) );
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
