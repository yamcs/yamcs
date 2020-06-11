package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.xtce.xml.XtceAliasSet;

/**
 * The type definition used by most elements that require a name with optional descriptions.
 */
public class NameDescription implements Serializable {
    private static final long serialVersionUID = 200706050619L;

    /**
     * path separator used in the fully qualified names
     */
    public static char PATH_SEPARATOR = '/';

    /**
     * Name of the object
     */
    protected String name = null;

    /**
     * fully qualified name (i.e. space system name+"/"+name
     */
    protected String qualifiedName = null;

    /**
     * Set of aliases
     */
    protected XtceAliasSet xtceAliasSet = XtceAliasSet.NO_ALIAS;

    /**
     * Escape hatch for storing any type of information
     */
    private Map<String, AncillaryData> ancillaryDataSet = null;

    String shortDescription;
    String longDescription;

    public NameDescription(Builder<?> builder) {
        this.name = builder.name;
        this.longDescription = builder.longDescription;
        this.shortDescription = builder.shortDescription;
        this.ancillaryDataSet = builder.ancillaryDataSet;
        this.xtceAliasSet = builder.xtceAliasSet;
    }

    NameDescription(String name) {
        this.name = name;
    }

    /*
     * creates a shallow copy
     */
    protected NameDescription(NameDescription t) {
        this.ancillaryDataSet = t.ancillaryDataSet;
        this.longDescription = t.longDescription;
        this.shortDescription = t.shortDescription;
        this.name = t.name;
        this.qualifiedName = t.qualifiedName;
        this.xtceAliasSet = t.xtceAliasSet;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    /**
     * Returns the non qualified name of the item
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    public String getAlias(String namespace) {
        if (xtceAliasSet == null) {
            return null;
        }
        return xtceAliasSet.getAlias(namespace);
    }

    public void setQualifiedName(String qname) {
        if (!qname.endsWith(name)) {
            throw new IllegalArgumentException("qualified name '" + qname + "' +must end with '" + name + "'");
        }
        this.qualifiedName = qname;
        // String ssName = getSubsystemName(qname);
        // addAlias(ssName, name);
    }

    public AncillaryData getAncillaryData(String name) {
        if (ancillaryDataSet == null) {
            return null;
        }
        return ancillaryDataSet.get(name);
    }

    /**
     * Stores the given ancillary data. If an entry already existed for the applicable name, that entry will be
     * overriden.
     */
    public void addAncillaryData(AncillaryData data) {
        if (ancillaryDataSet == null) {
            ancillaryDataSet = new LinkedHashMap<>();
        }
        ancillaryDataSet.put(data.getName(), data);
    }

    public Collection<AncillaryData> getAncillaryDataSet() {
        return ancillaryDataSet.values();
    }

    /**
     * Returns the fully qualified name.
     * 
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
     * Assign set of aliases with the object. The previous aliases if any are replaced by the new ones.
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

    /**
     * Adds all aliases to the existing aliases. The new aliases may overwrite already existing aliases - in this case
     * the old ones will be replaced with the new ones.
     *
     * @param newAliases
     */
    public void addAliases(XtceAliasSet newAliases) {
        if (xtceAliasSet == XtceAliasSet.NO_ALIAS) {
            xtceAliasSet = new XtceAliasSet();
        }
        for (Map.Entry<String, String> e : newAliases.getAliases().entrySet()) {
            xtceAliasSet.addAlias(e.getKey(), e.getValue());
        }
    }

    public void addAlias(String namespace, String alias) {
        if (xtceAliasSet == XtceAliasSet.NO_ALIAS) {
            xtceAliasSet = new XtceAliasSet();
        }
        xtceAliasSet.addAlias(namespace, alias);
    }

    /**
     * OPS name, in XTCE defined as alias for namespace "MDB:OPS Name"
     *
     * @return OPS Name alias if defined, otherwise name in the default namespace
     */
    public String getOpsName() {
        String alias = xtceAliasSet.getAlias("MDB:OPS Name");
        if (alias != null) {
            return alias;
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
        if (index < 0) {
            return fqname;
        }
        return fqname.substring(index + 1);
    }

    /**
     * returns the subsystem fully qualified name where this name is valid (i.e. the full path of the directory name if
     * it were a filesystem)
     *
     * @param fqname
     * @return the fully qualified name
     */
    public static String getSubsystemName(String fqname) {
        int index = fqname.lastIndexOf(PATH_SEPARATOR);

        if (index == 0) {
            return String.valueOf(PATH_SEPARATOR);
        }

        if (index < 0) {
            throw new RuntimeException("Illegal qualified name '" + fqname + "'");
        }
        return fqname.substring(0, index);
    }

    static public abstract class Builder<T extends Builder<T>>  {
        private String name;
        private XtceAliasSet xtceAliasSet = XtceAliasSet.NO_ALIAS;
        private Map<String, AncillaryData> ancillaryDataSet = null;
        private String shortDescription;
        private String longDescription;

        public Builder() {
        }
        
        public Builder(NameDescription nd) {
            this.name = nd.name;
            this.xtceAliasSet = nd.xtceAliasSet;
            this.ancillaryDataSet = nd.ancillaryDataSet;
            this.shortDescription = nd.shortDescription;
            this.longDescription = nd.longDescription;
        }
        

        public T setName(String name) {
            this.name = name;
            return self();
        }

        public void setLongDescription(String longDescription) {
            this.longDescription = longDescription;
        }

        public void setShortDescription(String shortDescription) {
            this.shortDescription = shortDescription;
        }

        public void setAliasSet(XtceAliasSet aliasSet) {
            this.xtceAliasSet = aliasSet;
        }

        public void AncillaryData(Map<String, AncillaryData> ancillaryDataSet) {
            this.ancillaryDataSet = ancillaryDataSet;
        }
        
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
        
        public String getName() {
            return name;
        }
    }
}
