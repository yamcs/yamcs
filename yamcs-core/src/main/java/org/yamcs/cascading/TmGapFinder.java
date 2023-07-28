package org.yamcs.cascading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import org.yamcs.archive.IndexRequest;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexRequestProcessor;
import org.yamcs.cascading.YamcsTmArchiveLink.Gap;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.StreamOptions;
import org.yamcs.events.EventProducer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

public class TmGapFinder {
    final Log log;
    final int retrievalDays;
    final YamcsLink parentLink;
    final String yamcsInstance;
    final EventProducer eventProducer;
    final Predicate<String> requiredPkt;

    TmGapFinder(String yamcsInstance, YamcsLink parentLink, EventProducer eventProducer, int retrievalDays,
            Predicate<String> requiredPkt) {
        this.yamcsInstance = yamcsInstance;
        this.parentLink = parentLink;
        this.log = new Log(TmGapFinder.class, yamcsInstance);
        this.retrievalDays = retrievalDays;
        this.eventProducer = eventProducer;
        this.requiredPkt = requiredPkt;
    }

    /**
     * retrieves the TM index from upstream and compares it with the local
     *
     * @return
     *
     * @return
     */
    List<Gap> identifyGaps(long start, long stop) {
        int mergeTime = 1000;

        java.time.Instant startj = java.time.Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(start));
        java.time.Instant stopj = java.time.Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(stop));

        ArchiveClient arcClient = parentLink.getClient().createArchiveClient(parentLink.getUpstreamInstance());
        List<ArchiveRecord> upstreamRecords = new ArrayList<>();
        try {
            arcClient.streamPacketIndex(upstreamRecords::add, startj, stopj, StreamOptions.mergeTime(mergeTime))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            eventProducer.sendWarning("Exception when receiving archive index: " + e.getMessage());
        }

        IndexRequest request = new IndexRequest(yamcsInstance);
        request.setSendAllTm(true);
        request.setStart(start);
        request.setStop(stop);
        request.setMergeTime(mergeTime);
        List<ArchiveRecord> downstreamRecords = new ArrayList<>();
        IndexRequestProcessor p = new IndexRequestProcessor(null, request, -1, null,
                new IndexRequestListener() {
                    @Override
                    public void processData(ArchiveRecord record) {
                        downstreamRecords.add(record);
                    }

                    @Override
                    public void finished(String token, boolean success) {
                    }
                });
        p.run();

        return diff(group(upstreamRecords, true), group(downstreamRecords, false));

    }

    List<Gap> diff(Map<String, List<ArchiveRecord>> upstreamRecords,
            Map<String, List<ArchiveRecord>> downstreamRecords) {
        GapCollector gapCollector = new GapCollector();
        for (Map.Entry<String, List<ArchiveRecord>> me : upstreamRecords.entrySet()) {
            String pname = me.getKey();
            if (downstreamRecords.containsKey(pname)) {
                addMissing(log, gapCollector, me.getValue(), downstreamRecords.get(pname));
            } else {
                for (ArchiveRecord ar : me.getValue()) {
                    gapCollector.addGap(ar.getFirst(), ar.getLast());
                }
            }
        }

        return gapCollector.gaps;
    }

    // For one specific packet, check records that are in upstream and not in downstream and add them to the missing
    // list. Those partially overlapping are also added in full because we don't know if the overlap part is complete
    static void addMissing(Log log, GapCollector gapCollector,
            List<ArchiveRecord> upstream, List<ArchiveRecord> downstream) {

        Iterator<ArchiveRecord> iterator = downstream.iterator();

        ArchiveRecord down = null;
        boolean keep = false;
        ArchiveRecord prevUp = null;
        boolean logLastDown = false;

        upstreamLoop: for (ArchiveRecord up : upstream) {

            while (keep || iterator.hasNext()) {
                if (!keep) {
                    down = iterator.next();
                }
                if (Timestamps.compare(up.getFirst(), down.getFirst()) <= 0 &&
                        Timestamps.compare(up.getLast(), down.getLast()) >= 0) {
                    // Nominal case: downstream times fully included into upstream (1)
                    if (down.getNum() < up.getNum()) {
                        gapCollector.addGap(up.getFirst(), up.getLast());
                    } else if (down.getNum() > up.getNum()) {
                        log.warn("Downstream record has more data than the related upstream record. DOWN: "
                                + toString(down) + ", UP: " + toString(up));
                    }
                    prevUp = up;
                    keep = false; // Get next record
                    continue upstreamLoop;
                } else if (Timestamps.compare(up.getLast(), down.getFirst()) < 0) {
                    // Upstream times completely before downstream (2)
                    // Adding entire upstream and keeping downstream record for the next iteration
                    gapCollector.addGap(up.getFirst(), up.getLast());
                    keep = true;
                    logLastDown = true;
                    continue upstreamLoop;
                } else if (Timestamps.compare(up.getFirst(), down.getLast()) > 0) {
                    // Upstream times completely after downstream (3)
                    // Warning only if downstream has parts after the previous upstream and does not include it
                    if (prevUp == null || (Timestamps.compare(prevUp.getFirst(), down.getFirst()) < 0
                            && Timestamps.compare(prevUp.getLast(), down.getLast()) < 0)) {
                        log.warn("Downstream record does not appear in the upstream archive: " + toString(down));
                    }
                    // Ignoring current downstream record and continue checking the rest
                    keep = false;
                } else if (Timestamps.compare(up.getFirst(), down.getFirst()) >= 0 &&
                        Timestamps.compare(up.getLast(), down.getLast()) <= 0) {
                    // upstream included completely in downstream (4)
                    log.warn("Downstream contains more data than upstream: " + toString(down));
                    // continue iterating over upstream records
                    prevUp = up;
                    keep = true;
                    logLastDown = false;
                    continue upstreamLoop;
                } else { // Unusual overlaps, where part of downstream record lays outside the upstream one (5.1 and
                         // 5.2)
                    // Skipping downstream record and adding upstream
                    log.warn("Downstream contains more data than upstream: " + toString(down));
                    gapCollector.addGap(up.getFirst(), up.getLast());
                    prevUp = up;
                    keep = false;
                    continue upstreamLoop;
                }
            }
            // Adding records that do not match any existing (exhausted iterator)
            gapCollector.addGap(up.getFirst(), up.getLast());
        }

        if (keep && logLastDown && down != null) {
            log.warn("Downstream contains more data than upstream: " + toString(down));
        }

        while (iterator.hasNext()) {
            down = iterator.next();
            if (prevUp == null || Timestamps.compare(down.getLast(), prevUp.getLast()) > 0) {
                log.warn("Downstream archive has more data than upstream: " + toString(down));
            }
        }
    }

    // group by packet name
    private Map<String, List<ArchiveRecord>> group(List<ArchiveRecord> records, boolean skipNotRequired) {
        Set<String> notRequired = new HashSet<>();
        Set<String> required = new HashSet<>();
        Map<String, List<ArchiveRecord>> r = new HashMap<>();
        for (ArchiveRecord ar : records) {
            String name = ar.getId().getName();
            if (skipNotRequired) {
                if (notRequired.contains(name)) {
                    continue;
                } else if (!required.contains(name)) {
                    if (requiredPkt.test(name)) {
                        required.add(name);
                    } else {
                        notRequired.add(name);
                        continue;
                    }
                }
            }

            List<ArchiveRecord> l = r.computeIfAbsent(name, x -> new ArrayList<>());
            l.add(ar);
        }
        return r;
    }

    private static String toString(ArchiveRecord ar) {
        return ar.getId().getName() + "[" + toString(ar.getFirst()) + " - " + toString(ar.getLast()) + "]";
    }

    private static String toString(Timestamp t) {
        return TimeEncoding.toString(TimeEncoding.fromProtobufTimestamp(t));
    }

    static class GapCollector {
        List<Gap> gaps = new ArrayList<>();

        void addGap(Timestamp start, Timestamp stop) {
            addGap(TimeEncoding.fromProtobufTimestamp(start), TimeEncoding.fromProtobufTimestamp(stop));
        }

        void addGap(long start, long stop) {
            Gap g = new Gap(start, stop);
            int idx = Collections.binarySearch(gaps, g);

            if (idx < 0) {
                idx = -idx - 1;
            }

            if (idx > 0) {
                Gap g1 = gaps.get(idx - 1);
                if (g1.stop >= start) {
                    g1.stop = g.stop;
                    g = g1;
                    idx -= 1;
                } else {
                    gaps.add(idx, g);
                }
            } else {
                gaps.add(0, g);
            }

            if (idx < gaps.size() - 1) {
                Gap g2 = gaps.get(idx + 1);
                if (g2.start <= g.stop) {
                    g.stop = Math.max(g2.stop, g.stop);
                    gaps.remove(idx + 1);
                }
            }
        }
    }
}
