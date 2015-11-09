package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.XtceDb;

public class MdbMappings {

    public final static String MDB_SID="MDB:SID";
    public final static String MDB_OPSNAME="MDB:OPS Name";
    public final static String MDB_PATHNAME="MDB:Pathname";
    public final static String MDB_HKID="MDB:HKID";
    public final static String MDB_UMI="MDB:UMI";
    public final static String CCU_Version="MDB:CCU Internal Version";
    public final static String Config_SID="Operational Configuration Enditem SID";
    public final static String CCU_Path="MDB:CCU Version Path";
    
    
    static HashMap<XtceDb, MdbMappings> instances=new HashMap<XtceDb, MdbMappings>();

    public static List<NamedObjectId> getParameterIdsForOpsnames(String[] opsnames) {
        List<NamedObjectId> paraList=new ArrayList<NamedObjectId>(opsnames.length);
        for(int i=0; i<opsnames.length; i++) {
            paraList.add(i, NamedObjectId.newBuilder().setNamespace(MdbMappings.MDB_OPSNAME).setName(opsnames[i]).build());
        }
        return paraList;
    }
}
