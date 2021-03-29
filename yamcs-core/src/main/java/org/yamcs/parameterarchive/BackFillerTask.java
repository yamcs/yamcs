package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;

import static org.yamcs.parameterarchive.ParameterArchive.*;

/**
 *
 */
class BackFillerTask extends AbstractArchiveFiller {


    // ParameterGroup_id -> PGSegment
    protected Map<Integer, PGSegment> pgSegments = new HashMap<>();

    // ignore any data older than this
    // when doing backfilling, there is a warming up interval - the replay is started with older data
    protected long collectionStart;


    private Processor processor;
    boolean aborted = false;

    public BackFillerTask(ParameterArchive parameterArchive) {
        super(parameterArchive);
        log.debug("Archive filler task maxSegmentSize: {} ", maxSegmentSize);
    }

    void setCollectionStart(long collectionStart) {
        this.collectionStart = collectionStart;
    }

    void flush() throws RocksDBException, IOException {
        for (PGSegment seg : pgSegments.values()) {
            parameterArchive.writeToArchive(seg);
        }
    }


    public void setProcessor(Processor proc) {
        this.processor = proc;
    }

    protected void writeToArchive(PGSegment pgSegment) {
        try {
            parameterArchive.writeToArchive(pgSegment);
        } catch (RocksDBException | IOException e) {
            log.error("Error writing segment to archive", e);
            throw new ParameterArchiveException("Error writing segment to arcive", e);
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {

        try {
            int parameterGroupId = parameterGroupIdMap.createAndGet(pvList.getPids());

            PGSegment pgs = pgSegments.computeIfAbsent(parameterGroupId,
                    id -> new PGSegment(parameterGroupId, t, pvList.getPids()));

            if (getInterval(t) != pgs.getInterval()) {
                writeToArchive(pgs);
                pgs = new PGSegment(parameterGroupId, t, pvList.getPids());
                pgSegments.put(parameterGroupId, pgs);
            }

            pgs.addRecord(t, pvList.getValues());
            if (pgs.size() >= maxSegmentSize) {
                parameterArchive.writeToArchive(pgs);
                pgSegments.remove(parameterGroupId);
            }
        } catch (RocksDBException | IOException e) {
            log.error("Error writing to the parameter archive", e);
        }
    }


    @Override
    protected void abort() {
        processor.stopAsync();
    }
}
