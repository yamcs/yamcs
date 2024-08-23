package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getIntervalEnd;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.DbIterator;
import org.yamcs.yarch.rocksdb.DescendingRangeIterator;

/**
 * Same as {@link SegmentIterator} but provides segments for multiple parameters from the same group in one step.
 * <p>
 * Since Yamcs 5.9.4, with the introduction of the sparseGroup, some segments in the returned MultiParameterValueSegment
 * may be null if they contain no data
 *
 */
public class MultiSegmentIterator implements ParchiveIterator<MultiParameterValueSegment> {
    private final int parameterGroupId;
    private final ParameterId[] pids;

    ParameterArchive parchive;

    List<Partition> partitions;

    // iterates over partitions
    Iterator<Partition> topIt;

    // iterates over segments in one partition
    SubIterator subIt;

    final boolean ascending, retrieveEngValues, retrieveRawValues, retrieveParameterStatus;

    MultiParameterValueSegment curValue;
    final long start, stop;

    // iterates over the segments in the realtime filler
    Iterator<MultiParameterValueSegment> rtIterator;
    final RealtimeArchiveFiller rtfiller;

    public MultiSegmentIterator(ParameterArchive parchive, ParameterId[] pids, int parameterGroupId,
            ParameterRequest req) {
        this.pids = pids;
        this.parameterGroupId = parameterGroupId;
        this.parchive = parchive;
        this.start = req.start;
        this.stop = req.stop;
        this.ascending = req.isAscending();
        this.retrieveEngValues = req.isRetrieveEngineeringValues();
        this.retrieveRawValues = req.isRetrieveRawValues();
        this.retrieveParameterStatus = req.isRetrieveParameterStatus();

        partitions = parchive.getPartitions(getIntervalStart(start), getIntervalEnd(stop), req.ascending);
        topIt = partitions.iterator();

        rtfiller = parchive.getRealtimeFiller();

        if (rtfiller != null && !ascending) {
            rtIterator = rtfiller.getSegments(pids, parameterGroupId, ascending).iterator();
        }
        next();
    }

    public boolean isValid() {
        return curValue != null;
    }

    public MultiParameterValueSegment value() {
        return curValue;
    }

    public void next() {
        // descending with a realtime filler: retrieve first the values from the realtime that are in range
        if (!ascending && rtIterator != null) {
            curValue = null;
            while (rtIterator.hasNext()) {
                curValue = rtIterator.next();
                if (curValue.getSegmentStart() <= stop && curValue.getSegmentEnd() >= start) {
                    break;
                } else {
                    curValue = null;
                }
            }
            if (curValue == null) {
                rtIterator = null;
            } else {
                return;
            }
        }

        subIt = getPartitionIterator();
        if (subIt != null) {
            curValue = subIt.value();
            subIt.next();
            return;
        } else {
            curValue = null;
        }

        // ascending with a realtime filler: retrieve at the end the values from the realtime that are in range
        if (ascending && rtfiller != null) {
            if (rtIterator == null) {
                rtIterator = rtfiller.getSegments(pids, parameterGroupId, ascending).iterator();
            }
            long lastSegmentTime = curValue == null ? start : curValue.getSegmentEnd();
            curValue = null;

            while (rtIterator.hasNext()) {
                curValue = rtIterator.next();
                if (curValue.getSegmentStart() <= stop && curValue.getSegmentEnd() >= lastSegmentTime) {
                    break;
                } else {
                    curValue = null;
                }
            }
            if (curValue == null) {
                rtIterator = null;
            }
        }
    }

    private SubIterator getPartitionIterator() {
        while (subIt == null || !subIt.isValid()) {
            if (topIt.hasNext()) {
                Partition p = topIt.next();
                close(subIt);
                subIt = new SubIterator(p);
            } else {
                close(subIt);
                return null;
            }
        }
        return subIt;
    }

    /**
     * Close the underlying rocks iterator if not already closed
     */
    public void close() {
        close(subIt);
    }

    private void close(SubIterator pit) {
        if (pit != null) {
            pit.close();
        }
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }

    class SubIterator {
        final Partition partition;
        private SegmentKey currentKey;
        SegmentEncoderDecoder segmentEncoder = new SegmentEncoderDecoder();
        SortedTimeSegment currentTimeSegment;

        /**
         * The dbIterator iterates over the time segments. The other segments (eng value, raw value, status) are
         * retrieved using another iterator in the {@link #value() function}
         *
         */
        DbIterator dbIterator;
        boolean valid;

        public SubIterator(Partition partition) {
            this.partition = partition;
            RocksIterator iterator;
            try {
                iterator = parchive.getIterator(partition);
            } catch (RocksDBException | IOException e) {
                throw new ParameterArchiveException("Failed to create iterator", e);
            }

            int timeParaId = parchive.getParameterIdDb().getTimeParameterId();

            var startk = new SegmentKey(timeParaId, parameterGroupId, ParameterArchive.getIntervalStart(start),
                    SegmentKey.TYPE_ENG_VALUE);
            byte[] rangeStart = partition.version == 0 ? startk.encodeV0() : startk.encode();
            var stopk = new SegmentKey(timeParaId, parameterGroupId, stop, SegmentKey.TYPE_ENG_VALUE);
            byte[] rangeStop = partition.version == 0 ? stopk.encodeV0() : stopk.encode();
            if (ascending) {
                dbIterator = new AscendingRangeIterator(iterator, rangeStart, rangeStop);
            } else {
                dbIterator = new DescendingRangeIterator(iterator, rangeStart, rangeStop);
            }
            next();
        }

        public void next() {
            if (!dbIterator.isValid()) {
                valid = false;
                return;
            }
            valid = true;
            currentKey = this.partition.version == 0 ? SegmentKey.decodeV0(dbIterator.key())
                    : SegmentKey.decode(dbIterator.key());
            try {
                currentTimeSegment = (SortedTimeSegment) SegmentEncoderDecoder.decode(dbIterator.value(),
                        currentKey.segmentStart);
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException("Cannot decode time segment", e);
            }

            if (ascending) {
                dbIterator.next();
            } else {
                dbIterator.prev();
            }
        }

        SegmentKey key() {
            return currentKey;
        }

        MultiParameterValueSegment value() {
            if (!valid) {
                throw new NoSuchElementException();
            }

            List<ParameterValueSegment> pvSegments = new ArrayList<>(pids.length);

            long segStart = currentKey.segmentStart;
            try (RocksIterator it = parchive.getIterator(partition)) {
                for (int i = 0; i < pids.length; i++) {
                    int pid = pids[i].getPid();
                    SegmentKey key = new SegmentKey(pid, parameterGroupId, segStart, (byte) 0);
                    it.seek(partition.version == 0 ? key.encodeV0() : key.encode());
                    if (!it.isValid()) {
                        throw new DatabaseCorruptionException(
                                "Cannot find any record for parameter id " + pid + " at start " + segStart);
                    }
                    ValueSegment engValueSegment = null;
                    ValueSegment rawValueSegment = null;
                    ParameterStatusSegment parameterStatusSegment = null;
                    SortedIntArray gaps = null;
                    boolean found = false;
                    while (it.isValid()) {
                        key = partition.version == 0 ? SegmentKey.decodeV0(it.key()) : SegmentKey.decode(it.key());
                        if (key.parameterId != pid || key.parameterGroupId != parameterGroupId) {
                            break;
                        }
                        byte type = key.type;
                        if (key.segmentStart != segStart) {
                            break;
                        }
                        found = true;
                        switch (type) {
                        case SegmentKey.TYPE_ENG_VALUE:
                            if (retrieveEngValues || retrieveRawValues) {
                                engValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(it.value(), segStart);
                            }
                            break;
                        case SegmentKey.TYPE_RAW_VALUE:
                            if (retrieveRawValues) {
                                rawValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(it.value(), segStart);
                            }
                            break;
                        case SegmentKey.TYPE_PARAMETER_STATUS:
                            if (retrieveParameterStatus) {
                                parameterStatusSegment = (ParameterStatusSegment) SegmentEncoderDecoder.decode(
                                        it.value(),
                                        segStart);
                            }
                            break;
                        case SegmentKey.TYPE_GAPS:
                            gaps = SegmentEncoderDecoder.decodeGaps(it.value());
                            break;
                        }
                        it.next();
                    }
                    if (retrieveRawValues && rawValueSegment == null) {
                        rawValueSegment = engValueSegment;
                    }
                    if (!retrieveEngValues) {
                        engValueSegment = null;
                    }
                    if (found) {
                        pvSegments.add(
                                new ParameterValueSegment(pid, currentTimeSegment, engValueSegment, rawValueSegment,
                                        parameterStatusSegment, gaps));
                    } else {
                        pvSegments.add(null);
                    }
                }
                MultiParameterValueSegment pvs = new MultiParameterValueSegment(pids, currentTimeSegment, pvSegments);
                return pvs;
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException(e);
            } catch (RocksDBException | IOException e) {
                throw new ParameterArchiveException("Failded extracting data from the parameter archive", e);
            }
        }

        boolean isValid() {
            return valid;
        }

        void close() {
            if (dbIterator != null) {
                dbIterator.close();
            }
        }
    }

}
