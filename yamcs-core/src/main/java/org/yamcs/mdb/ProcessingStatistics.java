package org.yamcs.mdb;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.yamcs.protobuf.TmStatistics;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.TimeEncoding;

public class ProcessingStatistics {

    long lastUpdated; // local java time of the last update
    public ConcurrentHashMap<String, TmStats> stats = new ConcurrentHashMap<>();

    public void newPacket(String pname, int subscribedParameterCount, long acquisitionTime,
            long generationTime, int sizeInBits) {
        TmStats s = stats.computeIfAbsent(pname, p -> new TmStats());
        s.pname = pname;
        s.receivedPackets++;
        s.subscribedParameterCount = subscribedParameterCount;
        s.lastReceived = acquisitionTime;
        s.lastPacketTime = generationTime;
        s.packetRateMeter.mark(1);
        s.dataRateMeter.mark(sizeInBits);
        lastUpdated = System.currentTimeMillis();
    }

    public void reset() {
        stats.clear();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public List<TmStatistics> snapshot() {
        return stats.values().stream()
                .map(t -> TmStatistics.newBuilder()
                        .setPacketName(t.pname)
                        .setQualifiedName(t.pname)
                        .setReceivedPackets(t.receivedPackets)
                        .setSubscribedParameterCount(t.subscribedParameterCount)
                        .setLastPacketTime(TimeEncoding.toProtobufTimestamp(t.lastPacketTime))
                        .setLastReceived(TimeEncoding.toProtobufTimestamp(t.lastReceived))
                        .setPacketRate(Math.round(t.packetRateMeter.getFiveSecondsRate()))
                        .setDataRate(Math.round(t.dataRateMeter.getFiveSecondsRate()))
                        .build())
                .collect(Collectors.toList());
    }

    private static class TmStats {
        String pname;
        int receivedPackets;
        int subscribedParameterCount;
        long lastReceived;
        long lastPacketTime;
        DataRateMeter packetRateMeter = new DataRateMeter();
        DataRateMeter dataRateMeter = new DataRateMeter();
    }
}
