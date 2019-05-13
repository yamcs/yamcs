package org.yamcs.simulation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.yamcs.spi.Plugin;

public class SimulationPlugin implements Plugin {

    private String version;

    public SimulationPlugin() throws IOException {
        try (InputStream in = SimulationPlugin.class.getResourceAsStream("/META-INF/org.yamcs.simulation.properties")) {
            Properties props = new Properties();
            props.load(in);
            version = props.getProperty("version");
        }
    }

    @Override
    public String getName() {
        return "yamcs-simulation";
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
