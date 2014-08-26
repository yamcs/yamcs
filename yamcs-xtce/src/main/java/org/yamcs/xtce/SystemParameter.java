package org.yamcs.xtce;


/**
 * Fake parameter made on the fly
 * 
 * @author nm
 * 
 */
public class SystemParameter extends Parameter {

   
    public SystemParameter(String spaceSystemName, String name) {
        super(name);
        setQualifiedName(spaceSystemName+"/"+name);
    }
    
    public static SystemParameter getForFullyQualifiedName(String fqname) {
       return new SystemParameter(NameDescription.getSubsystemName(fqname), NameDescription.getName(fqname));
    }
    
    @Override
    public String toString() {
        return "SysVar(qname=" + getQualifiedName() + ")";
    }
}