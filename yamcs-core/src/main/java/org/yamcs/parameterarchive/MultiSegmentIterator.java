package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameterarchive.ParameterArchiveStorage.ScanRequest;
import org.yamcs.parameterarchive.ParameterArchiveStorage.SegmentRecord;
import org.yamcs.parameterarchive.ParameterArchiveStorage.SegmentScanner;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;

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

    /**
     * Scans the time segments of the group (first pair) and the segments of the parameters (next pairs). For each
     * segment start, the time segment comes before the parameter segments.
     */
    SegmentScanner scanner;

    final boolean ascending, retrieveEngValues, retrieveRawValues, retrieveParameterStatus;
    MultiParameterValueSegment curValue;
    final long start, stop;

    Iterator<MultiParameterValueSegment> rtIterator;
    final RealtimeArchiveFiller rtfiller;

    public MultiSegmentIterator(ParameterArchive parchive, ParameterId[] pids, int parameterGroupId,
            ParameterRetrievalOptions req) {
        this.pids = pids;
        this.parameterGroupId = parameterGroupId;
        this.parchive = parchive;
        this.start = req.start();
        this.stop = req.stop();
        this.ascending = req.ascending();
        this.retrieveEngValues = req.retrieveEngValues();
        this.retrieveRawValues = req.retrieveRawValues();
        this.retrieveParameterStatus = req.retrieveParameterStatus();

        int timePid = parchive.getParameterIdDb().getTimeParameterId();
        int[] spids = new int[pids.length + 1];
        int[] pgids = new int[pids.length + 1];
        spids[0] = timePid;
        pgids[0] = parameterGroupId;
        for (int i = 0; i < pids.length; i++) {
            spids[i + 1] = pids[i].getPid();
            pgids[i + 1] = parameterGroupId;
        }
        scanner = parchive.getStorage().scan(new ScanRequest(timePid, spids, pgids, start, stop, ascending,
                retrieveEngValues || retrieveRawValues, retrieveRawValues, retrieveParameterStatus));

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

        curValue = nextFromArchive();
        if (curValue != null) {
            return;
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

    /**
     * Reads from the scanner the time segment and the segments of the parameters having the same segment start.
     * <p>
     * Returns null if there is no more data in the archive
     */
    private MultiParameterValueSegment nextFromArchive() {
        try {
            while (scanner.isValid()) {
                SegmentRecord rec = scanner.value();
                scanner.next();
                if (rec.pairIndex() != 0) {
                    // parameter segments without a time segment; the parameters are retrieved only for the existing
                    // time segments
                    continue;
                }
                long segStart = rec.segmentStart();
                var timeSegment = (SortedTimeSegment) SegmentEncoderDecoder.decode(rec.timeSegment(), segStart);
                List<ParameterValueSegment> pvSegments = new ArrayList<>(Collections.nCopies(pids.length, null));

                while (scanner.isValid() && scanner.value().segmentStart() == segStart) {
                    SegmentRecord prec = scanner.value();
                    scanner.next();
                    if (prec.pairIndex() > 0) {
                        pvSegments.set(prec.pairIndex() - 1, toValueSegment(prec, timeSegment));
                    }
                }
                return new MultiParameterValueSegment(pids, timeSegment, pvSegments);
            }
            return null;
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    private ParameterValueSegment toValueSegment(SegmentRecord rec, SortedTimeSegment timeSegment)
            throws DecodingException {
        long segStart = rec.segmentStart();
        ValueSegment engValueSegment = null;
        ValueSegment rawValueSegment = null;
        ParameterStatusSegment parameterStatusSegment = null;
        SortedIntArray gaps = null;

        if (rec.engValueSegment() != null) {
            engValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(rec.engValueSegment(), segStart);
        }
        if (rec.rawValueSegment() != null) {
            rawValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(rec.rawValueSegment(), segStart);
        }
        if (rec.parameterStatusSegment() != null) {
            parameterStatusSegment = (ParameterStatusSegment) SegmentEncoderDecoder
                    .decode(rec.parameterStatusSegment(), segStart);
        }
        if (rec.gaps() != null) {
            gaps = SegmentEncoderDecoder.decodeGaps(rec.gaps());
        }
        if (retrieveRawValues && rawValueSegment == null) {
            rawValueSegment = engValueSegment;
        }
        if (!retrieveEngValues) {
            engValueSegment = null;
        }
        return new ParameterValueSegment(rec.pid(), timeSegment, engValueSegment, rawValueSegment,
                parameterStatusSegment, gaps);
    }

    /**
     * Close the underlying storage scanner if not already closed
     */
    public void close() {
        scanner.close();
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }
}
