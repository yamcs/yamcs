package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.ParameterStatus;

public class MultiParameterRetrieval {
    final ParameterArchive parchive;
    final MultipleParameterRequest mpvr;

    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();
    private final Logger log = LoggerFactory.getLogger(MultiParameterRetrieval.class);

    public MultiParameterRetrieval(ParameterArchive parchive, MultipleParameterRequest mpvr) {
        this.parchive = parchive;
        this.mpvr = mpvr;
    }

    public void retrieve(Consumer<ParameterIdValueList> consumer) throws RocksDBException, IOException {

        PriorityQueue<ArchiveIterator> queue = new PriorityQueue<>(new ArchiveIteratorComparator(mpvr.ascending));
        Map<ArchiveIterator, String> iterator2ParameterName = new HashMap<>();

        for (int i = 0; i < mpvr.parameterIds.length; i++) {
            ParameterRequest req = new ParameterRequest(mpvr.start, mpvr.stop, mpvr.ascending, mpvr.retrieveEngValues,
                    mpvr.retrieveRawValues.get(i), mpvr.retrieveParamStatus);

            ArchiveIterator it = new ArchiveIterator(parchive, mpvr.parameterIds[i], mpvr.parameterGroupIds[i], req);
            if (it.isValid()) {
                queue.add(it);
                iterator2ParameterName.put(it, mpvr.parameterNames[i]);
            }
        }

        SegmentMerger merger = new SegmentMerger(mpvr, consumer);

        try {
            while (!queue.isEmpty()) {
                ArchiveIterator it = queue.poll();
                merger.process(iterator2ParameterName.get(it), it.getParameterId(), it.getParameterGroupId(),
                        it.value());

                if (merger.sentEnough())
                    return;

                it.next();
                if (it.isValid()) {
                    queue.add(it);
                }
            }
            merger.flush();
        } catch (ConsumerAbortException e) {
            log.debug("Stoped early due to receiving ConsumerAbortException");
        } finally {
            queue.forEach(it -> it.close());
        }
    }

    /**
     * Sorted merging of segments which takes care that parameters from the same group end up in the same list
     *
     */
    static class SegmentMerger {
        int count = 0;
        TreeMap<Key, ParameterIdValueList> values;

        final MultipleParameterRequest mpvr;
        final Consumer<ParameterIdValueList> consumer;

        public SegmentMerger(MultipleParameterRequest mpvr, Consumer<ParameterIdValueList> consumer) {
            this.mpvr = mpvr;
            this.consumer = consumer;
            values = new TreeMap<>(new Comparator<Key>() {
                @Override
                public int compare(Key k1, Key k2) {
                    int c = mpvr.ascending ? Long.compare(k1.instant, k2.instant)
                            : Long.compare(k2.instant, k1.instant);

                    if (c == 0) {
                        c = Integer.compare(k1.pgid, k2.pgid);
                    }
                    return c;
                }
            });

        }

        void process(String pname, int pid, int pgid, ParameterValueSegment pvs) {
            long t = mpvr.ascending ? pvs.getSegmentStart() : pvs.getSegmentEnd();

            // we can flush out all the values having start smaller than this segment start (or greater than the end
            // when descending) because we know that all future segments will have their start after
            SortedMap<Key, ParameterIdValueList> toflush = values.headMap(key(t, pgid));

            if (toflush.size() > 0) {
                for (ParameterIdValueList pvlist : toflush.values()) {
                    consumer.accept(pvlist);
                    count++;
                    if (sentEnough()) {
                        return;
                    }
                }
                toflush.clear();
            }

            new SegmentIterator(pvs, mpvr.start, mpvr.stop, mpvr.ascending)
                    .forEachRemaining(tv -> process(pname, pid, pgid, tv));
        }

        public void flush() {
            for (ParameterIdValueList pvlist : values.values()) {
                consumer.accept(pvlist);
                count++;
                if (sentEnough()) {
                    return;
                }
            }
        }

        private void process(String pname, int pid, int pgid, TimedValue tv) {
            Key k = key(tv.instant, pgid);
            ParameterIdValueList vlist = values.computeIfAbsent(k, k1 -> new ParameterIdValueList(tv.instant));

            ParameterValue pv = new ParameterValue(pname);
            pv.setGenerationTime(tv.instant);

            if (tv.engValue != null) {
                pv.setEngValue(tv.engValue);
            }
            if (tv.rawValue != null) {
                pv.setRawValue(tv.rawValue);
            }
            if (tv.paramStatus != null) {
                ParameterStatus ps = tv.paramStatus;
                if (ps.hasAcquisitionStatus()) {
                    pv.setAcquisitionStatus(ps.getAcquisitionStatus());
                }
                if (ps.hasMonitoringResult()) {
                    pv.setMonitoringResult(ps.getMonitoringResult());
                }
                if (ps.getAlarmRangeCount() > 0) {
                    pv.addAlarmRanges(ps.getAlarmRangeList());
                }
                if (ps.hasExpireMillis()) {
                    pv.setExpireMillis(ps.getExpireMillis());
                }
                if (ps.hasRangeCondition()) {
                    pv.setRangeCondition(ps.getRangeCondition());
                }
            }

            vlist.add(pid, pv);
        }

        boolean sentEnough() {
            return mpvr.limit > 0 && count >= mpvr.limit;
        }

    }

    private static Key key(long instant, int pgid) {
        return new Key(instant, pgid);
    }

    static class Key {
        final long instant;
        final int pgid;

        public Key(long instant, int pgid) {
            this.instant = instant;
            this.pgid = pgid;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (instant ^ (instant >>> 32));
            result = prime * result + pgid;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;

            Key other = (Key) obj;

            return (instant == other.instant && pgid == other.pgid);
        }

    }

    static class ArchiveIteratorComparator implements Comparator<ArchiveIterator> {
        final boolean ascending;

        public ArchiveIteratorComparator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(ArchiveIterator it1, ArchiveIterator it2) {
            ParameterValueSegment pvs1 = it1.value();
            ParameterValueSegment pvs2 = it2.value();

            int c = ascending ? Long.compare(pvs1.getSegmentStart(), pvs2.getSegmentStart())
                    : Long.compare(pvs2.getSegmentEnd(), pvs1.getSegmentEnd());

            if (c != 0) {
                return c;
            }
            //
            // make sure the parameters are extracted in the order of their id
            // (rather than some random order from PriorityQueue)
            return Integer.compare(it1.getParameterId(), it2.getParameterId());
        }
    }
}
