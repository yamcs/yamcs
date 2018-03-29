package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.MultiParameterDataRetrieval.PartitionIteratorComparator;
import org.yamcs.parameterarchive.ParameterArchiveV2.Partition;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.DatabaseCorruptionException;

import static org.yamcs.parameterarchive.SortedTimeSegment.getSegmentStart;

public class SingleParameterArchiveRetrieval {
    final private ParameterRequest spvr;
    final private ParameterArchiveV2 parchive;
    private final Logger log = LoggerFactory.getLogger(SingleParameterArchiveRetrieval.class);
    final ParameterId[] pids;
    
    final int[] parameterGroupIds; 
    
    public SingleParameterArchiveRetrieval(ParameterArchiveV2 parchive, String parameterFqn, ParameterRequest spvr) {
        this.spvr = spvr;
        this.parchive = parchive;

        pids = parchive.getParameterIdDb().get(parameterFqn);
        if (pids == null) {
            log.warn("No parameter id found in the parameter archive for {}", parameterFqn);
        }
        this.parameterGroupIds = null;
    }

    
    SingleParameterArchiveRetrieval(ParameterArchiveV2 parchive, int parameterId, ParameterRequest spvr) {
        this.spvr = spvr;
        this.parchive = parchive;
        ParameterId pid = parchive.getParameterIdDb().getParameterId(parameterId);
        this.pids = new ParameterId[]{pid};
        this.parameterGroupIds = null;
    }
    
    SingleParameterArchiveRetrieval(ParameterArchiveV2 parchive, int parameterId, int[] parameterGroupIds, ParameterRequest spvr) {
        this.spvr = spvr;
        this.parchive = parchive;
        ParameterId pid1 = parchive.getParameterIdDb().getParameterId(parameterId);
        this.pids = new ParameterId[]{pid1};
        this.parameterGroupIds = parameterGroupIds;
      
    }
    boolean hasData() {
        return pids != null;
    }
    
    

    public void retrieve(Consumer<ParameterValueArray> consumer) throws RocksDBException, IOException {
        if(pids==null) {
            return;
        }
        
        for (ParameterId pid : pids) {
            int[] pgids = parameterGroupIds;
            if(pgids==null) {
                pgids = parchive.getParameterGroupIdDb().getAllGroups(pid.pid);
            }
            
            if (pgids.length == 0) {
                log.error("Found no parameter group for parameter Id {}", pid);
                continue;
            }
            retrieveForId(pid, pgids, consumer);
        }
    }

    private void retrieveForId(ParameterId pid, int[] pgids, Consumer<ParameterValueArray> consumer)
            throws RocksDBException, IOException {

        List<Partition> parts = parchive.getPartitions(getSegmentStart(spvr.start), getSegmentStart(spvr.stop),
                spvr.ascending);
        if (pgids.length == 1) {
            for (Partition p : parts) {
                retrieveValuesFromPartitionSingleGroup(pid, pgids[0], p, consumer);
            }
        } else {
            for (Partition p : parts) {
                retrieveValuesFromPartitionMultiGroup(pid, pgids, p, consumer);
            }
        }
    }

    // this is the easy case, one single parameter group -> no merging of segments necessary
    private void retrieveValuesFromPartitionSingleGroup(ParameterId pid, int parameterGroupId, Partition p,
            Consumer<ParameterValueArray> consumer) throws RocksDBException, IOException {
        RocksIterator it = parchive.getIterator(p);
        boolean retrieveEng = spvr.isRetrieveRawValues() || spvr.isRetrieveEngineeringValues();
        try {
            PartitionIterator pit = new PartitionIterator(it, pid.pid, parameterGroupId, spvr.start, spvr.stop,
                    spvr.ascending, retrieveEng, spvr.isRetrieveRawValues(), spvr.isRetrieveParameterStatus());

            while (pit.isValid()) {
                SegmentKey key = pit.key();
                SortedTimeSegment timeSegment = parchive.getTimeSegment(p, key.segmentStart, parameterGroupId);
                if (timeSegment == null) {
                    String msg = "Cannot find a time segment for parameterGroupId=" + parameterGroupId
                            + " segmentStart = " + key.segmentStart
                            + " despite having a value segment for parameterId: " + pid.pid;
                    log.error(msg);
                    throw new DatabaseCorruptionException(msg);
                }

                retriveValuesFromSegment(pid, timeSegment, pit, spvr, consumer);
                pit.next();
            }
        } finally {
            it.close();
        }
    }

    // multiple parameter groups -> merging of segments necessary
    private void retrieveValuesFromPartitionMultiGroup(ParameterId pid, int parameterGroupIds[], Partition p, Consumer<ParameterValueArray> consumer)
            throws RocksDBException, IOException {
        RocksIterator[] its = new RocksIterator[parameterGroupIds.length];
        try {

            PriorityQueue<PartitionIterator> queue = new PriorityQueue<PartitionIterator>(
                    new PartitionIteratorComparator(spvr.ascending));
            boolean retrieveEng = spvr.isRetrieveRawValues() || spvr.isRetrieveEngineeringValues();

            for (int i = 0; i < parameterGroupIds.length; i++) {
                its[i] = parchive.getIterator(p);
                PartitionIterator pi = new PartitionIterator(its[i], pid.pid, parameterGroupIds[i],
                        spvr.start, spvr.stop, spvr.ascending,
                        retrieveEng, spvr.isRetrieveRawValues(), spvr.isRetrieveParameterStatus());

                if (pi.isValid()) {
                    queue.add(pi);
                }
            }
            SegmentMerger merger = new SegmentMerger(pid, spvr, consumer);
            while (!queue.isEmpty()) {
                PartitionIterator pit = queue.poll();
                SegmentKey key = pit.key();
                SortedTimeSegment timeSegment = parchive.getTimeSegment(p, key.segmentStart, pit.getParameterGroupId());
                if (timeSegment == null) {
                    String msg = "Cannot find a time segment for parameterGroupId=" + pit.getParameterGroupId()
                            + " segmentStart = " + key.segmentStart
                            + " despite having a value segment for parameterId: " + pid.pid;
                    log.error(msg);
                    throw new DatabaseCorruptionException(msg);
                }
                retriveValuesFromSegment(pid, timeSegment, pit, spvr, merger);
                pit.next();
                if (pit.isValid()) {
                    queue.add(pit);
                }
            }
            merger.flush();
        } finally {
            for (RocksIterator it : its) {
                it.close();
            }
        }
    }

    private void retriveValuesFromSegment(ParameterId pid, SortedTimeSegment timeSegment, PartitionIterator pit,
            ParameterRequest pvr, Consumer<ParameterValueArray> consumer) {
        ValueSegment engValueSegment = pit.engValue();
        ValueSegment rawValueSegment = pit.rawValue();
        ParameterStatusSegment parameterStatusSegment = pit.parameterStatus();

        // if raw type is not null it means that rawValues do exist-> if rawValueSegment is null means they are equal with the
        // engValues
        if ((rawValueSegment == null) && (pid.getRawType()!=null)) {
            rawValueSegment = engValueSegment;
            
            if(!spvr.isRetrieveEngineeringValues()) {
                engValueSegment = null;
            }
        }

        if ((engValueSegment == null) && (rawValueSegment == null) && (parameterStatusSegment == null)) {
            return;
        }

        int posStart, posStop;
        if (pvr.ascending) {
            if (pvr.start < timeSegment.getSegmentStart()) {
                posStart = 0;
            } else {
                posStart = timeSegment.search(pvr.start);
                if (posStart < 0) {
                    posStart = -posStart - 1;
                }
            }

            if (pvr.stop > timeSegment.getSegmentEnd()) {
                posStop = timeSegment.size();
            } else {
                posStop = timeSegment.search(pvr.stop);
                if (posStop < 0) {
                    posStop = -posStop - 1;
                }
            }
        } else {
            if (pvr.stop > timeSegment.getSegmentEnd()) {
                posStop = timeSegment.size() - 1;
            } else {
                posStop = timeSegment.search(pvr.stop);
                if (posStop < 0) {
                    posStop = -posStop - 2;
                }
            }

            if (pvr.start < timeSegment.getSegmentStart()) {
                posStart = -1;
            } else {
                posStart = timeSegment.search(pvr.start);
                if (posStart < 0) {
                    posStart = -posStart - 2;
                }
            }
        }

        if (posStart >= posStop) {
            return;
        }

        long[] timestamps = timeSegment.getRange(posStart, posStop, pvr.ascending);
        ValueArray engValues = null;
        if (engValueSegment!=null) {
            engValues = engValueSegment.getRange(posStart, posStop, pvr.ascending);
        }
        
        ValueArray rawValues = null;
        if (rawValueSegment!=null) {
            rawValues = rawValueSegment.getRange(posStart, posStop, pvr.ascending);
        }

        ParameterStatus[] paramStatus = null;
        if (pvr.isRetrieveParameterStatus()) {
            paramStatus = parameterStatusSegment.getRangeArray(posStart, posStop, pvr.ascending);
        }
        ParameterValueArray pva = new ParameterValueArray(timestamps, engValues, rawValues, paramStatus);
        consumer.accept(pva);
    }

    /**
     * Merges ParameterValueArray for same parameter and sends the result to the final consumer
     * 
     * @author nm
     *
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
                if (SortedTimeSegment.getSegmentId(mergedPva.timestamps[0]) != SortedTimeSegment
                        .getSegmentId(pva.timestamps[0])) {
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
                    Arrays.fill(src, k, k+n, 1);
                    break;
                }

                if (i1 < timestamps1.length) {
                    t1 = timestamps1[i1];
                } else {
                    int n = timestamps0.length - i0;
                    System.arraycopy(timestamps0, i0, mergedTimestamps, k, n);
                    Arrays.fill(src, k, k+n, 0);
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
            if (mergedPva.engValues!=null) {
                engValues = ValueArray.merge(src, mergedPva.engValues, pva.engValues);
            }
            ValueArray rawValues = null;
            if (mergedPva.rawValues!=null) {
                rawValues = ValueArray.merge(src, mergedPva.rawValues, pva.rawValues);
            }
            ParameterStatus[] paramStatus = null;
            if (spvr.isRetrieveParameterStatus()) {
                paramStatus = (ParameterStatus[]) merge(src, mergedPva.paramStatus, pva.paramStatus);
            }

            mergedPva = new ParameterValueArray(mergedTimestamps, engValues, rawValues, paramStatus);
        }

        private ParameterStatus[] merge(int[] src, ParameterStatus[]...inputValueArray) {
            int[] idx = new int[inputValueArray.length];
            ParameterStatus [] r = new ParameterStatus[src.length];
            for(int i= 0; i<src.length; i++) {
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

}
