package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;


import static org.yamcs.parameterarchive.ParameterArchive.*;

class BackFillerTask extends AbstractArchiveFiller {
    // ParameterGroup_id -> PGSegment
    protected Map<Integer, PGSegment> pgSegments = new HashMap<>();
    private Processor processor;

    public BackFillerTask(ParameterArchive parameterArchive) {
        super(parameterArchive);
    }

    void flush() {
        for (PGSegment seg : pgSegments.values()) {
            writeToArchive(seg);
        }
    }

    public void setProcessor(Processor proc) {
        this.processor = proc;
    }

    protected void writeToArchive(PGSegment pgSegment) {
        try {
            long t0 = System.nanoTime();
            parameterArchive.writeToArchive(pgSegment);
            long d = System.nanoTime() - t0;
            log.debug("Wrote segment {} to archive in {} millisec", pgSegment, d / 1000_000);
        } catch (RocksDBException | IOException e) {
            log.error("Error writing segment to archive", e);
            throw new ParameterArchiveException("Error writing segment to arcive", e);
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {
        try {
            var pg = parameterGroupIdMap.getGroup(pvList.getPids());
            var parameterGroupId = pg.id;
            var interval = getInterval(t);
            PGSegment pgs = pgSegments.computeIfAbsent(parameterGroupId,
                    id -> new PGSegment(parameterGroupId, interval, pg.pids.size()));

            if (interval != pgs.getInterval()) {
                writeToArchive(pgs);
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
}
