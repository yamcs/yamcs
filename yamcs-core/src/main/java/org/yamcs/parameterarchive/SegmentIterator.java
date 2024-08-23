package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getIntervalEnd;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;

import java.io.IOException;
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
 * For a given simple parameter id and group id, iterates over all segments in the parameter archive (across all
 * partitions).
 * <p>
 * Provides objects of type {@link ParameterValueSegment} which contain multiple values of one parameter - suitable to
 * be used for bulk processing (e.g. downsampling or averaging).
 * <p>
 * The {@link ParameterIterator} can be used to iterate over parameters value by value (at the expense of consuming more
 * memory)
 * <p>
 * This iterator works like a Rocks iterator (with isValid(), next(), and value()) not like a java one. The advantage is
 * that one can look at the current value multiple times. This property is used when merging the iterators using a
 * priority queue.
 * 
 * <p>
 * The iterator has to be closed if it is not used until the end, otherwise a rocks iterator may be left hanging
 * 
 * <p>
 * Note about the raw values retrieval: the retrieval assumes that if raw values are requested, the parameter has raw
 * values (this can be known from the type associated to the parameter id).
 * <p>
 * Thus, if the raw values are requested and not found in the archive, the engineering values are returned as raw
 * values. This is an optimisation done in case the two are equal.
 * 
 * <p>
 * The iterator also sends data from RealtimeFiller if that is enabled.
 * 
 *
 */
public class SegmentIterator implements ParchiveIterator<ParameterValueSegment> {
    private final ParameterId parameterId;
    private final int parameterGroupId;

    ParameterArchive parchive;

    List<Partition> partitions;

    // iterates over partitions
    Iterator<Partition> topIt;

    // iterates over segments in one partition
    SubIterator subIt;

    final boolean ascending, retrieveEngValues, retrieveRawValues, retrieveParameterStatus;
    final long start, stop;

    ParameterValueSegment curValue;
    Iterator<ParameterValueSegment> rtIterator;
    final RealtimeArchiveFiller rtfiller;

    public SegmentIterator(ParameterArchive parchive, ParameterId parameterId, int parameterGroupId,
            ParameterRequest req) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.parchive = parchive;
        this.start = req.start;
        this.stop = req.stop;
        this.ascending = req.isAscending();
        this.retrieveEngValues = req.isRetrieveEngineeringValues();
        this.retrieveRawValues = (parameterId.getRawType() == null) ? false : req.isRetrieveRawValues();
        this.retrieveParameterStatus = req.isRetrieveParameterStatus();

        int pid = parameterId.getPid();

        rtfiller = parchive.getRealtimeFiller();

        if (retrieveEngValues || retrieveRawValues || retrieveParameterStatus) {
            partitions = parchive.getPartitions(getIntervalStart(req.start), getIntervalEnd(req.stop), req.ascending);
            topIt = partitions.iterator();

            if (rtfiller != null && !ascending) {
                rtIterator = rtfiller.getSegments(pid, parameterGroupId, ascending).iterator();
            }
            next();
        } // else the iterator will return isValid = false since there is nothing to retrieve
    }

    public boolean isValid() {
        return curValue != null;
    }

    public ParameterValueSegment value() {
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
                rtIterator = rtfiller.getSegments(parameterId.getPid(), parameterGroupId, ascending).iterator();
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

    public ParameterId getParameterId() {
        return parameterId;
    }

    class SubIterator {
        final Partition partition;
        private SegmentKey currentKey;
        SegmentEncoderDecoder segmentEncoder = new SegmentEncoderDecoder();
        private byte[] currentEngValueSegment;
        private byte[] currentRawValueSegment;
        private byte[] currentStatusSegment;
        private byte[] currentGaps;
        long currentGapsSegmentStart;
        /**
         * The dbIterator iterates over all segment types (raw value, eng value, parameter status). The time values are
         * received using point loockups.
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

            int pid = parameterId.getPid();

            // we use the 0 and Byte.MAX_VALUE for the segment type to make sure we catch all types.
            // ENG_VALUE=0 and PARAMETER_STATUS=2 could have been used as well
            var startk = new SegmentKey(pid, parameterGroupId, ParameterArchive.getIntervalStart(start),
                    (byte) 0);
            byte[] rangeStart = partition.version == 0 ? startk.encodeV0() : startk.encode();
            var stopk = new SegmentKey(pid, parameterGroupId, stop, Byte.MAX_VALUE);

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
            if (ascending) {
                nextAscending();
            } else {
                nextDescending();
            }
        }

        void nextAscending() {
            currentKey = partition.version == 0 ? SegmentKey.decodeV0(dbIterator.key())
                    : SegmentKey.decode(dbIterator.key());
            valid = true;

            SegmentKey key = currentKey;
            while (key.segmentStart == currentKey.segmentStart) {
                loadSegment(key.type);
                dbIterator.next();
                if (dbIterator.isValid()) {
                    key = partition.version == 0 ? SegmentKey.decodeV0(dbIterator.key())
                            : SegmentKey.decode(dbIterator.key());
                } else {
                    break;
                }
            }
        }

        void nextDescending() {
            currentKey = partition.version == 0 ? SegmentKey.decodeV0(dbIterator.key())
                    : SegmentKey.decode(dbIterator.key());
            valid = true;
            SegmentKey key = currentKey;

            while (key.segmentStart == currentKey.segmentStart) {
                loadSegment(key.type);
                dbIterator.prev();
                if (dbIterator.isValid()) {
                    key = partition.version == 0 ? SegmentKey.decodeV0(dbIterator.key())
                            : SegmentKey.decode(dbIterator.key());
                } else {
                    break;
                }
            }
        }

        private void loadSegment(byte type) {
            switch (type) {
            case SegmentKey.TYPE_ENG_VALUE:
                if (retrieveEngValues || retrieveRawValues) {
                    currentEngValueSegment = dbIterator.value();
                }
                break;
            case SegmentKey.TYPE_RAW_VALUE:
                if (retrieveRawValues) {
                    currentRawValueSegment = dbIterator.value();
                }
                break;
            case SegmentKey.TYPE_PARAMETER_STATUS:
                if (retrieveParameterStatus) {
                    currentStatusSegment = dbIterator.value();
                }
                break;
            case SegmentKey.TYPE_GAPS:
                // we remember from which segment this gaps is otherwise we may inherit the gaps from the previous
                // segment
                currentGapsSegmentStart = currentKey.segmentStart;
                currentGaps = dbIterator.value();
                break;
            }
        }

        SegmentKey key() {
            return currentKey;
        }

        ParameterValueSegment value() {
            if (!valid) {
                throw new NoSuchElementException();
            }

            long segStart = currentKey.segmentStart;
            try {
                var timeSegment = parchive.getTimeSegment(partition, segStart, parameterGroupId);
                if (timeSegment == null) {
                    String msg = "Cannot find a time segment for parameterGroupId=" + parameterGroupId
                            + " segmentStart = " + segStart + " despite having a value segment for parameterId: "
                            + parameterId;
                    throw new DatabaseCorruptionException(msg);
                }

                ValueSegment _engValueSegment = null;
                if (currentEngValueSegment != null) {
                    _engValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(currentEngValueSegment, segStart);
                }

                ValueSegment engValueSegment = retrieveEngValues ? _engValueSegment : null;

                ValueSegment rawValueSegment = null;
                if (currentRawValueSegment != null) {
                    rawValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(currentRawValueSegment, segStart);
                } else if (retrieveRawValues) {
                    rawValueSegment = _engValueSegment;
                }
                ParameterStatusSegment parameterStatusSegment = currentStatusSegment == null ? null
                        : (ParameterStatusSegment) SegmentEncoderDecoder.decode(currentStatusSegment,
                                segStart);
                SortedIntArray gaps = currentGaps == null || segStart != currentGapsSegmentStart ? null
                        : SegmentEncoderDecoder.decodeGaps(currentGaps);

                ParameterValueSegment pvs = new ParameterValueSegment(parameterId.getPid(), timeSegment,
                        engValueSegment, rawValueSegment, parameterStatusSegment, gaps);
                return pvs;
            } catch (

            DecodingException e) {
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
