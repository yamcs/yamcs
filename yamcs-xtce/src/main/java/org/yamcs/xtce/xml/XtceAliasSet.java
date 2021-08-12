package org.yamcs.xtce.xml;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class XtceAliasSet implements Serializable {

    private static final long serialVersionUID = 6841708656383592099L;

    private HashMap<String, String> aliases = new HashMap<>();

    @SuppressWarnings("serial")
    public static XtceAliasSet NO_ALIAS = new XtceAliasSet() {
        @Override
        public void addAlias(String namespace, String alias) {
            throw new UnsupportedOperationException();
        };
    };

    /**
     * Add alias name, only one name per namespace is possible
     * 
     * @param namespace
     *            Namespace the alias adheres to
     * @param alias
     *            name in the given namespace
     */
    public void addAlias(String namespace, String alias) {
        if (aliases.containsKey(namespace)) {
            String existingAlias = aliases.get(namespace);
            if (!existingAlias.equals(alias)) {
                throw new IllegalArgumentException(String.format(
                        "Cannot set alias to '%s'. A different alias '%s' is already"
                                + " defined under the namespace '%s'",
                        alias, existingAlias, namespace));
            }
        }
        aliases.put(namespace, alias);
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
