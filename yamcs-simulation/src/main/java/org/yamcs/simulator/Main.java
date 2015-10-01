package org.yamcs.simulator;

import java.io.IOException;

import org.yamcs.YConfiguration;
import org.yamcs.simulator.ui.SimWindow;
import org.yamcs.utils.YObjectLoader;

public class Main {
    
    public static void main(String[] args) throws IOException {
        System.out.println("_______________________\n");
        System.out.println(" ╦ ╦┌─┐┌─┐");
        System.out.println(" ╚╦╝└─┐└─┐");
        System.out.println("  ╩ └─┘└─┘");
        System.out.println(" Yamcs Simulation System");
        System.out.println("_______________________");

        YConfiguration.setup(System.getProperty("user.dir"));
        SimulationConfiguration simConfig = SimulationConfiguration.loadFromFile();
        
        YObjectLoader<? extends Simulator> objectLoader = new YObjectLoader<>();
        Simulator simulator = objectLoader.loadObject(simConfig.getModelClass().getName(), simConfig);
        
        // Start UI
        if(simConfig.isUIEnabled()) {
            SimWindow simWindow = new SimWindow(simulator);
            simulator.setSimWindow(simWindow);
        }
        
        // Start simulator itself
        simulator.start();

        // start alternating los and aos
        if (simConfig.isLOSEnabled() && !simConfig.isUIEnabled()) {
            simulator.startTriggeringLos();
        }
    }
}
