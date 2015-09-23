package org.yamcs.simulator.leospacecraft;

import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationConfiguration;
import org.yamcs.simulator.SimulationData;
import org.yamcs.simulator.Simulator;

/**
 * Discrete 1Hz time-stepped simulation of a generic LEO spacecraft.
 */
public class LEOSpacecraftSimulator extends Simulator {
    
    private DataFeeder packetFeeder = new DataFeeder(true);
    private LEOSpacecraftModel model = new LEOSpacecraftModel();
    private long t = 0;
    
    public LEOSpacecraftSimulator(SimulationConfiguration simConfig) {
        super(simConfig);
    }
    
    @Override
    public void run() {
        super.run();
        try {
            SimulationData data;
            while ((data = packetFeeder.readNext()) != null) {
                model.step(t++, data);
                
                CCSDSPacket packet = model.toCCSDSPacket();
                transmitTM(packet);
                
                //executePendingCommands();
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
