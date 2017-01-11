package org.yamcs.xtce;

public enum DataSource {
/**
 * XTCE: 
 * A telemetered Parameter is one that will have values in telemetry. 
 * A derived Parameter is one that is calculated, usually by an Algorithm. 
 * A constant Parameter is  one that is used as a constant in the system (e.g. a vehicle id). 
 * A local Parameter is one that is used purely on the ground (e.g. a ground command counter).
 * 
 * 
 * Usage in Yamcs: 
 *  TELEMETERD - used for data acquired from outside
 *  DERIVED - are those set by algorithm manager
 *  LOCAL - are software parameters that can be set by user
 *  SYSTEM - parameters giving internal yamcs state -created on the fly (do not have to be defined in the database)
 *  CONSTANT - not used (yet)
 *  COMMAND and COMMAND_HISTORY - are special parameters created on the fly and instantiated in the context of command verifiers
 *  
 *  all the other are telemetered
 * 
 */
    TELEMETERED, DERIVED, CONSTANT, LOCAL, 
    SYSTEM, COMMAND, COMMAND_HISTORY;
}
