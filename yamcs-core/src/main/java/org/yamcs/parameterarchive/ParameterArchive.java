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

import org.rocksdb.ColumnFamilyHandle;
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
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
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

    public static final boolean STORE_RAW_VALUES = true;

    public static final int NUMBITS_MASK = 23; // 2^23 millisecons =~ 139 minutes per interval
    public static final int TIMESTAMP_MASK = (0xFFFFFFFF >>> (32 - NUMBITS_MASK));
    public static final long INTERVAL_MASK = ~TIMESTAMP_MASK;

    // from Yamcs 5.9.0, store the parameter archive data into a separate Column Family with this name
    public static final String CF_NAME = "parameter_archive";

    private ParameterIdDb parameterIdDb;

    private Tablespace tablespace;

    TimePartitionSchema partitioningSchema;

    // the tbsIndex used to encode partition information
    int partitionTbsIndex;

    private PartitionedTimeInterval<Partition> partitions = new PartitionedTimeInterval<>();
    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();

    TimeService timeService;
    private BackFiller backFiller;
    private RealtimeArchiveFiller realtimeFiller;
    YConfiguration realtimeFillerConfig;
    YConfiguration backFillerConfig;
    boolean realtimeFillerEnabled;
    boolean backFillerEnabled;
    int maxSegmentSize;
    boolean sparseGroups;
    double minimumGroupOverlap;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("backFiller", OptionType.MAP).withSpec(BackFiller.getSpec());
        spec.addOption("realtimeFiller", OptionType.MAP).withSpec(RealtimeArchiveFiller.getSpec());
        spec.addOption("partitioningSchema", OptionType.STRING).withDefault("none")
                .withChoices("YYYY/DOY", "YYYY/MM", "YYYY", "none");
        spec.addOption("maxSegmentSize", OptionType.INTEGER).withDefault(5000);
        spec.addOption("sparseGroups", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("minimumGroupOverlap", OptionType.FLOAT).withDefault(0.5);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        timeService = YamcsServer.getTimeService(yamcsInstance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb);
        this.maxSegmentSize = config.getInt("maxSegmentSize");

        if (config.containsKey("backFiller")) {
            backFillerConfig = config.getConfig("backFiller");
            log.debug("backFillerConfig: {}", backFillerConfig);
            backFillerEnabled = backFillerConfig.getBoolean("enabled", true);
        }
        if (config.containsKey("realtimeFiller")) {
            realtimeFillerConfig = config.getConfig("realtimeFiller");
            realtimeFillerEnabled = realtimeFillerConfig.getBoolean("enabled", false);
            log.debug("realtimeFillerConfig: {}", realtimeFillerConfig);
        }

        partitioningSchema = ydb.getTimePartitioningSchema(config);

        if (!config.containsKey("backFiller") && !config.containsKey("realtimeFiller")) {
            backFiller = new BackFiller(this, YConfiguration.emptyConfig());
        }
        sparseGroups = config.getBoolean("sparseGroups");
        minimumGroupOverlap = config.getDouble("minimumGroupOverlap");

        try {
            TablespaceRecord.Type trType = TablespaceRecord.Type.PARCHIVE_PINFO;
            List<TablespaceRecord> trl = tablespace.filter(trType, yamcsInstance, trb -> true);
            if (trl.size() > 1) {
                throw new DatabaseCorruptionException(
                        "More than one tablespace record of type " + trType.name() + " for instance " + yamcsInstance);
            }
            parameterIdDb = new ParameterIdDb(yamcsInstance, tablespace, sparseGroups, minimumGroupOverlap);

            TablespaceRecord tr;
            if (trl.isEmpty()) { // new database
                initializeDb();
            } else {// existing database
                tr = trl.get(0);
                partitionTbsIndex = tr.getTbsIndex();
                if (tr.hasPartitioningSchema()) {
                    partitioningSchema = TimePartitionSchema.getInstance(tr.getPartitioningSchema());
                }
                readPartitions();
                if (partitions.isEmpty() && partitioningSchema == null) {
                    partitions.insert(new Partition(tr.hasParchiveCf() ? tr.getParchiveCf() : null));
                }
            }

        } catch (RocksDBException | IOException e) {
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
            partitions.insert(new Partition(CF_NAME));
        }
        trb.setParchiveCf(CF_NAME);
        TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        partitionTbsIndex = tr.getTbsIndex();
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
        ByteArrayUtils.encodeInt(partitionTbsIndex, range, 0);

        try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, range)) {
            while (it.isValid()) {
                TimeBasedPartition tbp = TimeBasedPartition.parseFrom(it.value());
                String cfName = tbp.hasPartitionCf() ? tbp.getPartitionCf() : null;

                Partition p = new Partition(tbp.getPartitionStart(), tbp.getPartitionEnd(), tbp.getPartitionDir(),
                        cfName);
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

    public void writeToArchive(PGSegment pgs) throws RocksDBException, IOException {
        pgs.consolidate();
        Partition p = createAndGetPartition(getIntervalStart(pgs.getSegmentStart()));
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);
        ColumnFamilyHandle cfh = cfh(rdb, p);

        try (WriteBatch writeBatch = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            writeToBatch(cfh, writeBatch, pgs);
            rdb.write(wo, writeBatch);
        }
    }

    public void writeToArchive(long segStart, Collection<PGSegment> pgList) throws RocksDBException, IOException {
        Partition p = createAndGetPartition(getIntervalStart(segStart));
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        ColumnFamilyHandle cfh = cfh(rdb, p);

        try (WriteBatch writeBatch = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            for (PGSegment pgs : pgList) {
                pgs.consolidate();
                assert (segStart == pgs.getSegmentStart());
                writeToBatch(cfh, writeBatch, pgs);
            }
            rdb.write(wo, writeBatch);
        }
    }

    private void writeToBatch(ColumnFamilyHandle cfh, WriteBatch writeBatch, PGSegment pgs) throws RocksDBException {
        removeOldOverlappingSegments(cfh, writeBatch, pgs);

        // write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(parameterIdDb.timeParameterId, pgs.getParameterGroupId(),
                pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] timeValue = vsEncoder.encode(timeSegment);
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
                    SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = vsEncoder.encode(vs);
            writeBatch.put(cfh, engKey, engValue);

            if (STORE_RAW_VALUES && rvs != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_RAW_VALUE).encode();
                byte[] rawValue = vsEncoder.encode(rvs);
                writeBatch.put(cfh, rawKey, rawValue);
            }

            byte[] pssKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                    SegmentKey.TYPE_PARAMETER_STATUS).encode();
            byte[] pssValue = vsEncoder.encode(pss);
            writeBatch.put(cfh, pssKey, pssValue);

            if (gaps != null) {
                byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(),
                        SegmentKey.TYPE_GAPS).encode();
                byte[] rawValue = vsEncoder.encodeGaps(gaps);
                writeBatch.put(cfh, rawKey, rawValue);
            }
        }
    }

    private void removeOldOverlappingSegments(ColumnFamilyHandle cfh, WriteBatch writeBatch, PGSegment pgs)
            throws RocksDBException {
        long segStart = pgs.getSegmentStart();
        long segEnd = pgs.getSegmentEnd();
        int pgid = pgs.getParameterGroupId();

        byte[] timeKeyStart = new SegmentKey(parameterIdDb.timeParameterId, pgid, segStart, (byte) 0).encode();
        byte[] timeKeyEnd = new SegmentKey(parameterIdDb.timeParameterId, pgid, segEnd, Byte.MAX_VALUE).encode();
        deleteRange(cfh, writeBatch, timeKeyStart, timeKeyEnd);

        for (var pvs : pgs.pvSegments) {
            int pid = pvs.pid;

            byte[] paraKeyStart = new SegmentKey(pid, pgid, segStart, (byte) 0).encode();
            byte[] paraKeyEnd = new SegmentKey(pid, pgid, segEnd, Byte.MAX_VALUE).encode();
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
                p = new Partition(pinfo.getStart(), pinfo.getEnd(), pinfo.getDir(), CF_NAME);
                p = partitions.insert(p, 60000L);
                assert p != null;
                TimeBasedPartition tbp = TimeBasedPartition.newBuilder().setPartitionDir(p.partitionDir)
                        .setPartitionStart(p.getStart()).setPartitionEnd(p.getEnd())
                        .setPartitionCf(p.cfName)
                        .build();
                byte[] key = new byte[TBS_INDEX_SIZE + 8];
                ByteArrayUtils.encodeInt(partitionTbsIndex, key, 0);
                ByteArrayUtils.encodeLong(pinfo.getStart(), key, TBS_INDEX_SIZE);
                tablespace.putData(key, tbp.toByteArray());
            }
            return p;
        }
    }

    public Future<?> reprocess(long start, long stop) {
        log.debug("Scheduling a reprocess for interval [{} - {}]", TimeEncoding.toString(start),
                TimeEncoding.toString(stop));
        if (backFiller == null) {
            throw new ConfigurationException("backFilling is not enabled");
        }
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
        if (backFillerEnabled) {
            backFiller = new BackFiller(this, backFillerConfig);
            backFiller.start();
        }
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
            if (backFiller != null) {
                backFiller.shutDown();
            }
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
        SegmentEncoderDecoder decoder = new SegmentEncoderDecoder();
        for (Partition p : partitions) {
            try (RocksIterator it = getIterator(p)) {
                it.seekToFirst();
                while (it.isValid()) {
                    SegmentKey key = SegmentKey.decode(it.key());
                    byte[] v = it.value();
                    BaseSegment s;
                    s = decoder.decode(it.value(), key.segmentStart);
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

        if (backFiller != null) {
            log.debug("Shutting down the back filler");
            backFiller.shutDown();
        }
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

        log.debug("removing metadata records related to main parameter archive data");
        // data has been removed in the partition loop above
        tablespace.removeMetadataRecords(TablespaceRecord.Type.PARCHIVE_DATA);

        log.debug("removing parameter groups and related metadata");
        tablespace.removeTbsIndex(TablespaceRecord.Type.PARCHIVE_PGID2PG, pgTbsIndex);

        log.debug("removing partitions and related metadata");
        tablespace.removeTbsIndex(TablespaceRecord.Type.PARCHIVE_PINFO, partitionTbsIndex);

        log.debug("removing metadata storing aggregate/array composition");
        // there is no data of this type stored
        tablespace.removeMetadataRecords(TablespaceRecord.Type.PARCHIVE_AGGARR_INFO);

        parameterIdDb = new ParameterIdDb(yamcsInstance, tablespace, sparseGroups, minimumGroupOverlap);
        initializeDb();
        if (backFillerEnabled) {
            log.debug("Starting the back filler");
            backFiller = new BackFiller(this, backFillerConfig);
            backFiller.start();
        }
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

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart, int parameterGroupId)
            throws RocksDBException, IOException {
        byte[] timeKey = new SegmentKey(parameterIdDb.timeParameterId, parameterGroupId, segmentStart,
                SegmentKey.TYPE_ENG_VALUE).encode();
        YRDB rdb = tablespace.getRdb(p.partitionDir, false);

        var cfh = cfh(rdb, p);

        byte[] tv = rdb.get(cfh, timeKey);

        if (tv == null) {
            return null;
        }
        try {
            return (SortedTimeSegment) vsEncoder.decode(tv, segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    Partition getPartitions(long instant) {
        synchronized (partitions) {
            return partitions.getFit(instant);
        }
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

    public static class Partition extends TimeInterval {
        final String partitionDir;
        final private String cfName;

        Partition(String cfName) {
            super();
            this.partitionDir = null;
            this.cfName = cfName;
        }

        Partition(long start, long end, String dir, String cfName) {
            super(start, end);
            this.partitionDir = dir;
            this.cfName = cfName;
        }

        @Override
        public String toString() {
            return "partition: " + partitionDir + "[" + TimeEncoding.toString(getStart()) + " - "
                    + TimeEncoding.toString(getEnd()) + "]";
        }

        public String getPartitionDir() {
            return partitionDir;
        }
    }
}
