package org.yamcs.simulation;

import org.yamcs.api.Plugin;

public class SimulationPlugin implements Plugin {

    private String version;

    public SimulationPlugin() {
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
    }

    @Override
    public String getName() {
        return "Simulation";
    }

    @Override
    public String getDescription() {
        return "All-in-one configuration example using an embedded simulator";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getVendor() {
        return "Space Applications Services";
    }
}
