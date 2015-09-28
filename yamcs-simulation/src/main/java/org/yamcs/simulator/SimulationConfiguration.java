package org.yamcs.simulator;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class SimulationConfiguration {
    
    private Class<? extends Simulator> modelClass;
    private boolean uiEnabled;
    private boolean losEnabled;
    private int losPeriodS;
    private int aosPeriodS;
    
    private List<ServerConnection> serverConnections;

    private SimulationConfiguration() {
    }
    
    @SuppressWarnings("unchecked")
    public static SimulationConfiguration loadFromFile() {
        SimulationConfiguration conf = new SimulationConfiguration();
        
        YConfiguration yconfig = YConfiguration.getConfiguration("simulator");
        
        try {
            Class<?> modelClass = Class.forName(yconfig.getString("model"));
            if (Simulator.class.isAssignableFrom(modelClass)) {
                conf.modelClass = (Class<? extends Simulator>) modelClass;
            } else {
                throw new ConfigurationException("Class '" + modelClass.getName() + "' is not an instance of '" + Simulator.class.getName() + "'");
            }
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException("Could not locate model class", e);
        }
        
        conf.uiEnabled = yconfig.getBoolean("ui");
        conf.losEnabled = yconfig.getBoolean("losAos");
        conf.losPeriodS = yconfig.getInt("los_period_s");
        conf.aosPeriodS = yconfig.getInt("aos_period_s");

        int i = 0;
        conf.serverConnections = new LinkedList<>();
        Map<String, Object> servers = yconfig.getMap("servers");
        for(String serverName : servers.keySet()) {
            Map<String, Object> serverConfig = yconfig.getMap("servers", serverName);
            int tmPort = YConfiguration.getInt(serverConfig, "tmPort");
            int tcPort = YConfiguration.getInt(serverConfig, "tcPort");
            int dumpPort = YConfiguration.getInt(serverConfig, "dumpPort");
            conf.serverConnections.add(new ServerConnection(i++, tmPort, tcPort, dumpPort));
        }
        return conf;
    }
    
    public Class<? extends Simulator> getModelClass() {
        return modelClass;
    }
    
    public boolean isUIEnabled() {
        return uiEnabled;
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
}
