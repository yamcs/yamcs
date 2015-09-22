package org.yamcs.simulator;

/**
 * Tags a time-stepped simulation model
 */
public interface SimulationModel {
    
    /**
     * Update model by stepping to step <tt>t</tt>.
     */
    void step(long t, SimulationData data);
    
    /**
     * Dump the current state as a packet
     */
    CCSDSPacket toCCSDSPacket();
}
