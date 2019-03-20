package org.yamcs.xtceproc;

import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.xtce.SequenceContainer;

public class ProcessingStatistics {

    long lastUpdated; // local java time of the last update
    public ConcurrentHashMap<String, TmStats> stats = new ConcurrentHashMap<>();

    public void newPacket(SequenceContainer seq, int subscribedParameterCount, long acquisitionTime,
            long generationTime) {
        TmStats s = stats.computeIfAbsent(seq.getName(), k -> new TmStats(k));
        s.qualifiedName = seq.getQualifiedName();
        s.receivedPackets++;
        s.subscribedParameterCount = subscribedParameterCount;
        s.lastReceived = acquisitionTime;
        s.lastPacketTime = generationTime;
        lastUpdated = System.currentTimeMillis();
    }

    public void reset() {
        stats.clear();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public static class TmStats {
        public final String packetName;
        public String qualifiedName;
        public int receivedPackets;
        public int subscribedParameterCount;
        public long lastReceived;
        public long lastPacketTime;

        TmStats(String packetName) {
            this.packetName = packetName;
        }
    }
}
