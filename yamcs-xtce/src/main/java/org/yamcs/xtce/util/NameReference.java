package org.yamcs.xtce.util;

import org.yamcs.xtce.NameDescription;

/**
 * Used when referencing a directory style "NameType".
 *  All characters are legal.
 *  All name references use a Unix ‘like’ name referencing mechanism across the SpaceSystem Tree
 *   (e.g., SimpleSat/Bus/EPDS/BatteryOne/Voltage) where the '/', ‘..’ and ‘.’ are used to navigate through the 
 *   hierarchy.  The use of an unqualified name will search for an item in the current SpaceSystem first, then 
 *   if none is found, in progressively higher SpaceSystems.  A SpaceSystem is a name space (i.e., a named type 
 *   declared in MetaCommandData is also declared in TelemetryMetaData - and vice versa).
 *
 *   This is used only while reading the database, then all the references are resolved and we use 
 *   Java references to real objects
 *   
 *    The ResolvedAction.resolved will be called once the reference is resolved.
 */
public abstract class NameReference {

    public enum Type {SEQUENCE_CONTAINER, COMMAND_CONTAINER, PARAMETER, PARAMETER_TYPE, META_COMMAND, ALGORITHM, ARGUMENT_TYPE}

    @FunctionalInterface
    public interface ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         */
        public boolean resolved(NameDescription nd);
        
    }
    protected final String ref;
    protected final Type type;

    public NameReference(String ref, Type type) {
        this.ref = ref;
        this.type = type;
    }

    /**
     * Execute all the actions (if not already executed) and return true if the reference has been resolved.
     * @param nd
     * @return true if the reference has been resolved
     */
    public abstract boolean resolved(NameDescription nd);
        
    /**
     * Adds an action to the list to be executed when the reference is resolved and returns this.
     * 
     * If the reference is already resolved, execute the action immediately.
     * @param action
     * @return this 
     */
    public abstract NameReference addResolvedAction(ResolvedAction action);

    public String getReference() {
        return ref;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "name: "+ref+" type: "+type;
    }

}