package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
    protected List<AncillaryData> ancillaryData = null;

    String shortDescription;
    String longDescription;

    public NameDescription(Builder<?> builder) {
        this.name = builder.name;
        this.longDescription = builder.longDescription;
        this.shortDescription = builder.shortDescription;
        this.ancillaryData = builder.ancillaryData;
        this.xtceAliasSet = builder.xtceAliasSet;
        this.qualifiedName = builder.qualifiedName;
    }

    NameDescription(String name) {
        this.name = name;
    }

    /*
     * creates a shallow copy
     */
    protected NameDescription(NameDescription t) {
        this.ancillaryData = t.ancillaryData;
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
            throw new IllegalArgumentException("qualified name '" + qname + "' must end with '" + name + "'");
        }
        this.qualifiedName = qname;
        // String ssName = getSubsystemName(qname);
        // addAlias(ssName, name);
    }

    /**
     * Stores the given ancillary data. If an entry already existed for the applicable name, that entry will be
     * overridden.
     */
    public void addAncillaryData(AncillaryData data) {
        if (ancillaryData == null) {
            ancillaryData = new ArrayList<>();
        }
        ancillaryData.add(data);
    }

    public void setAncillaryData(List<AncillaryData> ancillaryData) {
        this.ancillaryData = ancillaryData;
    }

    public List<AncillaryData> getAncillaryData() {
        return ancillaryData;
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
     * Concatenates the root with the subsystems and returns a qualified name
     *
     * @param root
     */
    public static String qualifiedName(String root, String... subsystems) {
        if (root.charAt(0) != PATH_SEPARATOR) {
            throw new IllegalArgumentException("root has to start with " + PATH_SEPARATOR);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(root);
        for (String s : subsystems) {
            if (s.charAt(0) != PATH_SEPARATOR) {
                sb.append(PATH_SEPARATOR);
            }
            sb.append(s);
        }
        return sb.toString();
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

    static public abstract class Builder<T extends Builder<T>> {
        private String name;
        private XtceAliasSet xtceAliasSet = XtceAliasSet.NO_ALIAS;
        private List<AncillaryData> ancillaryData = null;
        private String shortDescription;
        private String longDescription;
        private String qualifiedName;

        public Builder() {
        }

        public Builder(NameDescription nd) {
            this.name = nd.name;
            this.xtceAliasSet = nd.xtceAliasSet;
            this.ancillaryData = nd.ancillaryData;
            this.shortDescription = nd.shortDescription;
            this.longDescription = nd.longDescription;
            this.qualifiedName = nd.qualifiedName;
        }

        public T setName(String name) {
            this.name = name;
            return self();
        }

        public T setQualifiedName(String fqn) {
            this.qualifiedName = fqn;
            return self();
        }

        public T setLongDescription(String longDescription) {
            this.longDescription = longDescription;
            return self();
        }

        public T setShortDescription(String shortDescription) {
            this.shortDescription = shortDescription;
            return self();
        }

        public T setAliasSet(XtceAliasSet aliasSet) {
            this.xtceAliasSet = aliasSet;
            return self();
        }

        public T addAlias(String namespace, String alias) {
            if (xtceAliasSet == XtceAliasSet.NO_ALIAS) {
                xtceAliasSet = new XtceAliasSet();
            }
            xtceAliasSet.addAlias(namespace, alias);
            return self();
        }

        public void setAncillaryData(List<AncillaryData> ancillaryData) {
            this.ancillaryData = ancillaryData;
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
