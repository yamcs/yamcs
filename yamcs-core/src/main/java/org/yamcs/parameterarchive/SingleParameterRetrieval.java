package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;

/**
 * Retrieves values for a single parameter from the parameter archive. The result is arrays of samples (see
 * {@link ParameterValueArray}) corresponding to the segments of the archive.
 * <p>
 * Because all values are of the same type, the memory consumed by those arrays is much smaller than what is provided by
 * an equivalent single parameter retrieval using {@link MultiParameterRetrieval}
 * 
 */
public class SingleParameterRetrieval {
    final private ParameterRequest req;
    final private ParameterArchive parchive;
    private final Logger log = LoggerFactory.getLogger(SingleParameterRetrieval.class);
    final ParameterId[] pids;

    final int[] parameterGroupIds;

    public SingleParameterRetrieval(ParameterArchive parchive, String parameterFqn, ParameterRequest spvr) {
        this.req = spvr.copy();
        this.parchive = parchive;

        pids = parchive.getParameterIdDb().get(parameterFqn);
        if (pids == null) {
            log.warn("No parameter id found in the parameter archive for {}", parameterFqn);
        }
        this.parameterGroupIds = null;
    }

    SingleParameterRetrieval(ParameterArchive parchive, int parameterId, int[] parameterGroupIds,
            ParameterRequest spvr) {
        this.req = spvr;
        this.parchive = parchive;
        ParameterId pid1 = parchive.getParameterIdDb().getParameterId(parameterId);
        this.pids = new ParameterId[] { pid1 };
        this.parameterGroupIds = parameterGroupIds;

    }

    boolean hasData() {
        return pids != null;
    }

    public void retrieve(Consumer<ParameterValueArray> consumer) throws RocksDBException, IOException {
        if (pids == null) {
            return;
        }

        for (ParameterId pid : pids) {
            int[] pgids = parameterGroupIds;
            if (pgids == null) {
                pgids = parchive.getParameterGroupIdDb().getAllGroups(pid.getPid());
            }

            if (pgids.length == 0) {
                log.error("Found no parameter group for parameter Id {}", pid);
                continue;
            }

            if (pgids.length == 1) {
                retrieveValueSingleGroup(pid, pgids[0], consumer);
            } else {
                retrieveValuesMultiGroup(pid, pgids, consumer);
            }
        }
    }

    // this is the easy case, one single parameter group -> no merging of segments necessary
    private void retrieveValueSingleGroup(ParameterId pid, int parameterGroupId,
            Consumer<ParameterValueArray> consumer) throws RocksDBException, IOException {

        try (SegmentIterator it = new SegmentIterator(parchive, pid, parameterGroupId, req)) {
            while (it.isValid()) {
                ParameterValueSegment pvs = it.value();
                sendValuesFromSegment(pid, pvs, req, consumer);
                it.next();
            }
        }
    }

    // multiple parameter groups -> merging of segments necessary
    private void retrieveValuesMultiGroup(ParameterId pid, int parameterGroupIds[],
            Consumer<ParameterValueArray> consumer)
            throws RocksDBException, IOException {

        PriorityQueue<SegmentIterator> queue = new PriorityQueue<>(new SegmentIteratorComparator(req.ascending));
        try {
            for (int pgid : parameterGroupIds) {
                SegmentIterator it = new SegmentIterator(parchive, pid, pgid, req);
                if (it.isValid()) {
                    queue.add(it);
                } else { // not really necessary
                    it.close();
                }
            }
            SegmentMerger merger = new SegmentMerger(pid, req, consumer);

            while (!queue.isEmpty()) {
                SegmentIterator it = queue.poll();
                sendValuesFromSegment(pid, it.value(), req, merger);
                it.next();
                if (it.isValid()) {
                    queue.add(it);
                }
            }
            merger.flush();
        } finally {
            queue.forEach(it -> it.close());
        }

    }

    private void sendValuesFromSegment(ParameterId pid, ParameterValueSegment pvs, ParameterRequest pvr,
            Consumer<ParameterValueArray> consumer) {
        SortedTimeSegment timeSegment = pvs.timeSegment;
        int posStart, posStop;
        if (pvr.ascending) {
            posStart = timeSegment.search(pvr.start);
            if (posStart < 0) {
                posStart = -posStart - 1;
            }

            posStop = timeSegment.search(pvr.stop);
            if (posStop < 0) {
                posStop = -posStop - 1;
            }
        } else {
            posStop = timeSegment.search(pvr.stop);
            if (posStop < 0) {
                posStop = -posStop - 2;
            }

            posStart = timeSegment.search(pvr.start);
            if (posStart < 0) {
                posStart = -posStart - 2;
            }
        }

        if (posStart >= posStop) {
            return;
        }

        ParameterValueArray pva = pvs.getRange(posStart, posStop, pvr.ascending, pvr.isRetrieveParameterStatus());
        if (pva != null) {
            consumer.accept(pva);
        }
    }

    /**
     * Merges ParameterValueArray for same parameter and sends the result to the final consumer
     */
    static class SegmentMerger implements Consumer<ParameterValueArray> {
        final Consumer<ParameterValueArray> finalConsumer;
        final ParameterRequest spvr;
        ParameterValueArray mergedPva;
        ParameterId pid;

        public SegmentMerger(ParameterId pid, ParameterRequest spvr, Consumer<ParameterValueArray> finalConsumer) {
            this.finalConsumer = finalConsumer;
            this.spvr = spvr;
            this.pid = pid;
        }

        @Override
        public void accept(ParameterValueArray pva) {
            if (mergedPva == null) {
                mergedPva = pva;
            } else {
                if (ParameterArchive.getIntervalStart(mergedPva.timestamps[0]) != ParameterArchive
                        .getIntervalStart(pva.timestamps[0])) {
                    finalConsumer.accept(mergedPva);
                    mergedPva = pva;
                } else {
                    merge(pva);
                }
            }
        }

        // merge the pva with the mergedPva into a new pva
        private void merge(ParameterValueArray pva) {

            long[] timestamps0 = mergedPva.timestamps;
            long[] timestamps1 = pva.timestamps;

            long[] mergedTimestamps = new long[timestamps0.length + timestamps1.length];
            int[] src = new int[mergedTimestamps.length];

            int i0 = 0;
            int i1 = 0;
            int k = 0;

            while (true) {
                long t0, t1;
                if (i0 < timestamps0.length) {
                    t0 = timestamps0[i0];
                } else {
                    int n = timestamps1.length - i1;
                    System.arraycopy(timestamps1, i1, mergedTimestamps, k, n);
                    Arrays.fill(src, k, k + n, 1);
                    break;
                }

                if (i1 < timestamps1.length) {
                    t1 = timestamps1[i1];
                } else {
                    int n = timestamps0.length - i0;
                    System.arraycopy(timestamps0, i0, mergedTimestamps, k, n);
                    Arrays.fill(src, k, k + n, 0);
                    break;
                }
                if ((spvr.ascending && t0 <= t1) || (!spvr.ascending && t0 >= t1)) {
                    mergedTimestamps[k] = t0;
                    src[k] = 0;
                    k++;
                    i0++;
                } else {
                    mergedTimestamps[k] = t1;
                    src[k] = 1;
                    k++;
                    i1++;
                }
            }

            ValueArray engValues = null;
            if (mergedPva.engValues != null) {
                engValues = ValueArray.merge(src, mergedPva.engValues, pva.engValues);
            }
            ValueArray rawValues = null;
            if (mergedPva.rawValues != null) {
                rawValues = ValueArray.merge(src, mergedPva.rawValues, pva.rawValues);
            }
            ParameterStatus[] paramStatus = null;
            if (spvr.isRetrieveParameterStatus()) {
                paramStatus = (ParameterStatus[]) merge(src, mergedPva.paramStatus, pva.paramStatus);
            }

            mergedPva = new ParameterValueArray(mergedTimestamps, engValues, rawValues, paramStatus);
        }

        private ParameterStatus[] merge(int[] src, ParameterStatus[]... inputValueArray) {
            int[] idx = new int[inputValueArray.length];
            ParameterStatus[] r = new ParameterStatus[src.length];
            for (int i = 0; i < src.length; i++) {
                int n = src[i];
                r[i] = inputValueArray[n][idx[n]];
                idx[n]++;
            }
            return r;
        }

        /**
         * sends the last segment
         */
        public void flush() {
            if (mergedPva != null) {
                finalConsumer.accept(mergedPva);
            }
            mergedPva = null;
        }
    }

    static class ParameterValueSegmentCompatator implements Comparator<ParameterValueSegment> {
        final boolean ascending;

        public ParameterValueSegmentCompatator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(ParameterValueSegment o1, ParameterValueSegment o2) {
            int c;
            if (ascending) {
                c = Long.compare(o1.getSegmentStart(), o2.timeSegment.getSegmentStart());
            } else {
                c = Long.compare(o2.getSegmentStart(), o1.timeSegment.getSegmentStart());
            }

            return c;
        }
    }

    static class SegmentIteratorComparator implements Comparator<SegmentIterator> {
        final boolean ascending;

        public SegmentIteratorComparator(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(SegmentIterator it1, SegmentIterator it2) {
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
            return Integer.compare(it1.getParameterId().getPid(), it2.getParameterId().getPid());
        }
    }

}
