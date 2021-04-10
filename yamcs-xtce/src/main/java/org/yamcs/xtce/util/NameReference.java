package org.yamcs.xtce.util;

import java.util.concurrent.CompletableFuture;

import org.yamcs.xtce.NameDescription;

/**
 * Used when referencing a directory style "NameType".
 * <p>
 * All characters are legal.
 * <p>
 * All name references use a Unix ‘like’ name referencing mechanism across the SpaceSystem Tree
 * (e.g., SimpleSat/Bus/EPDS/BatteryOne/Voltage) where the '/', ‘..’ and ‘.’ are used to navigate through the
 * hierarchy. The use of an unqualified name will search for an item in the current SpaceSystem first, then
 * if none is found, in progressively higher SpaceSystems. A SpaceSystem is a name space (i.e., a named type
 * declared in MetaCommandData is also declared in TelemetryMetaData - and vice versa).
 * <p>
 * This is used only while reading the database, then all the references are resolved and we use
 * Java references to real objects
 * <p>
 * The ResolvedAction.resolved will be called once the reference is resolved.
 */
public interface NameReference {

    public enum Type {
        SEQUENCE_CONTAINER, COMMAND_CONTAINER, PARAMETER, PARAMETER_TYPE, META_COMMAND, ALGORITHM, ARGUMENT_TYPE, ARGUMENT
    }

    @FunctionalInterface
    public interface ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         */
        public boolean resolved(NameDescription nd);

    }

    /**
     * Execute all the actions (if not already executed) and return true if the reference has been resolved.
     * 
     * @param nd
     * @return true if the reference has been resolved
     */
    public boolean tryResolve(NameDescription nd);

    /**
     * Adds an action to the list to be executed when the reference is resolved and returns this.
     * 
     * If the reference is already resolved, execute the action immediately.
     * 
     * @param action
     * @return this
     */
    public NameReference addResolvedAction(ResolvedAction action);

    public String getReference();

    public Type getType();

    public abstract boolean isResolved();

    /**
     * returns a future that is called when the reference is resolved
     * 
     * @return
     */
    public abstract CompletableFuture<NameDescription> getResolvedFuture();

}
