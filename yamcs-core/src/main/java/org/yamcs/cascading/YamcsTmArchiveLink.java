package org.yamcs.cascading;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.StreamOptions;
import org.yamcs.client.archive.ArchiveClient.StreamOptions.StreamOption;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.TimeEncoding;

/**
 * 
 * Yamcs TM Archive link - fetches archive data
 *
 */
public class YamcsTmArchiveLink extends AbstractTmDataLink {
    YamcsLink parentLink;

    private List<String> containers;

    int retrievalDays;
    int mergeTime;
    int gapFillingInterval;

    Queue<Gap> queue = new ArrayDeque<>();
    List<Gap> prevGaps;
    CompletableFuture<Void> runningTask;
    private long start;
    private long stop;

    public YamcsTmArchiveLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) {
        config = YamcsTmLink.swapConfig(config, "tmArchiveStream", "tmStream", "tm_dump");
        super.init(instance, name, config);
        this.retrievalDays = config.getInt("retrievalDays", 120);
        // when retrieving archive indexes, merge all the gaps smaller than 300 seconds
        this.mergeTime = config.getInt("mergeTime", 300) * 1000;

        this.mergeTime = 1000;
        this.gapFillingInterval = config.getInt("gapFillingInterval", 300);

        log.debug("Archive retrieval for {} days", retrievalDays);
    }

    @Override
    protected void doStart() {
        if (!isEffectivelyDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doDisable() {
    }

    @Override
    public void doEnable() {
        if (containers != null) {
            scheduleDataRetrieval();
        }
    }

    @Override
    protected void doStop() {
        if (!isDisabled()) {
            doDisable();
        }
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }

    void scheduleDataRetrieval() {
        parentLink.getExecutor().execute(this::retrieveGaps);
    }

    void retrieveGaps() {
        if (connectionStatus() != Status.OK || isEffectivelyDisabled()) {
            return;
        }

        if (runningTask != null && !runningTask.isDone()) {
            return;
        }

        if (queue.isEmpty()) {
            if (runningTask != null) {
                log.debug("Retrieval finished, looking for new gaps");
                runningTask = null;
            }
            if (prevGaps == null) {
                identifyGaps();
            } else {
                checkRemainingGaps();
            }
        }
        if (queue.isEmpty()) {
            return;
        } else {
            retrieveGap(queue.poll());
        }
    }

    private void processPacketData(TmPacketData data) {
        long rectime = timeService.getMissionTime();
        byte[] pktData = data.getPacket().toByteArray();

        TmPacket pkt = new TmPacket(rectime, pktData);
        if (data.hasGenerationTime()) {
            pkt.setGenerationTime(TimeEncoding.fromProtobufTimestamp(data.getGenerationTime()));
        }
        pkt.setSequenceCount(data.getSequenceNumber());

        packetCount.incrementAndGet();
        processPacket(pkt);
    }

    /**
     * retrieves the TM index from upstream and compares it with the local
     * 
     * @return
     */
    void identifyGaps() {
        start = timeService.getMissionTime() - 86400_000 * retrievalDays;
        stop = timeService.getMissionTime();

        TmGapFinder gapFinder = new TmGapFinder(yamcsInstance, parentLink, eventProducer, retrievalDays,
                p -> isPacketRequired(p));

        var gaps = gapFinder.identifyGaps(start, stop);

        if (gaps.size() == 0) {
            log.debug("No gap identified.");
            log.debug("Scheduling next gap filling in {} seconds", gapFillingInterval);
            parentLink.getExecutor().schedule(this::retrieveGaps, gapFillingInterval, TimeUnit.SECONDS);
            return;
        }

        Collections.sort(gaps);

        prevGaps = gaps;
        log.info("Identified {} gaps for the retrieval", gaps.size());
        queue.addAll(gaps);
    }

    void checkRemainingGaps() {
        TmGapFinder gapFinder = new TmGapFinder(yamcsInstance, parentLink, eventProducer, retrievalDays,
                p -> isPacketRequired(p));

        var gaps = gapFinder.identifyGaps(start, stop);

        for (Gap gap : gaps) {
            if (Collections.binarySearch(prevGaps, gap) >= 0) {
                if (gap.stop < stop) {
                    log.warn("Gap {} still remains after replay", gap);
                }
            }
        }

        prevGaps = null;
        log.debug("Scheduling next gap filling in {} seconds", gapFillingInterval);
        parentLink.getExecutor().schedule(this::retrieveGaps, gapFillingInterval, TimeUnit.SECONDS);
    }

    void retrieveGap(Gap g) {
        log.debug("Retrieving gap {}", g);
        ArchiveClient arcClient = parentLink.getClient().createArchiveClient(parentLink.getUpstreamInstance());
        java.time.Instant startj = java.time.Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(g.start));

        // the retrieval is exclusive on the right, so we increase the stop by one millisecond
        java.time.Instant stopj = java.time.Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(g.stop + 1));
        StreamOption opt = StreamOptions.packets(containers.toArray(new String[0]));
        runningTask = arcClient.streamPackets(pkt -> processPacketData(pkt), startj, stopj, opt);
        runningTask.whenComplete((v, t) -> {
            if (t != null) {
                log.warn("Error in gap retrieval", t);
            }
            scheduleDataRetrieval();
        });
    }

    private boolean isPacketRequired(String name) {
        return containers.contains(name);
    }

    public void setContainers(List<String> containers) {
        this.containers = containers;
    }

    static class Gap implements Comparable<Gap> {
        long start;
        long stop;

        public Gap(long start, long stop) {
            this.start = start;
            this.stop = stop;
        }

        @Override
        public int compareTo(Gap other) {
            return Long.compare(start, other.start);
        }

        @Override
        public String toString() {
            return "[" + TimeEncoding.toString(start) + " - " + TimeEncoding.toString(stop) + "]";
        }
    }
}
