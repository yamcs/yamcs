package org.yamcs.xtceproc;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.yamcs.protobuf.TmStatistics;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.TimeEncoding;
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
        s.packetRateMeter.mark(1);
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
                        .setPacketName(t.packetName)
                        .setQualifiedName(t.qualifiedName)
                        .setReceivedPackets(t.receivedPackets)
                        .setSubscribedParameterCount(t.subscribedParameterCount)
                        .setLastPacketTime(TimeEncoding.toProtobufTimestamp(t.lastPacketTime))
                        .setLastReceived(TimeEncoding.toProtobufTimestamp(t.lastReceived))
                        .setPacketRate(t.packetRateMeter.getFiveSecondsRate())
                        .build())
                .collect(Collectors.toList());
    }

    private static class TmStats {
        final String packetName;
        String qualifiedName;
        int receivedPackets;
        int subscribedParameterCount;
        long lastReceived;
        long lastPacketTime;

        DataRateMeter packetRateMeter = new DataRateMeter();

        TmStats(String packetName) {
            this.packetName = packetName;
        }
    }
}
