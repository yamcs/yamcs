package org.yamcs.xtce;

import java.io.Serializable;

public interface NonStandardData<T extends NonStandardData<T>> extends Serializable {

    /**
     * Sets the qualified name of the space system this non-standard data belongs to. 
     * This is where qualified names and aliases should be registered.
     */
    public void setSpaceSystemQualifiedName(String ssQualifiedName);

    /**
     * Merges non-standard data of one SpaceSystem with that of one of its direct sub-SpaceSystems
     */
    public T mergeWithChild(T childData);    
}
