package org.yamcs.xtce;


/**
 * Parameters made on the fly.
 * 
 * DataSource should be one of SYSTEM, COMMAND or COMMAND_HISTORY (so the class name is a misnomer);
 * 
 * @author nm
 * 
 */
public class SystemParameter extends Parameter {   
    public SystemParameter(String spaceSystemName, String name, DataSource ds) {
        super(name);
        setQualifiedName(spaceSystemName+"/"+name);
        addAlias(spaceSystemName, name);
        setDataSource(ds);
    }

    public static SystemParameter getForFullyQualifiedName(String fqname, DataSource ds) {
        return new SystemParameter(NameDescription.getSubsystemName(fqname), NameDescription.getName(fqname), ds);
    }

    @Override
    public String toString() {
        return "SysParam(qname=" + getQualifiedName() + ")";
    }
}