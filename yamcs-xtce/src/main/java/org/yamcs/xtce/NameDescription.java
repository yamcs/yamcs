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
     * fully qualified name (i.e. systemname+"/"+name
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
    
    public void addAlias(String namespace, String name) {
        if(xtceAliasSet==null) {
            xtceAliasSet=new XtceAliasSet();
        }
        xtceAliasSet.addAlias(namespace, name);
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
}
