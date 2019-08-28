package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.logging.Log;
import org.yamcs.utils.TimeEncoding;

/**
 * Filler for one interval of the parameter archive.
 * 
 * An interval is composed from multiple segments each having maximum maxSegmentSize parameter values.
 * 
 * 
 * @author nm
 *
 */
public class ArchiveIntervalFiller {

    // parameter group id -> segment
    final Map<Integer, PGSegment> pgSegments = new HashMap<>();
    final ParameterArchive parchive;
    final ParameterGroupIdDb parameterGroupIdMap;
    int numParams;
    final int maxSegmentSize;
    final private Log log;
    final long intervalStart;

    public ArchiveIntervalFiller(ParameterArchive parchive, Log log, long intervalStart, int maxSegmentSize) {
        this.parchive = parchive;
        this.parameterGroupIdMap = parchive.getParameterGroupIdDb();
        this.maxSegmentSize = maxSegmentSize;
        this.log = log;
        this.intervalStart = intervalStart;
    }

    void addParameters(long t, BasicParameterList pvList) throws IOException, RocksDBException {
        if (intervalStart != ParameterArchive.getIntervalStart(t)) {
            throw new IllegalArgumentException("Data does not fit into this interval");
        }
        pvList.sort();

        numParams += pvList.size();
        int parameterGroupId = parameterGroupIdMap.createAndGet(pvList.getPids());

        PGSegment pgs = pgSegments.computeIfAbsent(parameterGroupId,
                k -> new PGSegment(parameterGroupId, intervalStart, pvList.getPids()));

        if (t < pgs.getSegmentStart()) {
            log.warn(
                    "Ignoring parameter data ({} parameters) because the time {} is too old for this segment starting at {}"
                            + "(this may happen in case of badly ordered high frequency data)",
                    pvList.size(),
                    TimeEncoding.toString(t), TimeEncoding.toString(pgs.getSegmentStart()));
            return;
        }

        pgs.addRecord(t, pvList.getValues());
        if (pgs.size() >= maxSegmentSize) {
            pgs.trimSegmentStart();
            log.debug("Segment {} reached max size {}, writing to disk", pgs, maxSegmentSize);
            parchive.writeToArchive(pgs);
            pgSegments.put(parameterGroupId,
                    new PGSegment(parameterGroupId, pgs.getSegmentEnd() + 1, pvList.getPids()));
        }
    }

    void flush() throws RocksDBException, IOException {
        log.debug("Flushing interval [{} - {}] with {} segments",
                TimeEncoding.toString(intervalStart),
                TimeEncoding.toString(ParameterArchive.getIntervalEnd(intervalStart)),
                pgSegments.size());

        for (PGSegment pgs : pgSegments.values()) {
            if (pgs.size() > 0) {
                parchive.writeToArchive(pgs);
            }
        }
    }
}
