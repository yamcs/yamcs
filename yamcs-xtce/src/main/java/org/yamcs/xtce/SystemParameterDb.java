package org.yamcs.xtce;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Like XtceDB but keeps track of system parameters.
 * The reason for having it separated it is because this one is more dynamic. New parameters can pop in at any time.
 * 
 * Could be reunited with XtceDb if we ever have a mechanism of modifying the XtceDb on the fly.
 * 
 * @author nm
 *
 */
public class SystemParameterDb implements Serializable {
    private static final long  serialVersionUID   = 1L;
    /**
     * Namespace for hosting system parameters
     */
    public static String YAMCS_SPACESYSTEM_NAME = "/yamcs";
    
    final SpaceSystem yamcsSs;
    transient static Logger log = LoggerFactory.getLogger(SystemParameterDb.class);
    
    /**
     * Namespaces for hosting parameters valid in the command verification context
     */
    public static String YAMCS_CMD_SPACESYSTEM_NAME = "/yamcs/cmd";
    public static String YAMCS_CMDHIST_SPACESYSTEM_NAME = "/yamcs/cmdHist";
    
    public SystemParameterDb() {
        yamcsSs = new SpaceSystem(YAMCS_SPACESYSTEM_NAME.substring(1));
    }
    
    
    //map from the fully qualified names to the objects
    private Map<String, SystemParameter> parameters = new LinkedHashMap<String, SystemParameter>();
    

    private static DataSource getSystemParameterDataSource(String fqname) {
        if(fqname.startsWith(YAMCS_CMD_SPACESYSTEM_NAME)) return DataSource.COMMAND;
        else if(fqname.startsWith(YAMCS_CMDHIST_SPACESYSTEM_NAME)) return DataSource.COMMAND_HISTORY;
        else return DataSource.SYSTEM;
    }
    
    
    public synchronized  SystemParameter getSystemParameter(String fqname) {
        SystemParameter p = parameters.get(fqname);
        if(p==null) {
            p = createSystemParameter(fqname);
        }
        return p;
    }
    
    public synchronized Parameter getSystemParameter(NamedObjectId id) {
        if(!isSystemParameter(id)) throw new IllegalArgumentException("Not a system parameter "+id);
        String fqname;
        if (id.hasNamespace()) {
            fqname = id.getNamespace()+NameDescription.PATH_SEPARATOR+id.getName();            
        } else {
            fqname = id.getName();
        }   
        
        return getSystemParameter(fqname);
    }

    
    /**
     * Create if not already existing the system parameter and the enclosing space systems
     * 
     * @param rootSs
     * @param fqname
     * @return
     */
    private SystemParameter createSystemParameter(String fqname) {
        DataSource ds = getSystemParameterDataSource(fqname);
        
        String[] a = Pattern.compile(String.valueOf(NameDescription.PATH_SEPARATOR), Pattern.LITERAL).split(fqname);

        SpaceSystem ss = yamcsSs;
        for(int i=2; i<a.length-1; i++) {
            SpaceSystem sss = ss.getSubsystem(a[i]);
            if(sss==null) {
                sss=new SpaceSystem(a[i]);
                ss.addSpaceSystem(sss);
            }
            ss=sss;
        }
        SystemParameter sp = (SystemParameter)ss.getParameter(a[a.length-1]);
        
        if(sp==null) {
            sp = SystemParameter.getForFullyQualifiedName(fqname, ds);
            log.debug("adding new system parameter for "+fqname+" in system "+ss);
            ss.addParameter(sp);
            parameters = new LinkedHashMap<String, SystemParameter>();
            buildParameterMap(yamcsSs);
        }
        
        return sp;
    }
    
    
    
    private void buildParameterMap(SpaceSystem ss) {
        for(Parameter p:ss.getParameters()) {
            parameters.put(p.getQualifiedName(), (SystemParameter)p);
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildParameterMap(ss1);
        }
    }

    public SpaceSystem getYamcsSpaceSystem() {
        return yamcsSs;
    }

    public static boolean isSystemParameter(NamedObjectId id) {
        boolean result;
        if(!id.hasNamespace()) {
            result = id.getName().startsWith(SystemParameterDb.YAMCS_SPACESYSTEM_NAME);
        } else {
            result = id.getNamespace().startsWith(SystemParameterDb.YAMCS_SPACESYSTEM_NAME);
        }
        return result;
    }

    public synchronized boolean isDefined(String fqn) {
        return parameters.containsKey(fqn);
    }
}
