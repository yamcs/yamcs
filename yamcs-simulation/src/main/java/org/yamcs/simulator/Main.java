package org.yamcs.simulator;

import java.io.IOException;

import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class Main {
    
    public static void main(String[] args) throws IOException {
        System.out.println("_______________________\n");
        System.out.println(" ╦ ╦┌─┐┌─┐");
        System.out.println(" ╚╦╝├─┤└─┐");
        System.out.println("  ╩ ┴ ┴└─┘");
        System.out.println(" Yet Another Simulator");
        System.out.println("_______________________");

        YConfiguration.setup(System.getProperty("user.dir"));
        SimulationConfiguration simConfig = SimulationConfiguration.loadFromFile();
        
        YObjectLoader<? extends Simulator> objectLoader = new YObjectLoader<>();
        Simulator simulator = objectLoader.loadObject(simConfig.getModelClass().getName(), simConfig);
        simulator.start();

        // start alternating los and aos
        if (simConfig.isLOSEnabled()) {
            simulator.startTriggeringLos();
        }
    }
}
