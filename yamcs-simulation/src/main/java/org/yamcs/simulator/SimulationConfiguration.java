package org.yamcs.simulator;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.YConfiguration;

public class SimulationConfiguration {

    private boolean losEnabled;
    private int losPeriodS;
    private int aosPeriodS;

    private List<ServerConnection> serverConnections = new ArrayList<>();

    private String testDataDir;

    private SimulationConfiguration() {
    }

    public static SimulationConfiguration loadFromFile() {
        SimulationConfiguration conf = new SimulationConfiguration();

        YConfiguration yconf = YConfiguration.getConfiguration("simulator");

        conf.losEnabled = yconf.getBoolean("losAos");
        conf.losPeriodS = yconf.getInt("los_period_s");
        conf.aosPeriodS = yconf.getInt("aos_period_s");
        conf.testDataDir = yconf.getString("test_data");

        int tmPort = yconf.getInt("tmPort");
        int tcPort = yconf.getInt("tcPort");
        int dumpPort = yconf.getInt("dumpPort");
        conf.serverConnections.add(new ServerConnection(0, tmPort, tcPort, dumpPort));

        return conf;
    }

    public boolean isLOSEnabled() {
        return losEnabled;
    }

    public int getLOSPeriod() {
        return losPeriodS;
    }

    public int getAOSPeriod() {
        return aosPeriodS;
    }

    public List<ServerConnection> getServerConnections() {
        return serverConnections;
    }

    public String getTestDataDir() {
        return testDataDir;
    }

    public int getPerfTestNumPackets() {
        return 0;
    }

    public int getPerfTestPacketSize() {
        // TODO Auto-generated method stub
        return 0;
    }
}
