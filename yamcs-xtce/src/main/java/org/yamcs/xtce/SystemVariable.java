package org.yamcs.xtce;


/**
 * Fake parameter made on the fly
 * 
 * @author nm
 * 
 */
public class SystemVariable extends Parameter {

   
    public SystemVariable(String spaceSystemName, String name) {
        super(name);
        setQualifiedName(spaceSystemName+"/"+name);
    }
    
    public static SystemVariable getForFullyQualifiedName(String fqname) {
       return new SystemVariable(NameDescription.getSubsystemName(fqname), NameDescription.getName(fqname));
    }
    
    @Override
    public String toString() {
        return "SysVar(qname=" + getQualifiedName() + ")";
    }
}