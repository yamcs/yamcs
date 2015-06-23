package org.yamcs.xtce.xml;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper class for work with aliases.
 * 
 * @author mu
 * 
 */
public class XtceAliasSet implements Serializable {

    private static final long       serialVersionUID = 6841708656383592099L;

    /**
     * Map of all aliases the object has
     */
    private HashMap<String, String> aliases          = null;

    /**
     * Constructor
     */
    public XtceAliasSet() {
        aliases = new HashMap<String, String>();
    }

    /**
     * Add alias name, only one name per namespace is possible
     * 
     * @param nameSpace
     *            Namespace the alias adhers to
     * @param alias
     *            name in the given namespace
     */
    public void addAlias(String nameSpace, String alias) {
        aliases.put(nameSpace, alias);
    }

    /**
     * Returns the name of the object in the given namespace
     * 
     * @param nameSpace
     *            Namespace the name should be from
     * @return Name of the object in the given namespace, can be null
     */
    public String getAlias(String nameSpace) {
        return aliases.get(nameSpace);
    }

    public Set<String> getNamespaces() {
        return aliases.keySet();
    }

    /**
     * Returns a readonly map, mapping namespace to alias
     */
    public Map<String, String> getAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    public int size() {
        return aliases.size();
    }

    @Override
    public String toString() {
        return aliases.toString();
    }
}
