package org.yamcs.xtce;

public enum DataSource {
/**
 * XTCE: 
 * A telemetered Parameter is one that will have values in telemetry. 
 * A derived Parameter is one that is calculated, usually be an Algorithm. 
 * A constant Parameter is  one that is used as a constant in the system (e.g. a vehicle id). 
 * A local Parameter is one that is used purely on the ground (e.g. a ground command counter).
 * 
 * Usage in Yamcs: 
 *  derived are those set by algorithm manager
 *  local are software parameters and system parameters
 *  constant - not used (yet)
 *  
 *  all the other are telemetered
 * 
 */
    TELEMETERED, DERIVED, CONSTANT, LOCAL;
}
