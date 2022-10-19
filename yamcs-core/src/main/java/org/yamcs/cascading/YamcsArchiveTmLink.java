package org.yamcs.cascading;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import org.yamcs.YConfiguration;
import org.yamcs.TmPacket;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.StreamOptions;
import org.yamcs.client.archive.ArchiveClient.StreamOptions.StreamOption;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.TimeEncoding;

/**
 * 
 * Yamcs Archive TM link - fetches archive data
 *
 */
public class YamcsArchiveTmLink extends AbstractTmDataLink {
    YamcsLink parentLink;

    List<String> containers;

    int retrievalDays;
    int mergeTime;
    int gapFillingInterval;

    Queue<Gap> queue = new ArrayDeque<>();
    List<Gap> prevGaps;
    List<Gap> permanentGaps = new ArrayList<>();

    CompletableFuture<Void> runningTask;

    public YamcsArchiveTmLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        config = YamcsTmLink.swapConfig(config, "tmArchiveStream", "tmStream", "tm_dump");
        super.init(instance, name, config);
        this.containers = config.getList("containers");
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
        scheduleDataRetrieval();
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
        parentLink.getExecutor().execute(() -> retrieveGaps());
    }

    void retrieveGaps() {
        if (isEffectivelyDisabled()) {
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
            identifyGaps();
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

        long start = timeService.getMissionTime() - 86400_000 * retrievalDays;
        long stop = timeService.getMissionTime();

        TmGapFinder gapFinder = new TmGapFinder(yamcsInstance, parentLink, eventProducer, retrievalDays,
                p -> isPacketRequired(p));

        var gaps = gapFinder.identifyGaps(start, stop);

        if (gaps.size() == 0) {
            log.debug("No gap identified.");
            prevGaps = null;
            return;
        }

        Collections.sort(gaps);

        Iterator<Gap> it = gaps.iterator();
        while (it.hasNext()) {
            Gap g = it.next();
            if (Collections.binarySearch(permanentGaps, g) >= 0) {
                log.debug("Not retrieving gap {} because it is in the permanent list");
                it.remove();
            }
        }

        if (prevGaps != null) {
            it = gaps.iterator();
            boolean needsSorting = false;
            while (it.hasNext()) {
                Gap g = it.next();
                if (Collections.binarySearch(prevGaps, g) >= 0) {
                    log.warn("Gap {} still remains after replay, adding it to the permament list", g);
                    permanentGaps.add(g);
                    needsSorting = true;
                    it.remove();
                }

            }
            if (needsSorting) {
                Collections.sort(permanentGaps);
            }
        }
        prevGaps = gaps;
        log.info("Identified {} gaps for the retrieval", gaps.size());
        queue.addAll(gaps);
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
