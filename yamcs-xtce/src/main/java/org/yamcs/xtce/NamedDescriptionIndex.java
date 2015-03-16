package org.yamcs.xtce;


import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.xtce.xml.XtceAliasSet;


/**
 * Keeps a list of parameters with corresponding indexes to be able to retrieve them in any namespace.
 * 
 * Currently the name is case sensitive while aliases are not. Is this the correct behavior???
 * @author nm
 *
 */
public class NamedDescriptionIndex<T extends NameDescription> implements Serializable, Iterable<T>{
    private static final long serialVersionUID = 3L;
    
    private LinkedHashMap<String, LinkedHashMap<String,T>> aliasIndex =new LinkedHashMap<String, LinkedHashMap<String,T>>();
    private LinkedHashMap<String,T> index =new LinkedHashMap<String,T>();
    
    
    public void add(T o) {
        XtceAliasSet aliases=o.getAliasSet();
        if(aliases!=null) {
            for(String ns:aliases.getNamespaces()) {
                LinkedHashMap<String, T> m=aliasIndex.get(ns);
                if(m==null) {
                    m=new LinkedHashMap<String, T>();
                    aliasIndex.put(ns, m);
                }
                m.put(aliases.getAlias(ns).toUpperCase(), o);
            }
        }
        
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
     * @param name
     * @param nameSpace
     * @return
     */
    public T get(String nameSpace, String name) {
        Map<String, T>m=aliasIndex.get(nameSpace);
        if (m!=null) {
            return m.get(name.toUpperCase());
        } else {
            return null;
        }
    }
    /**
     *  returns a collection of all the objects (parameters) in the index
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
