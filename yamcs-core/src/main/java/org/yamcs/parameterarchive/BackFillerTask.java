package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.parameterarchive.ParameterArchive.*;

class BackFillerTask extends AbstractArchiveFiller {
    // ParameterGroup_id -> PGSegment
    protected Map<Integer, PGSegment> pgSegments = new HashMap<>();
    private Processor processor;
    long coverageEnd = TimeEncoding.NEGATIVE_INFINITY;
    private final FillerLock fillerLock;
    private final Map<LockFailureKey, LockFailureCount> lockFailureCount = new HashMap<>();

    public BackFillerTask(ParameterArchive parameterArchive) {
        super(parameterArchive);
        this.fillerLock = parameterArchive.getFillerLock();
    }

    void flush() {
        for (PGSegment pgs : pgSegments.values()) {
            writeToArchive(pgs);
            fillerLock.unlock(pgs.getInterval(), pgs.getParameterGroupId());
            var segEnd = pgs.getSegmentEnd();
            if (segEnd <= parameterArchive.maxCoverageEnd()) {
                coverageEnd = Math.max(coverageEnd, segEnd);
            }
        }
        parameterArchive.updateCoverageEnd(coverageEnd);
    }

    public void setProcessor(Processor proc) {
        this.processor = proc;
    }

    protected void writeToArchive(PGSegment pgSegment) {
        try {
            long t0 = System.nanoTime();
            parameterArchive.writeToArchive(pgSegment);
            long d = System.nanoTime() - t0;
            log.trace("Wrote segment {} to archive in {} millisec", pgSegment, d / 1000_000);
        } catch (RocksDBException | IOException e) {
            log.error("Error writing segment to archive", e);
            throw new ParameterArchiveException("Error writing segment to archive", e);
        }
        var segEnd = pgSegment.getSegmentEnd();
        if (segEnd <= parameterArchive.maxCoverageEnd()) {
            coverageEnd = Math.max(coverageEnd, segEnd);
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {
        try {
            var pg = parameterGroupIdMap.getGroup(pvList.getPids());
            var parameterGroupId = pg.id;
            var interval = getInterval(t);
            PGSegment pgs = pgSegments.get(parameterGroupId);

            if (pgs == null) {
                if (!fillerLock.try_lock(interval, parameterGroupId, this)) {
                    var lfc = lockFailureCount.computeIfAbsent(new LockFailureKey(interval, parameterGroupId),
                            k -> new LockFailureCount());
                    lfc.increment();
                    if (lfc.count == 1 || lfc.count % 100 == 0) {
                        log.warn(
                                "Failed to aquire lock {} for interval {} parameter group {} (backfiller overlapping with realtime filler?); dropping parameters ",
                                lfc.count > 1 ? "(" + lfc.count + " times already)" : "",
                                TimeEncoding.toString(interval), parameterGroupId);
                    }
                    return;
                }
                pgs = new PGSegment(parameterGroupId, interval, pg.pids.size());
                pgs.addRecord(t, pvList);
                pgSegments.put(parameterGroupId, pgs);
            } else if (interval != pgs.getInterval()) {
                writeToArchive(pgs);
                fillerLock.unlock(pgs.getInterval(), parameterGroupId);

                if (!fillerLock.try_lock(interval, parameterGroupId, this)) {
                    log.warn(
                            "Failed to aquire lock for interval {} parameter group {} (backfiller overlapping with realtime filler?); dropping parameters ",
                            TimeEncoding.toString(interval), parameterGroupId);
                    return;
                }
                var pgs1 = new PGSegment(parameterGroupId, interval, pg.pids.size());
                pgs1.addRecord(t, pvList);
                pgSegments.put(parameterGroupId, pgs1);
            } else if (pgs.size() >= maxSegmentSize) {
                pgs.freeze();
                writeToArchive(pgs);

                var pgs1 = new PGSegment(parameterGroupId, interval, pg.pids.size());
                pgs1.addRecord(t, pvList);
                pgs1.continueSegment(pgs);
                pgSegments.put(parameterGroupId, pgs1);
            } else {
                pgs.addRecord(t, pvList);
            }

        } catch (RocksDBException e) {
            log.error("Error writing to the parameter archive", e);
        }
    }

    @Override
    protected void abort() {
        processor.stopAsync();
    }

    static record LockFailureKey(long interval, int parameterGroupId) {
    }

    static class LockFailureCount {
        int count = 0;

        void increment() {
            count++;
        }
    }
}
