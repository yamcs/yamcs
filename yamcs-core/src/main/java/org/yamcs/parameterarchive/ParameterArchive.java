package org.yamcs.parameterarchive;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameterarchive.ParameterGroupIdDb.ParameterGroup;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntHashSet;
import org.yamcs.utils.PartitionedTimeInterval;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TimePartitionInfo;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TimeBasedPartition;

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

    private Tablespace tablespace;

    TimePartitionSchema partitioningSchema;

    // the tablespace record holding partition information
    TablespaceRecord pinfoTablespaceRecord;

    private PartitionedTimeInterval<Partition> partitions = new PartitionedTimeInterval<>();

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
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        this.maxSegmentSize = config.getInt("maxSegmentSize");

        if (config.containsKey("realtimeFiller")) {
            realtimeFillerConfig = config.getConfig("realtimeFiller");
            realtimeFillerEnabled = realtimeFillerConfig.getBoolean("enabled", false);
            log.debug("realtimeFillerConfig: {}", realtimeFillerConfig);
        } else {
            realtimeFillerEnabled = false;
        }

        partitioningSchema = ydb.getTimePartitioningSchema(config);
        this.backFillerConfig = config.getConfigOrEmpty("backFiller");
        this.backFiller = new BackFiller(this, backFillerConfig, !realtimeFillerEnabled);

        sparseGroups = config.getBoolean("sparseGroups");
        minimumGroupOverlap = config.getDouble("minimumGroupOverlap");
        coverageEndDelta = config.getLong("coverageEndDelta") * 1000;

        try {
            TablespaceRecord.Type trType = TablespaceRecord.Type.PARCHIVE_PINFO;
            List<TablespaceRecord> trl = tablespace.filter(trType, yamcsInstance, trb -> true);
            if (trl.size() > 1) {
                throw new DatabaseCorruptionException(
                        "More than one tablespace record of type " + trType.name() + " for instance " + yamcsInstance);
            }
            parameterIdDb = new ParameterIdDb(yamcsInstance, tablespace, sparseGroups, minimumGroupOverlap);

            if (trl.isEmpty()) { // new database
                initializeDb();
            } else {// existing database
                pinfoTablespaceRecord = trl.get(0);

                if (pinfoTablespaceRecord.hasPartitioningSchema()) {
                    partitioningSchema = TimePartitionSchema.getInstance(pinfoTablespaceRecord.getPartitioningSchema());
                }
                readPartitions();
                if (partitions.isEmpty() && partitioningSchema == null) {
                    partitions.insert(new Partition(
                            pinfoTablespaceRecord.hasParchiveCf() ? pinfoTablespaceRecord.getParchiveCf() : null,
                            pinfoTablespaceRecord.getParchiveVersion()));
                }

                coverageEnd.set(getCoverageEnd(maxCoverageEnd()));
            }

        } catch (RocksDBException | IOException | DecodingException e) {
            throw new InitException(e);
        }
    }

    /**
     * Called in the init function to initialize the database
     */
    private void initializeDb() throws RocksDBException {
        log.debug("initializing db");
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_PINFO);
        if (partitioningSchema != null) {
            trb.setPartitioningSchema(partitioningSchema.getName());
        } else {
            partitions.insert(new Partition(CF_NAME, VERSION));
        }
        trb.setParchiveCf(CF_NAME);
        trb.setParchiveVersion(VERSION);
        pinfoTablespaceRecord = tablespace.createMetadataRecord(yamcsInstance, trb);
    }

    public TimePartitionSchema getPartitioningSchema() {
        return partitioningSchema;
    }

    /**
     * Called in the init function to read the existing partitions
     */
    private void readPartitions() throws IOException, RocksDBException {
        YRDB db = tablespace.getRdb();
        byte[] range = new byte[TBS_INDEX_SIZE];
        ByteArrayUtils.encodeInt(pinfoTablespaceRecord.getTbsIndex(), range, 0);

        try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, range)) {
            while (it.isValid()) {
                TimeBasedPartition tbp = TimeBasedPartition.parseFrom(it.value());
                String cfName = tbp.hasPartitionCf() ? tbp.getPartitionCf() : null;

                Partition p = new Partition(tbp.getPartitionStart(), tbp.getPartitionEnd(), tbp.getPartitionDir(),
                        cfName, tbp.getParchiveVersion());
                if (partitions.insert(p, 0) == null) {
                    throw new DatabaseCorruptionException("Partition " + p + " overlaps with existing partitions");
                }
                it.next();
            }
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

    public void writeToArchive(PGSegment pgs) throws RocksDBException, IOException {
        pgs.consolidate();
        Partition p = createAndGetPartition(pgs.getInterval());
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);
        ColumnFamilyHandle cfh = cfh(rdb, p);

        try (WriteBatch writeBatch = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            if (p.version == 0) {
                writeToBatchVersion0(cfh, writeBatch, pgs);
            } else {
                writeToBatch(rdb, cfh, writeBatch, pgs);
            }
            rdb.write(wo, writeBatch);
        }
    }

    public void writeToArchive(long interval, Collection<PGSegment> pgList) throws RocksDBException, IOException {
        Partition p = createAndGetPartition(interval);
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        ColumnFamilyHandle cfh = cfh(rdb, p);
        long maxTime = Instant.MIN_INSTANT;

        try (WriteBatch writeBatch = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            for (PGSegment pgs : pgList) {
                pgs.consolidate();
                assert (interval == pgs.getInterval());
                if (p.version == 0) {
                    writeToBatchVersion0(cfh, writeBatch, pgs);
                } else {
                    writeToBatch(rdb, cfh, writeBatch, pgs);
                }
                maxTime = Math.max(maxTime, pgs.getSegmentEnd());
            }
            rdb.write(wo, writeBatch);
        }
        updateCoverageEnd(maxTime);
    }

    // write data to the archive using the merge operator.
    // first segment has to be written with put, the subsequent ones with merge
    // the merge operator will merge the segments into intervals
    private void writeToBatch(YRDB rdb, ColumnFamilyHandle cfh, WriteBatch writeBatch, PGSegment pgs)
            throws RocksDBException {
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
            writeBatch.put(cfh, timeKey, timeValue);
        } else {
            writeBatch.merge(cfh, timeKey, timeValue);
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
                writeBatch.merge(cfh, gapKey, gapValue);
            }

            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getInterval(),
                    SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = SegmentEncoderDecoder.encode(vs);
            if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                writeBatch.put(cfh, engKey, engValue);
            } else {
                writeBatch.merge(cfh, engKey, engValue);
            }

            if (STORE_RAW_VALUES && rvs != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgid, pgs.getInterval(), SegmentKey.TYPE_RAW_VALUE)
                        .encode();
                byte[] rawValue = SegmentEncoderDecoder.encode(rvs);
                if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                    writeBatch.put(cfh, rawKey, rawValue);
                } else {
                    writeBatch.merge(cfh, rawKey, rawValue);
                }
            }

            byte[] pssKey = new SegmentKey(parameterId, pgid, pgs.getInterval(), SegmentKey.TYPE_PARAMETER_STATUS)
                    .encode();
            byte[] pssValue = SegmentEncoderDecoder.encode(pss);
            if (pgs.isFirstInInterval() || pgs.wasPreviousGap(pvs.pid)) {
                writeBatch.put(cfh, pssKey, pssValue);
            } else {
                writeBatch.merge(cfh, pssKey, pssValue);
            }

            if (gaps != null) {
                byte[] gapsValue = SegmentEncoderDecoder.encodeGaps(pgs.segmentIdxInsideInterval, gaps);
                if (pgs.isFirstInInterval()) {
                    writeBatch.put(cfh, gapKey, gapsValue);
                } else {
                    writeBatch.merge(cfh, gapKey, gapsValue);
                }
            } else if (pgs.isFirstInInterval()) {
                if (rdb.get(cfh, gapKey) != null) {
                    writeBatch.delete(cfh, gapKey);
                }
            }
        }
        if (orphans != null) {
            // there might have been previously (in the previous fillings) records containing these parameters, we have
            // to remove them
            for (int pid : orphans) {
                var key = new SegmentKey(pid, pgid, pgs.getInterval(),
                        SegmentKey.TYPE_PARAMETER_STATUS);
                byte[] statusKey = key.encode();
                if (rdb.get(cfh, statusKey) != null) {
                    writeBatch.delete(cfh, statusKey);

                    key.type = SegmentKey.TYPE_RAW_VALUE;
                    writeBatch.delete(cfh, key.encode());

                    key.type = SegmentKey.TYPE_ENG_VALUE;
                    writeBatch.delete(cfh, key.encode());

                    key.type = SegmentKey.TYPE_GAPS;
                    writeBatch.delete(cfh, key.encode());
                }
            }
        }
        if (!pgs.isFirstInInterval() && pgs.currentFullGaps != null && pgs.currentFullGaps.size() > 0) {
            // insert gap records for parameters appearing in the interval in the previous segments but not in this one
            for (int pid : pgs.currentFullGaps) {

                byte[] rawKey = new SegmentKey(pid, pgid, pgs.getInterval(), SegmentKey.TYPE_GAPS).encode();
                byte[] rawValue = SegmentEncoderDecoder.encodeGaps(pgs.segmentIdxInsideInterval,
                        pgs.segmentIdxInsideInterval + pgs.size());
                writeBatch.merge(cfh, rawKey, rawValue);
            }
        }
    }

    // writes to the archive without using the rocksdb merge operator (which merges segments together into intervals).
    // The segment start (instead of the interval start) is part of the key which means that we need to remove old
    // data as it may have a different segment start resulting into a different key.
    //
    private void writeToBatchVersion0(ColumnFamilyHandle cfh, WriteBatch writeBatch, PGSegment pgs)
            throws RocksDBException {
        removeOldOverlappingSegments(cfh, writeBatch, pgs);

        // write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(parameterIdDb.timeParameterId, pgs.getParameterGroupId(),
                pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encodeV0();
        byte[] timeValue = SegmentEncoderDecoder.encode(timeSegment);
        writeBatch.put(cfh, timeKey, timeValue);

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
            writeBatch.put(cfh, engKey, engValue);

            if (STORE_RAW_VALUES && rvs != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_RAW_VALUE).encodeV0();
                byte[] rawValue = SegmentEncoderDecoder.encode(rvs);
                writeBatch.put(cfh, rawKey, rawValue);
            }

            byte[] pssKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                    SegmentKey.TYPE_PARAMETER_STATUS).encodeV0();
            byte[] pssValue = SegmentEncoderDecoder.encode(pss);
            writeBatch.put(cfh, pssKey, pssValue);

            if (gaps != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_GAPS).encodeV0();
                byte[] rawValue = SegmentEncoderDecoder.encodeGaps(pgs.getSegmentIdxInsideInterval(), gaps);
                writeBatch.put(cfh, rawKey, rawValue);
            }
        }
    }

    private void removeOldOverlappingSegments(ColumnFamilyHandle cfh, WriteBatch writeBatch, PGSegment pgs)
            throws RocksDBException {
        long segStart = pgs.getSegmentStart();
        long segEnd = pgs.getSegmentEnd();
        int pgid = pgs.getParameterGroupId();

        byte[] timeKeyStart = new SegmentKey(parameterIdDb.timeParameterId, pgid, segStart, (byte) 0).encodeV0();
        byte[] timeKeyEnd = new SegmentKey(parameterIdDb.timeParameterId, pgid, segEnd, Byte.MAX_VALUE)
                .encodeV0();
        deleteRange(cfh, writeBatch, timeKeyStart, timeKeyEnd);

        for (var pvs : pgs.pvSegments) {
            int pid = pvs.pid;

            byte[] paraKeyStart = new SegmentKey(pid, pgid, segStart, (byte) 0).encodeV0();
            byte[] paraKeyEnd = new SegmentKey(pid, pgid, segEnd, Byte.MAX_VALUE).encodeV0();
            deleteRange(cfh, writeBatch, paraKeyStart, paraKeyEnd);
        }
    }

    private void deleteRange(ColumnFamilyHandle cfh, WriteBatch writeBatch, byte[] start, byte[] end)
            throws RocksDBException {
        if (cfh != null) {
            writeBatch.deleteRange(cfh, start, end);
        } else {
            writeBatch.deleteRange(start, end);
        }
    }

    /**
     * get partition for interval, creating it if it doesn't exist
     * 
     * @param intervalStart
     * @throws RocksDBException
     */
    private Partition createAndGetPartition(long intervalStart) throws RocksDBException {
        synchronized (partitions) {
            Partition p = partitions.getFit(intervalStart);
            if (p == null) {
                TimePartitionInfo pinfo = partitioningSchema.getPartitionInfo(intervalStart);
                p = new Partition(pinfo.getStart(), pinfo.getEnd(), pinfo.getDir(), CF_NAME, VERSION);
                p = partitions.insert(p, 60000L);
                assert p != null;
                TimeBasedPartition tbp = TimeBasedPartition.newBuilder().setPartitionDir(p.partitionDir)
                        .setPartitionStart(p.getStart()).setPartitionEnd(p.getEnd())
                        .setPartitionCf(p.cfName)
                        .setParchiveVersion(p.version)
                        .build();
                byte[] key = new byte[TBS_INDEX_SIZE + 8];
                ByteArrayUtils.encodeInt(pinfoTablespaceRecord.getTbsIndex(), key, 0);
                ByteArrayUtils.encodeLong(pinfo.getStart(), key, TBS_INDEX_SIZE);
                tablespace.putData(key, tbp.toByteArray());
            }
            return p;
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

    /**
     * a copy of the partitions from start to stop inclusive
     * 
     * @param start
     * @param stop
     * @return a sorted list of partitions
     */
    public List<Partition> getPartitions(long start, long stop, boolean ascending) {
        List<Partition> r = new ArrayList<>();
        Iterator<Partition> it;
        if (ascending) {
            it = partitions.overlappingIterator(new TimeInterval(start, stop));
        } else {
            it = partitions.overlappingReverseIterator(new TimeInterval(start, stop));
        }
        while (it.hasNext()) {
            r.add(it.next());
        }
        return r;
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

    public void printKeys(PrintStream out) throws DecodingException, RocksDBException, IOException {
        out.println("pid\t pgid\t type\tSegmentStart\tcount\tsize\tstype");
        for (Partition p : partitions) {
            try (RocksIterator it = getIterator(p)) {
                it.seekToFirst();
                while (it.isValid()) {
                    SegmentKey key = p.version == 0 ? SegmentKey.decodeV0(it.key()) : SegmentKey.decode(it.key());
                    byte[] v = it.value();
                    BaseSegment s;
                    s = SegmentEncoderDecoder.decode(it.value(), key.segmentStart);
                    out.println(key.parameterId + "\t " + key.parameterGroupId + "\t " + key.type + "\t"
                            + TimeEncoding.toString(key.segmentStart) + "\t" + s.size() + "\t" + v.length + "\t"
                            + s.getClass().getSimpleName());
                    it.next();
                }
            }
        }
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
     * Prior to Yamcs 5.9.0 the Parameter Archive was stored on the default RocksDB column family. After the purge
     * operation, the parameter archive will be moved to its own column family
     * <p>
     * If the parameter archive is stored in the default column family this operation will remove all the records.
     * <p>
     * If the parameter archive is stored into its own column family this operation will simply drop that column family
     * (for all time based partitions)
     * 
     * @throws RocksDBException
     * @throws InterruptedException
     * @throws IOException
     */
    public void purge() throws RocksDBException, InterruptedException, IOException {
        log.info("Purging the parameter archive");

        log.debug("Shutting down the back filler");
        backFiller.shutDown();

        if (realtimeFiller != null) {
            log.debug("Shutting down the realtime filler");
            realtimeFiller.shutDown();
        }

        var allPids = parameterIdDb.getAllPids();
        int pgTbsIndex = parameterIdDb.getParameterGroupIdDb().tbsIndex;

        for (var p : partitions) {
            log.debug("purging partition {}", p);
            YRDB rdb = tablespace.getRdb(p.partitionDir, false);
            if (p.cfName == null || YRDB.DEFAULT_CF.equals(p.cfName)) {
                WriteBatch wb = new WriteBatch();
                for (int i = 0; i < allPids.size(); i++) {
                    var tbsIndex = allPids.get(i);
                    wb.deleteRange(dbKey(tbsIndex), dbKey(tbsIndex + 1));
                }
                try (WriteOptions wo = new WriteOptions()) {
                    rdb.write(wo, wb);
                }
            } else {
                rdb.dropColumnFamily(p.cfName);
            }
        }
        partitions = new PartitionedTimeInterval<>();

        log.debug("removing metadata records related to main parameter archive data");
        // data has been removed in the partition loop above
        tablespace.removeMetadataRecords(TablespaceRecord.Type.PARCHIVE_DATA);

        log.debug("removing parameter groups and related metadata");
        tablespace.removeTbsIndex(TablespaceRecord.Type.PARCHIVE_PGID2PG, pgTbsIndex);

        log.debug("removing partitions and related metadata");
        tablespace.removeTbsIndex(TablespaceRecord.Type.PARCHIVE_PINFO, pinfoTablespaceRecord.getTbsIndex());

        log.debug("removing metadata storing aggregate/array composition");
        // there is no data of this type stored
        tablespace.removeMetadataRecords(TablespaceRecord.Type.PARCHIVE_AGGARR_INFO);

        parameterIdDb = new ParameterIdDb(yamcsInstance, tablespace, sparseGroups, minimumGroupOverlap);
        initializeDb();

        log.debug("Starting the back filler");
        backFiller = new BackFiller(this, backFillerConfig, !realtimeFillerEnabled);
        backFiller.scheduleAutoFillers();

        if (realtimeFillerEnabled) {
            log.debug("Starting the realtime filler");
            realtimeFiller = new RealtimeArchiveFiller(this, realtimeFillerConfig);
            realtimeFiller.start();
        }
    }

    public RocksIterator getIterator(Partition p) throws RocksDBException, IOException {
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        return rdb.newIterator(cfh(rdb, p));
    }

    public RdbIteratorWithOptions getIteratorWithOptions(Partition p) throws RocksDBException, IOException {
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);
        var snapshot = rdb.getSnapshot();
        ReadOptions opts = new ReadOptions();
        opts.setSnapshot(snapshot);

        var it = rdb.newIterator(cfh(rdb, p), opts);
        return new RdbIteratorWithOptions(it, opts);
    }

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart, int parameterGroupId)
            throws RocksDBException, IOException {

        var sk = new SegmentKey(parameterIdDb.timeParameterId, parameterGroupId, segmentStart,
                SegmentKey.TYPE_ENG_VALUE);
        byte[] timeKey = p.version == 0 ? sk.encodeV0() : sk.encode();
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        var cfh = cfh(rdb, p);

        byte[] tv = rdb.get(cfh, timeKey);

        if (tv == null) {
            return null;
        }
        try {
            return (SortedTimeSegment) SegmentEncoderDecoder.decode(tv, segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart, int parameterGroupId, ReadOptions opts)
            throws RocksDBException, IOException {

        var sk = new SegmentKey(parameterIdDb.timeParameterId, parameterGroupId, segmentStart,
                SegmentKey.TYPE_ENG_VALUE);
        byte[] timeKey = p.version == 0 ? sk.encodeV0() : sk.encode();
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        var cfh = cfh(rdb, p);

        byte[] tv = rdb.get(cfh, opts, timeKey);

        if (tv == null) {
            return null;
        }
        try {
            return (SortedTimeSegment) SegmentEncoderDecoder.decode(tv, segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
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
    PGSegment readPGsegment(ParameterGroup pg, long intervalStart) throws IOException, RocksDBException {
        var partition = createAndGetPartition(intervalStart);
        if (partition.version == 0) {
            return null;
        }

        var timeSegment = getTimeSegment(partition, intervalStart, pg.id);

        if (timeSegment == null) {
            return null;
        }
        YRDB rdb = tablespace.getRdb(partition.partitionDir, false);
        var cfh = cfh(rdb, partition);

        try (var snapshot = rdb.getSnapshot();
                ReadOptions opts = new ReadOptions()) {
            opts.setSnapshot(snapshot);
            List<ParameterValueSegment> pvsList = new ArrayList<>();
            for (int pid : pg.pids) {
                ValueSegment engValueSegment = null;
                ValueSegment rawValueSegment = null;
                ParameterStatusSegment parameterStatusSegment = null;
                SortedIntArray gaps = null;
                try {

                    var sk = new SegmentKey(pid, pg.id, intervalStart, SegmentKey.TYPE_PARAMETER_STATUS);
                    byte[] key = partition.version == 0 ? sk.encodeV0() : sk.encode();
                    byte[] value = rdb.get(cfh, opts, key);
                    if (value == null) {
                        // parameter status is mandatory so if it does not exist it means this parameter is not part of
                        // the interval
                        continue;
                    }
                    parameterStatusSegment = (ParameterStatusSegment) SegmentEncoderDecoder.decode(value,
                            intervalStart);

                    sk = new SegmentKey(pid, pg.id, intervalStart, SegmentKey.TYPE_ENG_VALUE);
                    key = partition.version == 0 ? sk.encodeV0() : sk.encode();
                    value = rdb.get(cfh, opts, key);

                    if (value != null) {
                        engValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(value, intervalStart);
                    }

                    sk = new SegmentKey(pid, pg.id, intervalStart, SegmentKey.TYPE_RAW_VALUE);
                    key = partition.version == 0 ? sk.encodeV0() : sk.encode();
                    value = rdb.get(cfh, opts, key);
                    if (value != null) {
                        rawValueSegment = (ValueSegment) SegmentEncoderDecoder.decode(value, intervalStart);
                    }

                    sk = new SegmentKey(pid, pg.id, intervalStart, SegmentKey.TYPE_GAPS);
                    key = partition.version == 0 ? sk.encodeV0() : sk.encode();
                    value = rdb.get(cfh, opts, key);
                    if (value != null) {
                        gaps = SegmentEncoderDecoder.decodeGaps(value);
                    }

                    ParameterValueSegment pvs = new ParameterValueSegment(pid, timeSegment, engValueSegment,
                            rawValueSegment, parameterStatusSegment, gaps);
                    pvsList.add(pvs);

                } catch (DecodingException e) {
                    throw new DatabaseCorruptionException(e);
                }

            }

            return new PGSegment(pg.id, timeSegment, pvsList);
        }
    }

    Partition getPartitions(long instant) {
        synchronized (partitions) {
            return partitions.getFit(instant);
        }
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

    public Tablespace getTablespace() {
        return tablespace;
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

    // this method is never used
    // we leave it in if we want to experiment again with manual compaction
    public void disableAutoCompaction(long start, long stop) {
        try {
            var interval = new TimeInterval(start, stop);
            log.debug("Disabling auto-compaction on partitions overlapping with {}", interval.toStringEncoded());
            var it = partitions.overlappingIterator(interval);
            while (it.hasNext()) {
                Partition p = it.next();
                YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                rdb.disableAutoCompaction(cfh(rdb, p));
            }
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("error compacting", e);
        }
    }

    // this method is never used
    // we leave it in if we want to experiment again with manual compaction
    public void enableAutoCompaction(long start, long stop) {
        try {
            var interval = new TimeInterval(start, stop);
            log.debug("Enabling auto-compaction on partitions overlapping with {}", interval.toStringEncoded());
            var it = partitions.overlappingIterator(interval);
            while (it.hasNext()) {
                Partition p = it.next();
                YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                rdb.enableAutoCompaction(cfh(rdb, p));
            }
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("error compacting", e);
        }
    }

    public void compact() {
        try {
            log.debug("Compacting all partitions");
            long t0 = System.currentTimeMillis();
            for (Partition p : partitions) {
                YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                rdb.compactRange(cfh(rdb, p));
            }
            log.debug("Compaction finished in {} millisec", System.currentTimeMillis() - t0);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("error compacting", e);
        }

    }

    /**
     * 
     * Returns the ColumnFamilyHandle for the partition.
     * <p>
     * The databases created after 5.9.0 will use a different column family; before that version the data will be stored
     * in the default column family
     * 
     */
    ColumnFamilyHandle cfh(YRDB rdb, Partition p) throws RocksDBException {
        if (p.cfName == null) {
            return rdb.getDefaultColumnFamilyHandle();
        } else {
            return rdb.createAndGetColumnFamilyHandle(p.cfName);
        }
    }

    public long maxCoverageEnd() {
        return timeService.getMissionTime() + coverageEndDelta;
    }

    /**
     * Computes the coverage end as the greatest timestamp of a parameter in the archive, smaller than now
     * <p>
     * In order to find that, it iterates over all time segments
     * 
     * @throws IOException
     * @throws RocksDBException
     * @throws DecodingException
     */
    public long getCoverageEnd(long now) throws RocksDBException, IOException, DecodingException {
        long covEnd = TimeEncoding.NEGATIVE_INFINITY;
        log.debug("Computing coverage end as greatest timestamp of a parameter smaller than {}",
                TimeEncoding.toString(now));

        if (getParameterGroupIdDb().numGroups() == 0) {
            log.debug("No parameter group, coverageEnd is {}", TimeEncoding.toString(covEnd));
            return covEnd;
        }
        var partitions = getPartitions(TimeEncoding.MIN_INSTANT, now, false);
        if (partitions.isEmpty()) {
            log.debug("No partition, coverageEnd is {}", TimeEncoding.toString(covEnd));
            return covEnd;
        }

        // he first partition (in descending order) should be enough but maybe it will contain no data, that's why we
        // iterate
        var nowIntervalEnd = getIntervalEnd(now);
        for (var p : partitions) {
            try (RocksIterator it = getIterator(p)) {
                var groupIterator = getParameterGroupIdDb().groupIterato();
                pg_loop: while (groupIterator.hasNext()) {
                    var pg = groupIterator.next();

                    var sk = new SegmentKey(parameterIdDb.timeParameterId, pg.id, nowIntervalEnd, Byte.MAX_VALUE);
                    byte[] timeKey = p.version == 0 ? sk.encodeV0() : sk.encode();
                    it.seekForPrev(timeKey);
                    while (it.isValid()) {
                        sk = p.version == 0 ? SegmentKey.decodeV0(it.key()) : SegmentKey.decode(it.key());
                        if (sk.parameterGroupId != pg.id) {
                            // no time segment for this parameter group
                            continue pg_loop;
                        }
                        if (getIntervalEnd(sk.segmentStart) <= covEnd) {
                            // this time segment ends before the current covEnd, no point in decoding it
                            continue pg_loop;
                        }

                        var sts = (SortedTimeSegment) SegmentEncoderDecoder.decode(it.value(), sk.segmentStart);

                        long covEnd1;
                        int pos = sts.search(now);
                        if (pos >= 0) {
                            covEnd1 = sts.getTime(pos);
                        } else {
                            pos = -pos - 1;
                            if (pos == 0) {
                                it.prev();
                                continue;
                            }
                            covEnd1 = sts.getTime(pos - 1);
                        }
                        covEnd = Long.max(covEnd, covEnd1);
                        break;
                    }
                }
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
        final private String cfName;

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
