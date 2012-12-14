package org.yamcs.xtceproc;

import java.util.concurrent.ConcurrentHashMap;


public class ProcessingStatistics {
    long lastUpdated;//local java time of the last update
    public ConcurrentHashMap<String, TmStats> stats=new ConcurrentHashMap<String, TmStats>();
    
    public void newPacket(String name, int subscribedParameterCount, long acquisitionTime, long generationTime) {
        TmStats s=stats.get(name);
        if(s==null) { 
            s=new TmStats(name);
            stats.put(name, s);
        }
        s.receivedPackets++;
        s.subscribedParameterCount=subscribedParameterCount;
        s.lastReceived=acquisitionTime;
        s.lastPacketTime=generationTime;
        lastUpdated=System.currentTimeMillis();
    }

    public void reset() {
        stats.clear();
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public static class TmStats { 
        public final String packetName;
        public int receivedPackets;
        public int subscribedParameterCount;
        public long lastReceived;
        public long lastPacketTime;
        
        TmStats(String packetName){
            this.packetName=packetName;
        }
    }
}
