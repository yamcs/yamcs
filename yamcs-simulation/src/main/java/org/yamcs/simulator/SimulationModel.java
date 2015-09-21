package org.yamcs.simulator;

/**
 * Tags a time-stepped simulation model
 */
public interface SimulationModel {
    
    /**
     * Update model by stepping to step <tt>t</tt>.
     */
    void step(int t, SimulationData data);
}
