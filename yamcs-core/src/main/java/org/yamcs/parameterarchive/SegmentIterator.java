package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameterarchive.ParameterArchiveStorage.ScanRequest;
import org.yamcs.parameterarchive.ParameterArchiveStorage.SegmentRecord;
import org.yamcs.parameterarchive.ParameterArchiveStorage.SegmentScanner;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;

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
    // iterates over the segments in the archive
    SegmentScanner scanner;

    final boolean ascending, retrieveEngValues, retrieveRawValues, retrieveParameterStatus;
    final long start, stop;
    ParameterValueSegment curValue;

    Iterator<ParameterValueSegment> rtIterator;
    final RealtimeArchiveFiller rtfiller;

    public SegmentIterator(ParameterArchive parchive, ParameterId parameterId, int parameterGroupId,
            ParameterRetrievalOptions req) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.parchive = parchive;
        this.start = req.start();
        this.stop = req.stop();
        this.ascending = req.ascending();
        this.retrieveEngValues = req.retrieveEngValues();
        this.retrieveRawValues = (parameterId.getRawType() == null) ? false : req.retrieveRawValues();
        this.retrieveParameterStatus = req.retrieveParameterStatus();
        int pid = parameterId.getPid();
        rtfiller = parchive.getRealtimeFiller();

        if (retrieveEngValues || retrieveRawValues || retrieveParameterStatus) {
            int timePid = parchive.getParameterIdDb().getTimeParameterId();
            // the engineering values are used as raw values if the raw values are not stored
            scanner = parchive.getStorage().scan(new ScanRequest(timePid, new int[] { pid },
                    new int[] { parameterGroupId }, start, stop, ascending,
                    retrieveEngValues || retrieveRawValues, retrieveRawValues, retrieveParameterStatus));
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

        if (scanner != null && scanner.isValid()) {
            curValue = toValueSegment(scanner.value());
            scanner.next();
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

    /**
     * Close the underlying storage scanner if not already closed
     */
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }

    public ParameterId getParameterId() {
        return parameterId;
    }

    private ParameterValueSegment toValueSegment(SegmentRecord rec) {
        long segStart = rec.segmentStart();
        try {
            if (rec.timeSegment() == null) {
                String msg = "Cannot find a time segment for parameterGroupId=" + parameterGroupId
                        + " segmentStart = " + segStart + " despite having a value segment for parameterId: "
                        + parameterId;
                throw new DatabaseCorruptionException(msg);
            }
            var timeSegment = (SortedTimeSegment) SegmentEncoderDecoder.decode(rec.timeSegment(), segStart);

            ValueSegment _engValueSegment = null;
            if (rec.engValueSegment() != null) {
                _engValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(rec.engValueSegment(), segStart);
            }

            ValueSegment engValueSegment = retrieveEngValues ? _engValueSegment : null;

            ValueSegment rawValueSegment = null;
            if (rec.rawValueSegment() != null) {
                rawValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(rec.rawValueSegment(), segStart);
            } else if (retrieveRawValues) {
                rawValueSegment = _engValueSegment;
            }

            ParameterStatusSegment parameterStatusSegment = rec.parameterStatusSegment() == null ? null
                    : (ParameterStatusSegment) SegmentEncoderDecoder.decode(rec.parameterStatusSegment(), segStart);

            SortedIntArray gaps = rec.gaps() == null ? null : SegmentEncoderDecoder.decodeGaps(rec.gaps());

            checkConsistency(timeSegment, engValueSegment, rawValueSegment, parameterStatusSegment, gaps);

            return new ParameterValueSegment(parameterId.getPid(), timeSegment, engValueSegment, rawValueSegment,
                    parameterStatusSegment, gaps);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    /**
     * Checks that the size of the engingeering raw and parameter status is the same and the size of the gaps with
     * the size of the values is equal to the size of the timestamp segment.
     */
    private void checkConsistency(SortedTimeSegment timeSegment, ValueSegment engValueSegment,
            ValueSegment rawValueSegment, ParameterStatusSegment parameterStatusSegment, SortedIntArray gaps) {

        int timeSize = timeSegment.size();
        int gapSize = gaps == null ? 0 : gaps.size();

        if ((engValueSegment != null && engValueSegment.size() + gapSize != timeSize)
                || (rawValueSegment != null && rawValueSegment.size() + gapSize != timeSize)
                || (parameterStatusSegment != null && parameterStatusSegment.size() + gapSize != timeSize)) {
            String err = String.format(
                    """
                                    Parameter %s id=%d pgid=%d: size of the values segment + gaps does not match the size of the time segment.
                                    Values size: %s, Gap size: %d, Time size: %d
                                    If this is a database made with Yamcs versions 5.10.0 - 5.10.7, please rebuild the corrupted segment using the command:
                                    "yamcs parameter-archive rebuild %s %s".

                            """,
                    parameterId.getParamFqn(), parameterId.getPid(), parameterGroupId,
                    getSizesString(engValueSegment, rawValueSegment, parameterStatusSegment),
                    gapSize, timeSize,
                    TimeEncoding.toString(timeSegment.getInterval()),
                    TimeEncoding.toString(timeSegment.getSegmentEnd()));

            throw new DatabaseCorruptionException(err);
        }
    }

    private String getSizesString(ValueSegment engValueSegment, ValueSegment rawValueSegment,
            ParameterStatusSegment parameterStatusSegment) {
        List<String> parts = new ArrayList<>();

        if (engValueSegment != null) {
            parts.add("eng=" + engValueSegment.size());
        }
        if (rawValueSegment != null) {
            parts.add("raw=" + rawValueSegment.size());
        }
        if (parameterStatusSegment != null) {
            parts.add("status=" + parameterStatusSegment.size());
        }

        return String.join(", ", parts);
    }
}
