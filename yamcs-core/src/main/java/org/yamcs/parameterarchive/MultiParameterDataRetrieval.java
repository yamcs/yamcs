package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameterarchive.ParameterArchiveV2.Partition;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.TimeEncoding;
import static org.yamcs.parameterarchive.SortedTimeSegment.getSegmentStart;

public class MultiParameterDataRetrieval {
    final ParameterArchiveV2 parchive;
    final MultipleParameterValueRequest mpvr;

    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();
    private final Logger log = LoggerFactory.getLogger(MultiParameterDataRetrieval.class);
    private int count;

    public MultiParameterDataRetrieval(ParameterArchiveV2 parchive, MultipleParameterValueRequest mpvr) {
        this.parchive = parchive;
        this.mpvr = mpvr;
    }

    public void retrieve(Consumer<ParameterIdValueList> consumer) throws RocksDBException, DecodingException, IOException {
        count = 0;
        try {
            List<Partition> parts = parchive.getPartitions(getSegmentStart(mpvr.start), getSegmentStart(mpvr.stop), mpvr.ascending);
            for (Partition p : parts) {
                retrieveFromPartition(p, consumer);
            }
        } catch (ConsumerAbortException e) {
            log.debug("Stoped early due to receiving ConsumerAbortException");
        }
    }

    private void retrieveFromPartition(Partition p, Consumer<ParameterIdValueList> consumer)
            throws RocksDBException, DecodingException, IOException {
        RocksIterator[] its = new RocksIterator[mpvr.parameterIds.length];
        Map<PartitionIterator, String> partition2ParameterName = new HashMap<>();
        PriorityQueue<PartitionIterator> queue = new PriorityQueue<>(new PartitionIteratorComparator(mpvr.ascending));
        SegmentMerger merger = null;
      
        for (int i = 0; i < mpvr.parameterIds.length; i++) {
            boolean retrieveEng = mpvr.retrieveEngValues || mpvr.retrieveRawValues.get(i);
            its[i] = parchive.getIterator(p);

            PartitionIterator pi = new PartitionIterator(its[i], mpvr.parameterIds[i], mpvr.parameterGroupIds[i],
                    mpvr.start, mpvr.stop, mpvr.ascending, retrieveEng, mpvr.retrieveRawValues.get(i),
                    mpvr.retrieveParamStatus);
            if (pi.isValid()) {
                queue.add(pi);
                partition2ParameterName.put(pi, mpvr.parameterNames[i]);
            } else {
                its[i].close();
                its[i] = null;
            }
        }

        try {
            while (!queue.isEmpty()) {
                if ((mpvr.limit > 0) && (count >= mpvr.limit)) {
                    break;
                }

                PartitionIterator pit = queue.poll();
                SegmentKey key = pit.key();
                if (merger == null) {
                    merger = new SegmentMerger(key, mpvr);
                } else {
                    if (key.segmentStart != merger.key.segmentStart) {
                        sendAllData(merger, consumer);
                        merger = new SegmentMerger(key, mpvr);
                    }
                }

                SortedTimeSegment timeSegment = parchive.getTimeSegment(p, key.segmentStart, pit.getParameterGroupId());
                if (timeSegment == null) {
                    String msg = "Cannot find a time segment for parameterGroupId=" + pit.getParameterGroupId()
                            + " segmentStart = " + key.segmentStart
                            + " despite having a value segment for parameterId: " + pit.getParameterId();
                    log.error(msg);
                    throw new IOException(msg);
                }
                ValueSegment engValueSegment = mpvr.retrieveEngValues ? pit.engValue() : null;
                ParameterStatusSegment paramStatuSegment = mpvr.retrieveParamStatus ? pit.parameterStatus() : null;

                ValueSegment rawValueSegment = null;
                if (pit.retrieveRawValue) {
                    rawValueSegment = pit.rawValue();
                    if (rawValueSegment == null) {
                        rawValueSegment = pit.engValue();
                    }
                }
                // do some sanity checks
                long numRecords = timeSegment.size();
                if (engValueSegment != null && engValueSegment.size() != numRecords) {
                    throw new DecodingException("EngValueSegment has a different number of records than timeSegment: "
                            + engValueSegment.size() + " vs " + timeSegment.size() + " for segment: ["
                            + TimeEncoding.toString(timeSegment.getSegmentStart()) + " - "
                            + TimeEncoding.toString(timeSegment.getSegmentEnd()) + "]" + " offending key: "
                            + pit.key());
                }

                if (rawValueSegment != null && rawValueSegment.size() != numRecords) {
                    throw new DecodingException("RawValueSegment has a different number of records than timeSegment: "
                            + rawValueSegment.size() + " vs " + timeSegment.size() + " for segment: ["
                            + TimeEncoding.toString(timeSegment.getSegmentStart()) + " - "
                            + TimeEncoding.toString(timeSegment.getSegmentEnd()) + "]" + " offending key: "
                            + pit.key());
                }

                if (paramStatuSegment != null && paramStatuSegment.size() != numRecords) {
                    throw new DecodingException(
                            "ParmaeterStatusSegment has a different number of records than timeSegment: "
                                    + paramStatuSegment.size() + " vs " + timeSegment.size() + " for segment: ["
                                    + TimeEncoding.toString(timeSegment.getSegmentStart()) + " - "
                                    + TimeEncoding.toString(timeSegment.getSegmentEnd()) + "]" + " offending key: "
                                    + pit.key());
                }

                merger.currentParameterGroupId = pit.getParameterGroupId();
                merger.currentParameterId = pit.getParameterId();
                merger.currentParameterName = partition2ParameterName.get(pit);
                new SegmentIterator(timeSegment, (ValueSegment) engValueSegment, (ValueSegment) rawValueSegment,
                        paramStatuSegment, mpvr.start, mpvr.stop, mpvr.ascending).forEachRemaining(merger);
                pit.next();
                if (pit.isValid()) {
                    queue.add(pit);
                }
            }
            if (merger != null) {
                sendAllData(merger, consumer);
            }

        } finally {
            for (int i = 0; i < mpvr.parameterIds.length; i++) {
                if (its[i] != null) {
                    its[i].close();
                }
            }
        }
    }

    private void sendAllData(SegmentMerger merger, Consumer<ParameterIdValueList> consumer) {
        Collection<ParameterIdValueList> c = merger.values.values();
        if (mpvr.limit < 0) {
            merger.values.values().forEach(consumer);
        } else {
            if (count < mpvr.limit) {
                for (ParameterIdValueList pivl : c) {
                    consumer.accept(pivl);
                    count++;
                    if (count >= mpvr.limit) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Sorted merging of parameter values from segments with the same start
     *  taking care that parameters from the same group end up in the same list  
     *
     */
    static class SegmentMerger implements Consumer<TimedValue> {
        final SegmentKey key;
        TreeMap<Long, ParameterIdValueList> values;
        int currentParameterId;
        int currentParameterGroupId;
        String currentParameterName;

        final MultipleParameterValueRequest mpvr;

        public SegmentMerger(SegmentKey key, MultipleParameterValueRequest mpvr) {
            this.key = key;
            this.mpvr = mpvr;
            values = new TreeMap<>(new Comparator<Long>() {
                @Override
                public int compare(Long o1, Long o2) {
                    if (mpvr.ascending) {
                        return Long.compareUnsigned(o1,  o2);
                    } else {
                        return Long.compareUnsigned(o2, o1);
                    }
                }
            });
        }

        @Override
        public void accept(TimedValue tv) {
            long k = k(currentParameterGroupId, tv.instant);
            ParameterIdValueList vlist = values.computeIfAbsent(k, k1 -> new ParameterIdValueList(tv.instant, currentParameterGroupId));
            
            ParameterValue pv = new ParameterValue(currentParameterName);
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
                if(ps.hasExpireMillis()) {
                    pv.setExpireMillis(ps.getExpireMillis());
                }
                if(ps.hasRangeCondition()) {
                    pv.setRangeCondition(ps.getRangeCondition());
                }
            }

            vlist.add(currentParameterId, pv);
        }

        private long k(int parameterGroupId, long instant) {
          return (instant<<32 ) | ((long) parameterGroupId) ;
        }

    }

    static class PartitionIteratorComparator implements Comparator<PartitionIterator> {
        final boolean ascending;

        public PartitionIteratorComparator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(PartitionIterator pit1, PartitionIterator pit2) {
            int c;
            if (ascending) {
                c = Long.compare(pit1.key().segmentStart, pit2.key().segmentStart);
            } else {
                c = Long.compare(pit2.key().segmentStart, pit1.key().segmentStart);
            }

            if (c != 0) {
                return c;
            }
            // make sure the parameters are extracted in the order of their id
            // (rather than some random order from PriorityQueue)
            return Integer.compare(pit1.getParameterId(), pit2.getParameterId());
        }
    }
}
