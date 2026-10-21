package org.yamcs.parameterarchive;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.RocksDBException;
import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameterarchive.ParameterArchiveStorage.ParchiveWriteBatch;
import org.yamcs.parameterarchive.ParameterArchiveStorage.ScanRequest;
import org.yamcs.parameterarchive.ParameterGroupIdDb.ParameterGroup;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeService;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.IntHashSet;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.Tablespace;

/**
 * 
 * The parameter archive stores data in partitions(optional) -> intervals -> segments.
 * <p>
 * A partition covers one year/month/day and each partition has its own RocksDB database.
 * <p>
 * An interval covers 2^23 millisec (=~ 139 minutes) - so for any timestamp (Yamcs time) we know exactly in which
 * interval it falls.
 * <p>
 * A segment covers at most maxSegmentSize samples for one parameter. The segments do not cover a fixed period of time;
 * we use them to avoid intervals getting very large; usually (1Hz or less frequency data) there is only one segment in
 * an interval.
 *
 * <p>
 * Segments cannot span across intervals.
 * 
 * <p>
 * When new data has been received in the past, the whole interval has to be re-created (by doing a replay); that likely
 * means a new split of the respective interval into segments.
 * 
 */
public class ParameterArchive extends AbstractYamcsService {
    /**
     * 
     * version 0 - before Yamcs 5.10
     * <p>
     * version 1 - starting with Yamcs 5.10
     * <ul>
     * <li>uses the RocksDB merge operator</li>
     * <li>sorts properly the timestamps</li>
     * </ul>
     */
    public static final int VERSION = 1;

    public static final boolean STORE_RAW_VALUES = true;

    public static final int NUMBITS_MASK = 23; // 2^23 milliseconds =~ 139 minutes per interval
    public static final int TIMESTAMP_MASK = (0xFFFFFFFF >>> (32 - NUMBITS_MASK));
    public static final long INTERVAL_MASK = ~TIMESTAMP_MASK;

    // from Yamcs 5.9.0, store the parameter archive data into a separate Column Family with this name
    public static final String CF_NAME = "parameter_archive";

    // how long in the future (compared to mission time) to allow data part of the coverage
    private long coverageEndDelta = 3600_000;

    private ParameterIdDb parameterIdDb;

    private ParameterArchiveStorage storage;

    TimeService timeService;
    private BackFiller backFiller;
    private RealtimeArchiveFiller realtimeFiller;
    YConfiguration realtimeFillerConfig;
    YConfiguration backFillerConfig;
    boolean realtimeFillerEnabled;
    int maxSegmentSize;
    boolean sparseGroups;
    double minimumGroupOverlap;

    AtomicLong coverageEnd = new AtomicLong(TimeEncoding.NEGATIVE_INFINITY);

    final FillerLock fillerLock = new FillerLock();

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("backFiller", OptionType.MAP).withSpec(BackFiller.getSpec());
        spec.addOption("realtimeFiller", OptionType.MAP).withSpec(RealtimeArchiveFiller.getSpec());
        spec.addOption(YarchDatabaseInstance.PART_CONF_KEY, OptionType.STRING).withAliases("partitioningSchema")
                .withChoices("YYYY/DOY", "YYYY/MM", "YYYY", "none");
        spec.addOption("maxSegmentSize", OptionType.INTEGER).withDefault(500);
        spec.addOption("sparseGroups", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("minimumGroupOverlap", OptionType.FLOAT).withDefault(0.5);
        spec.addOption("coverageEndDelta", OptionType.INTEGER).withDefault(60)
                .withDescription("how long in the future in seconds (compared to mission time) "
                        + "to allow data part of the coverage)");

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        timeService = YamcsServer.getTimeService(yamcsInstance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        this.maxSegmentSize = config.getInt("maxSegmentSize");

        if (config.containsKey("realtimeFiller")) {
            realtimeFillerConfig = config.getConfig("realtimeFiller");
            realtimeFillerEnabled = realtimeFillerConfig.getBoolean("enabled", false);
            log.debug("realtimeFillerConfig: {}", realtimeFillerConfig);
        } else {
            realtimeFillerEnabled = false;
        }

        TimePartitionSchema partitioningSchema = ydb.getTimePartitioningSchema(config);
        this.backFillerConfig = config.getConfigOrEmpty("backFiller");
        this.backFiller = new BackFiller(this, backFillerConfig, !realtimeFillerEnabled);

        sparseGroups = config.getBoolean("sparseGroups");
        minimumGroupOverlap = config.getDouble("minimumGroupOverlap");
        coverageEndDelta = config.getLong("coverageEndDelta") * 1000;

        try {
            storage = ydb.getParameterArchiveStorageEngine().getParameterArchiveStorage(ydb, partitioningSchema);
            parameterIdDb = new ParameterIdDb(yamcsInstance, storage, sparseGroups, minimumGroupOverlap);
            coverageEnd.set(getCoverageEnd(maxCoverageEnd()));
        } catch (ParameterArchiveException | UnsupportedOperationException | YarchException | DecodingException e) {
            throw new InitException(e);
        }
    }

    public ParameterIdDb getParameterIdDb() {
        return parameterIdDb;
    }

    public ParameterGroupIdDb getParameterGroupIdDb() {
        return parameterIdDb.getParameterGroupIdDb();
    }

    public void updateCoverageEnd(long t) {
        long t0 = coverageEnd.getAndUpdate(current -> Math.max(current, t));
        if (t != t0 && log.isDebugEnabled()) {
            log.debug("Updated coverageEnd from {} to {}", TimeEncoding.toString(t0), TimeEncoding.toString(t));
        }
    }

    /**
     * Sets the {@link #coverageEnd} to negative infinity
     * <p>
     * This method is used mostly in unit tests
     */
    public void resetCoverageEnd() {
        coverageEnd.set(TimeEncoding.NEGATIVE_INFINITY);
    }

    public void writeToArchive(PGSegment pgs) {
        pgs.consolidate();
        try (var writeBatch = storage.newWriteBatch(pgs.getInterval())) {
            if (writeBatch.version() == 0) {
                writeToBatchVersion0(writeBatch, pgs);
            } else {
                writeToBatch(writeBatch, pgs);
            }
            writeBatch.commit();
        }
    }

    public void writeToArchive(long interval, Collection<PGSegment> pgList) {
        long maxTime = Instant.MIN_INSTANT;

        try (var writeBatch = storage.newWriteBatch(interval)) {
            for (PGSegment pgs : pgList) {
                pgs.consolidate();
                assert (interval == pgs.getInterval());
                if (writeBatch.version() == 0) {
                    writeToBatchVersion0(writeBatch, pgs);
                } else {
                    writeToBatch(writeBatch, pgs);
                }
                maxTime = Math.max(maxTime, pgs.getSegmentEnd());
            }
            writeBatch.commit();
        }
        updateCoverageEnd(maxTime);
    }

    // write data to the archive using the merge operator.
    // first segment has to be written with put, the subsequent ones with merge
    // the merge operator will merge the segments into intervals
    private void writeToBatch(ParchiveWriteBatch writeBatch, PGSegment pgs) {
        log.trace("Writing {}", pgs);
        int pgid = pgs.getParameterGroupId();

        var pgParams = getParameterGroupIdDb().getParameterGroup(pgid);
        IntHashSet orphans = null;

        if (pgs.isFirstInInterval() && pgParams.size() != pgs.numParameters()) {
            // we store here all parameters that are part of this group but not part of the first segment of the
            // interval
            orphans = new IntHashSet(pgParams);
        }

        // write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(parameterIdDb.timeParameterId, pgs.getParameterGroupId(),
                pgs.getInterval(), SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] timeValue = SegmentEncoderDecoder.encode(timeSegment);
        if (pgs.isFirstInInterval()) {
            writeBatch.put(timeKey, timeValue);
        } else {
            writeBatch.merge(timeKey, timeValue);
        }
        // and then the consolidated value segments
        for (var pvs : pgs.pvSegments) {
            log.trace("Writing {}", pvs);

            int parameterId = pvs.pid;
            if (orphans != null) {
                orphans.remove(parameterId);
            }
            if (pvs.numGaps() + pvs.numValues() != timeSegment.size()) {
                String pname = parameterIdDb.getParameterFqnById(parameterId);
                throw new IllegalStateException(
                        "Trying to write to archive an engineering value segment whose number of values ("
                                + pvs.numValues()
                                + ") + number of gaps (" + pvs.numGaps() + ") is different than the time segment ("
                                + timeSegment.size() + ") " + "for parameterId: " + parameterId + "(" + pname
                                + ") and segment: [" + TimeEncoding.toString(timeSegment.getSegmentStart()) + " - "
                                + TimeEncoding.toString(timeSegment.getSegmentEnd()) + "]");
            }
            BaseSegment vs = pvs.getConsolidatedEngValueSegment();
            BaseSegment rvs = pvs.getConsolidatedRawValueSegment();
            BaseSegment pss = pvs.getConsolidatedParmeterStatusSegment();
            SortedIntArray gaps = pvs.getGaps();
            byte[] gapKey = new SegmentKey(parameterId, pgid, pgs.getInterval(), SegmentKey.TYPE_GAPS).encode();

            if (!pgs.isFirstInInterval() && pgs.wasPreviousGap(pvs.pid)) {
                byte[] gapValue = SegmentEncoderDecoder.encodeGaps(0, pgs.segmentIdxInsideInterval);
                writeBatch.merge(gapKey, gapValue);
            }

            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getInterval(),
                    SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = SegmentEncoderDecoder.encode(vs);
            if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                writeBatch.put(engKey, engValue);
            } else {
                writeBatch.merge(engKey, engValue);
            }

            if (STORE_RAW_VALUES && rvs != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgid, pgs.getInterval(), SegmentKey.TYPE_RAW_VALUE)
                        .encode();
                byte[] rawValue = SegmentEncoderDecoder.encode(rvs);
                if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                    writeBatch.put(rawKey, rawValue);
                } else {
                    writeBatch.merge(rawKey, rawValue);
                }
            }

            byte[] pssKey = new SegmentKey(parameterId, pgid, pgs.getInterval(), SegmentKey.TYPE_PARAMETER_STATUS)
                    .encode();
            byte[] pssValue = SegmentEncoderDecoder.encode(pss);
            if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                writeBatch.put(pssKey, pssValue);
            } else {
                writeBatch.merge(pssKey, pssValue);
            }

            if (gaps != null) {
                byte[] gapsValue = SegmentEncoderDecoder.encodeGaps(pgs.segmentIdxInsideInterval, gaps);
                if (pgs.isFirstInInterval()) {
                    writeBatch.put(gapKey, gapsValue);
                } else {
                    writeBatch.merge(gapKey, gapsValue);
                }
            } else if (pgs.isFirstInInterval()) {
                writeBatch.deleteIfExists(gapKey);
            }
        }
        if (orphans != null) {
            // there might have been previously (in the previous fillings) records containing these parameters, we have
            // to remove them
            for (int pid : orphans) {
                // the parameter status is mandatory, so the other segments exist only if the status exists
                var key = new SegmentKey(pid, pgid, pgs.getInterval(),
                        SegmentKey.TYPE_PARAMETER_STATUS);
                writeBatch.deleteIfExists(key.encode());

                key.type = SegmentKey.TYPE_RAW_VALUE;
                writeBatch.deleteIfExists(key.encode());

                key.type = SegmentKey.TYPE_ENG_VALUE;
                writeBatch.deleteIfExists(key.encode());

                key.type = SegmentKey.TYPE_GAPS;
                writeBatch.deleteIfExists(key.encode());
            }
        }
        if (!pgs.isFirstInInterval() && pgs.currentFullGaps != null && pgs.currentFullGaps.size() > 0) {
            // insert gap records for parameters appearing in the interval in the previous segments but not in this one
            for (int pid : pgs.currentFullGaps) {

                byte[] rawKey = new SegmentKey(pid, pgid, pgs.getInterval(), SegmentKey.TYPE_GAPS).encode();
                byte[] rawValue = SegmentEncoderDecoder.encodeGaps(pgs.segmentIdxInsideInterval,
                        pgs.segmentIdxInsideInterval + pgs.size());
                writeBatch.merge(rawKey, rawValue);
            }
        }
    }

    // writes to the archive without using the rocksdb merge operator (which merges segments together into intervals).
    // The segment start (instead of the interval start) is part of the key which means that we need to remove old
    // data as it may have a different segment start resulting into a different key.
    //
    private void writeToBatchVersion0(ParchiveWriteBatch writeBatch, PGSegment pgs) {
        removeOldOverlappingSegments(writeBatch, pgs);

        // write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(parameterIdDb.timeParameterId, pgs.getParameterGroupId(),
                pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encodeV0();
        byte[] timeValue = SegmentEncoderDecoder.encode(timeSegment);
        writeBatch.put(timeKey, timeValue);

        // and then the consolidated value segments

        for (var pvs : pgs.pvSegments) {
            int parameterId = pvs.pid;

            if (pvs.numGaps() + pvs.numValues() != timeSegment.size()) {
                String pname = parameterIdDb.getParameterFqnById(parameterId);
                throw new IllegalStateException(
                        "Trying to write to archive an engineering value segment whose number of values ("
                                + pvs.numValues()
                                + ") + number of gaps (" + pvs.numGaps() + ") is different than the time segment ("
                                + timeSegment.size() + ") " + "for parameterId: " + parameterId + "(" + pname
                                + ") and segment: [" + TimeEncoding.toString(timeSegment.getSegmentStart()) + " - "
                                + TimeEncoding.toString(timeSegment.getSegmentEnd()) + "]");
            }
            BaseSegment vs = pvs.getConsolidatedEngValueSegment();
            BaseSegment rvs = pvs.getConsolidatedRawValueSegment();
            BaseSegment pss = pvs.getConsolidatedParmeterStatusSegment();
            SortedIntArray gaps = pvs.getGaps();

            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                    SegmentKey.TYPE_ENG_VALUE).encodeV0();
            byte[] engValue = SegmentEncoderDecoder.encode(vs);
            writeBatch.put(engKey, engValue);

            if (STORE_RAW_VALUES && rvs != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_RAW_VALUE).encodeV0();
                byte[] rawValue = SegmentEncoderDecoder.encode(rvs);
                writeBatch.put(rawKey, rawValue);
            }

            byte[] pssKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                    SegmentKey.TYPE_PARAMETER_STATUS).encodeV0();
            byte[] pssValue = SegmentEncoderDecoder.encode(pss);
            writeBatch.put(pssKey, pssValue);

            if (gaps != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_GAPS).encodeV0();
                byte[] rawValue = SegmentEncoderDecoder.encodeGaps(pgs.getSegmentIdxInsideInterval(), gaps);
                writeBatch.put(rawKey, rawValue);
            }
        }
    }

    private void removeOldOverlappingSegments(ParchiveWriteBatch writeBatch, PGSegment pgs) {
        long segStart = pgs.getSegmentStart();
        long segEnd = pgs.getSegmentEnd();
        int pgid = pgs.getParameterGroupId();

        byte[] timeKeyStart = new SegmentKey(parameterIdDb.timeParameterId, pgid, segStart, (byte) 0).encodeV0();
        byte[] timeKeyEnd = new SegmentKey(parameterIdDb.timeParameterId, pgid, segEnd, Byte.MAX_VALUE)
                .encodeV0();
        writeBatch.deleteRange(timeKeyStart, timeKeyEnd);

        for (var pvs : pgs.pvSegments) {
            int pid = pvs.pid;

            byte[] paraKeyStart = new SegmentKey(pid, pgid, segStart, (byte) 0).encodeV0();
            byte[] paraKeyEnd = new SegmentKey(pid, pgid, segEnd, Byte.MAX_VALUE).encodeV0();
            writeBatch.deleteRange(paraKeyStart, paraKeyEnd);
        }
    }

    /**
     * Rebuild the parameter archive between start and stop. The times will be adjusted to build full intervals.
     * <p>
     * Both start and stop can be left unspecified (by setting them to {@link TimeEncoding#INVALID_INSTANT}) to start
     * from the beginning and/or go to the end of the archive.
     * <p>
     * If the realtime parameter filler is enabled, the end will be set by default to the end of the previous interval
     * compared to the interval of the current mission time.
     */
    public Future<?> reprocess(long start, long stop) {
        if (backFiller == null) {
            throw new ConfigurationException("backFilling is not enabled");
        }
        if (start == TimeEncoding.INVALID_INSTANT) {
            start = TimeEncoding.NEGATIVE_INFINITY;
        }

        if (stop == TimeEncoding.INVALID_INSTANT) {
            if (realtimeFiller == null) {
                stop = TimeEncoding.POSITIVE_INFINITY;
            } else {
                stop = ParameterArchive.getIntervalStart(timeService.getMissionTime()) - 1;
            }
        }
        log.debug("Scheduling a reprocess for interval [{} - {}]",
                start == TimeEncoding.INVALID_INSTANT ? "not_specified" : TimeEncoding.toString(start),
                stop == TimeEncoding.INVALID_INSTANT ? "not_specified" : TimeEncoding.toString(stop));
        return backFiller.scheduleFillingTask(start, stop);
    }

    @Override
    protected void doStart() {
        backFiller.scheduleAutoFillers();

        if (realtimeFillerEnabled) {
            realtimeFiller = new RealtimeArchiveFiller(this, realtimeFillerConfig);
            realtimeFiller.start();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        log.debug("Stopping ParameterArchive service for instance {}", yamcsInstance);
        try {

            backFiller.shutDown();

            if (realtimeFiller != null) {
                realtimeFiller.shutDown();
            }
        } catch (Exception e) {
            log.error("Error stopping realtime filler", e);
            notifyFailed(e);
            return;
        }
        notifyStopped();
    }

    /**
     * Prints all the keys of the archive (only for the RocksDB storage)
     */
    public void printKeys(PrintStream out) throws DecodingException, RocksDBException, IOException {
        rdbStorage().printKeys(out);
    }

    /**
     * Delete all partitions that overlap with [start, stop) segment.
     * 
     * @param start
     * @param stop
     * @throws RocksDBException
     * @return all the partitions removed
     */
    public List<Partition> deletePartitions(long start, long stop) throws RocksDBException {
        // List<Partition> parts = getPartitions(start, stop, true);
        throw new UnsupportedOperationException("operation not supported");
    }

    /**
     * Remove all the data and metadata related to the parameter archive and initialize a new database
     * <p>
     * The fillers are stopped during the operation and the data is removed by {@link ParameterArchiveStorage#purge()}.
     * 
     * @throws InterruptedException
     *             if interrupted while waiting for the fillers to stop
     */
    public void purge() throws InterruptedException {
        log.info("Purging the parameter archive");

        log.debug("Shutting down the back filler");
        backFiller.shutDown();

        if (realtimeFiller != null) {
            log.debug("Shutting down the realtime filler");
            realtimeFiller.shutDown();
        }

        storage.purge();
        parameterIdDb = new ParameterIdDb(yamcsInstance, storage, sparseGroups, minimumGroupOverlap);

        log.debug("Starting the back filler");
        backFiller = new BackFiller(this, backFillerConfig, !realtimeFillerEnabled);
        backFiller.scheduleAutoFillers();

        if (realtimeFillerEnabled) {
            log.debug("Starting the realtime filler");
            realtimeFiller = new RealtimeArchiveFiller(this, realtimeFillerConfig);
            realtimeFiller.start();
        }
    }

    /**
     * Used by the realtime filler to read a PGSegment for an interval in order to add data to it
     * <p>
     * returns null if no data for the given interval is found
     * <p>
     * This is only used starting with version 1 (when the merge operator has been introduced). The behaviour before
     * could be incorrect.
     */
    PGSegment readPGsegment(ParameterGroup pg, long intervalStart) {
        if (storage.getVersion(intervalStart) == 0) {
            return null;
        }
        // the first pair is the time segment, then the parameters of the group, all in the same interval
        int timePid = parameterIdDb.timeParameterId;
        int n = pg.pids.size();
        int[] pids = new int[n + 1];
        int[] pgids = new int[n + 1];
        pids[0] = timePid;
        pgids[0] = pg.id;
        for (int i = 0; i < n; i++) {
            pids[i + 1] = pg.pids.get(i);
            pgids[i + 1] = pg.id;
        }
        var req = new ScanRequest(timePid, pids, pgids, intervalStart, intervalStart, true, true, true, true);

        SortedTimeSegment timeSegment = null;
        List<ParameterValueSegment> pvsList = new ArrayList<>();
        try (var scanner = storage.scan(req)) {
            for (; scanner.isValid(); scanner.next()) {
                var rec = scanner.value();
                if (rec.pairIndex() == 0) {
                    timeSegment = (SortedTimeSegment) SegmentEncoderDecoder.decode(rec.timeSegment(), intervalStart);
                    continue;
                }
                if (timeSegment == null || rec.parameterStatusSegment() == null) {
                    // parameter status is mandatory so if it does not exist it means this parameter is not part of
                    // the interval
                    continue;
                }
                var parameterStatusSegment = (ParameterStatusSegment) SegmentEncoderDecoder
                        .decode(rec.parameterStatusSegment(), intervalStart);
                ValueSegment engValueSegment = rec.engValueSegment() == null ? null
                        : (ValueSegment) SegmentEncoderDecoder.decode(rec.engValueSegment(), intervalStart);
                ValueSegment rawValueSegment = rec.rawValueSegment() == null ? null
                        : (ValueSegment) SegmentEncoderDecoder.decode(rec.rawValueSegment(), intervalStart);
                SortedIntArray gaps = rec.gaps() == null ? null : SegmentEncoderDecoder.decodeGaps(rec.gaps());

                pvsList.add(new ParameterValueSegment(rec.pid(), timeSegment, engValueSegment,
                        rawValueSegment, parameterStatusSegment, gaps));
            }
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
        if (timeSegment == null) {
            return null;
        }
        return new PGSegment(pg.id, timeSegment, pvsList);
    }

    /**
     * Returns the partition containing the instant (only for the RocksDB storage)
     */
    Partition getPartitions(long instant) {
        return rdbStorage().getPartition(instant);
    }

    public long coverageEnd() {
        return coverageEnd.get();
    }

    /**
     * returns the interval (instant) where this instant could fit.
     * 
     * @param instant
     * @return
     */
    public static long getIntervalStart(long instant) {
        return getInterval(instant);
    }

    public static long getInterval(long instant) {
        return instant & INTERVAL_MASK;
    }

    /**
     * returns the end of the interval where the instant fits
     * 
     * @param instant
     * @return
     */
    public static long getIntervalEnd(long instant) {
        return instant | TIMESTAMP_MASK;
    }

    /**
     * duration in milliseconds of one segment
     * 
     * @return
     */
    public static long getIntervalDuration() {
        return TIMESTAMP_MASK + 1l;
    }

    /**
     * Returns the tablespace storing the archive (only for the RocksDB storage)
     */
    public Tablespace getTablespace() {
        return rdbStorage().getTablespace();
    }

    public ParameterArchiveStorage getStorage() {
        return storage;
    }

    private RdbParameterArchiveStorage rdbStorage() {
        if (storage instanceof RdbParameterArchiveStorage rdbStorage) {
            return rdbStorage;
        }
        throw new UnsupportedOperationException("The parameter archive is not stored in RocksDB");
    }

    int getMaxSegmentSize() {
        return maxSegmentSize;
    }

    public RealtimeArchiveFiller getRealtimeFiller() {
        return realtimeFiller;
    }

    public BackFiller getBackFiller() {
        return backFiller;
    }

    public void compact() {
        storage.compact();
    }

    public long maxCoverageEnd() {
        return timeService.getMissionTime() + coverageEndDelta;
    }

    /**
     * Computes the coverage end as the greatest timestamp of a parameter in the archive, smaller than now
     * <p>
     * In order to find that, it iterates over the time segments in descending order
     * 
     * @throws DecodingException
     */
    public long getCoverageEnd(long now) throws DecodingException {
        long covEnd = TimeEncoding.NEGATIVE_INFINITY;
        log.debug("Computing coverage end as greatest timestamp of a parameter smaller than {}",
                TimeEncoding.toString(now));

        var pgDb = getParameterGroupIdDb();
        if (pgDb.numGroups() == 0) {
            log.debug("No parameter group, coverageEnd is {}", TimeEncoding.toString(covEnd));
            return covEnd;
        }
        // scan the time segments of all groups, descending
        int timePid = parameterIdDb.timeParameterId;
        IntArray pgids = new IntArray();
        pgDb.groupIterato().forEachRemaining(pg -> pgids.add(pg.id));
        int[] pids = new int[pgids.size()];
        Arrays.fill(pids, timePid);
        var req = new ScanRequest(timePid, pids, pgids.toArray(), TimeEncoding.MIN_INSTANT, getIntervalEnd(now), false,
                false, false, false);

        // the groups for which the last time segment containing a time smaller than now has been found
        IntHashSet done = new IntHashSet();
        try (var scanner = storage.scan(req)) {
            for (; scanner.isValid() && done.size() < pids.length; scanner.next()) {
                var rec = scanner.value();
                if (getIntervalEnd(rec.segmentStart()) <= covEnd) {
                    // the time segments come in descending order, this one and all the next ones end before covEnd
                    break;
                }
                if (done.contains(rec.pgid())) {
                    continue;
                }
                var sts = (SortedTimeSegment) SegmentEncoderDecoder.decode(rec.timeSegment(), rec.segmentStart());
                int pos = sts.search(now);
                if (pos < 0) {
                    pos = -pos - 1;
                    if (pos == 0) {
                        // all the times are after now, look in the previous segment of the group
                        continue;
                    }
                    pos = pos - 1;
                }
                covEnd = Long.max(covEnd, sts.getTime(pos));
                done.add(rec.pgid());
            }
        }
        log.debug("Found coverageEnd {}", TimeEncoding.toString(covEnd));
        return covEnd;
    }

    public FillerLock getFillerLock() {
        return fillerLock;
    }

    public static class Partition extends TimeInterval {
        final String partitionDir;
        final String cfName;

        final int version;

        Partition(String cfName, int version) {
            super(TimeEncoding.NEGATIVE_INFINITY, TimeEncoding.POSITIVE_INFINITY);

            this.partitionDir = null;
            this.cfName = cfName;
            this.version = version;
        }

        Partition(long start, long end, String dir, String cfName, int version) {
            super(start, end);

            this.partitionDir = dir;
            this.cfName = cfName;
            this.version = version;
        }

        @Override
        public String toString() {
            return "partition: " + partitionDir + "[" + TimeEncoding.toString(getStart()) + " - "
                    + TimeEncoding.toString(getEnd()) + "], version: " + version;
        }

        public String getPartitionDir() {
            return partitionDir;
        }
    }

}
