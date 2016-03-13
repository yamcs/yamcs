package org.yamcs.xtce;


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
 *    The ResolvAction.resolv will be called once the reference is resolved.
 */
public class NameReference {
    public enum Type {SEQUENCE_CONTAINTER, PARAMETER, PARAMETER_TYPE, META_COMMAND};
    public interface ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         */
        public boolean resolved(NameDescription nd);
    }
    
    public String ref;
    Type type;
    ResolvedAction action;
    
    public NameReference(String ref, Type type, ResolvedAction action) {
        this.ref=ref;
        this.type=type;
        this.action=action;
    }
    
    public boolean resolved(NameDescription nd) {
        return action.resolved(nd);
    }
    
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
