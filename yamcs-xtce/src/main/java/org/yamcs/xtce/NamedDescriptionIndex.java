package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.xtce.xml.XtceAliasSet;

/**
 * Keeps a list of {@link NameDescription} objects with corresponding indexes to be able to retrieve them in any namespace.
 * 
 * Note that the names are case sensitive while aliases are not. 
 * 
 * @author nm
 *
 */
public class NamedDescriptionIndex<T extends NameDescription> implements Serializable, Iterable<T> {
    private static final long serialVersionUID = 4L;

    private LinkedHashMap<String, LinkedHashMap<String, T>> aliasIndex = new LinkedHashMap<String, LinkedHashMap<String, T>>();
    private LinkedHashMap<String, T> index = new LinkedHashMap<String, T>();

    public void add(T o) {
        XtceAliasSet aliases = o.getAliasSet();
        if (aliases != null) {
            for (String ns : aliases.getNamespaces()) {
                LinkedHashMap<String, T> m = aliasIndex.computeIfAbsent(ns, k -> new LinkedHashMap<String, T>());
                m.put(aliases.getAlias(ns).toUpperCase(), o);
            }
        }
        //add an "alias" for (fq_space_system_name, name) 
        LinkedHashMap<String, T> m = aliasIndex.computeIfAbsent(o.getSubsystemName(), k -> new LinkedHashMap<String, T>());
        m.put(o.getName().toUpperCase(), o);

        if (o.getQualifiedName() != null) {
            index.put(o.getQualifiedName(), o);
        } else {
            index.put(o.getName(), o); // Happens for Derived Values
        }
    }

    /**
     * returns the object based on its qualified name
     */
    public T get(String qualifiedName) {
        return index.get(qualifiedName);
    }

    /**
     * returns the object in namespace
     * 
     * @param name
     * @param nameSpace
     * @return
     */
    public T get(String nameSpace, String name) {
        Map<String, T> m = aliasIndex.get(nameSpace);
        if (m != null) {
            return m.get(name.toUpperCase());
        } else {
            return null;
        }
    }

    /**
     * returns a collection of all the objects (parameters) in the index
     * 
     * @return
     */
    public Collection<T> getObjects() {
        return index.values();
    }

    /**
     * 
     * @return number of objects in index
     */
    public int size() {
        return index.size();
    }

    @Override
    public Iterator<T> iterator() {
        return index.values().iterator();
    }
}
