package org.yamcs.xtce;

import java.io.Serializable;

import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * The type definition used by most elements that require a name with optional
 * descriptions.
 */
public class NameDescription implements Serializable {
    private static final long serialVersionUID = 200706050619L;

    /**
     * Name of the object
     */
    protected String name = null;
    
    /**
     * path separator used in the fully qualified names
     */
    public static char PATH_SEPARATOR = '/';
    
    
    /**
     * fully qualified name (i.e. space system name+"/"+name
     */
    protected String qualifiedName=null;
    /**
     * Set of aliases
     */
    protected XtceAliasSet xtceAliasSet= null;

    String shortDescription;
    String longDescription;
    

    NameDescription(String name) {
        this.name = name;
    }
    
    public void setName(String newName) {
        this.name = newName;
    }
    /**
     * Returns the non qualified name of the item
     * @return
     */
    public String getName() {
        return name;
    }

    public String getAlias(String namespace) {
        if(xtceAliasSet==null)return null;
        return xtceAliasSet.getAlias(namespace);
    }
    
    

    public void setQualifiedName(String qname) {
        this.qualifiedName=qname;
    }
    
    /**
     * Returns the fully qualified name. 
     * @return a name of shape /system/subsys1/subsys2/item
     */
    public String getQualifiedName() {
        return qualifiedName;
    }
    
    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public String getLongDescription() {
        return longDescription;
    }

    /**
     * Assign set of aliases with the object
     * 
     * @param aliasSet
     *            Set of aliases
     */
    public void setAliasSet(XtceAliasSet aliasSet) {
        this.xtceAliasSet = aliasSet;
    }

    public XtceAliasSet getAliasSet() {
        return xtceAliasSet;
    }
    
    public void addAlias(String namespace, String alias) {
        if(xtceAliasSet==null) {
            xtceAliasSet=new XtceAliasSet();
        }
        xtceAliasSet.addAlias(namespace, alias);
    }
    /**
     * OPS name, in XTCE defined as alias for namespace "MDB:OPS Name"
     * 
     * @return OPS Name alias if defined, otherwise name in the default
     *         namespace
     */
    public String getOpsName() {
        if (xtceAliasSet != null) {
            String alias = xtceAliasSet.getAlias("MDB:OPS Name");
            if (alias != null) {
                return alias;
            }
        }
        return name;
    }
    
    /**
     * 
     * @return fully qualified name of the subsystem of which this name is part of
     */
    public String getSubsystemName() {
        return getSubsystemName(qualifiedName);
    }
    
    /**
     * returns the last component of the fully qualified name
     * 
     * @param fqname
     * @return
     */
    public static String getName(String fqname) {
        int index = fqname.lastIndexOf(PATH_SEPARATOR);
        if (index < 0) return fqname;
        return fqname.substring(index + 1);
    }
    
    /**
     * returns the subsystem fully qualified name where this name is valid (i.e. the full path of the directory name if it were a filesystem)
     * 
     * @param fqname
     * @return
     */
    public static String getSubsystemName(String fqname) {
        int index = fqname.lastIndexOf(PATH_SEPARATOR);
        if (index < 0) return fqname;
        return fqname.substring(0, index);
    }
}
