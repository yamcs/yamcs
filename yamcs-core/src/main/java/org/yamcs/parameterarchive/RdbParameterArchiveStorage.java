package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getIntervalEnd;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.dbKey;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.yamcs.logging.Log;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.PartitionedTimeInterval;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.VarIntUtil;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.TimePartitionInfo;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.DbIterator;
import org.yamcs.yarch.rocksdb.DescendingRangeIterator;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TimeBasedPartition;

/**
 * Stores the parameter archive in a RocksDB {@link Tablespace}.
 * <p>
 * The metadata records and the parameter groups are stored in the main database of the tablespace. The segments are
 * stored in the {@link ParameterArchive#CF_NAME} column family (or the default column family for archives created
 * before Yamcs 5.9.0) of the main database or of the time partition databases.
 * <p>
 * The partitions (and their versions) are described by TimeBasedPartition records stored in the main database under
 * the tbsIndex of the PARCHIVE_PINFO record.
 */
public class RdbParameterArchiveStorage implements ParameterArchiveStorage {
    final String yamcsInstance;
    final Tablespace tablespace;
    final Log log;

    private TimePartitionSchema partitioningSchema;
    // the tablespace record holding partition information
    private TablespaceRecord pinfoTablespaceRecord;
    private PartitionedTimeInterval<Partition> partitions = new PartitionedTimeInterval<>();

    // tbsIndex of the PARCHIVE_PGID2PG record, -1 if not yet known
    private int pgTbsIndex = -1;

    /**
     * Creates a storage which can only be used for the records and the parameter groups (for testing)
     */
    RdbParameterArchiveStorage(String yamcsInstance, Tablespace tablespace) {
        this.yamcsInstance = yamcsInstance;
        this.tablespace = tablespace;
        this.log = new Log(getClass(), yamcsInstance);
    }

    /**
     * Opens the storage, reading the existing partitions or initializing a new parameter archive.
     *
     * @param partitioningSchema
     *            the partitioning schema to use for a new parameter archive (null for no partitioning). For an
     *            existing archive, the schema stored in the database is used.
     */
    public static RdbParameterArchiveStorage open(String yamcsInstance, Tablespace tablespace,
            TimePartitionSchema partitioningSchema) {
        var storage = new RdbParameterArchiveStorage(yamcsInstance, tablespace);
        storage.partitioningSchema = partitioningSchema;
        try {
            storage.initPartitions();
        } catch (RocksDBException | IOException e) {
            throw new ParameterArchiveException("Cannot open the parameter archive", e);
        }
        return storage;
    }

    private void initPartitions() throws RocksDBException, IOException {
        Type trType = Type.PARCHIVE_PINFO;
        List<TablespaceRecord> trl = tablespace.filter(trType, yamcsInstance, trb -> true);
        if (trl.size() > 1) {
            throw new DatabaseCorruptionException(
                    "More than one tablespace record of type " + trType.name() + " for instance " + yamcsInstance);
        }
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
        }
    }

    /**
     * Creates the PARCHIVE_PINFO record for a new database
     */
    private void initializeDb() throws RocksDBException {
        log.debug("initializing db");
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_PINFO);
        if (partitioningSchema != null) {
            trb.setPartitioningSchema(partitioningSchema.getName());
        } else {
            partitions.insert(new Partition(ParameterArchive.CF_NAME, ParameterArchive.VERSION));
        }
        trb.setParchiveCf(ParameterArchive.CF_NAME);
        trb.setParchiveVersion(ParameterArchive.VERSION);
        pinfoTablespaceRecord = tablespace.createMetadataRecord(yamcsInstance, trb);
    }

    /**
     * Reads the existing partitions
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

    public TimePartitionSchema getPartitioningSchema() {
        return partitioningSchema;
    }

    public Tablespace getTablespace() {
        return tablespace;
    }

    @Override
    public List<TablespaceRecord> listRecords(Type type) {
        return tablespace.filter(type, yamcsInstance, trb -> true);
    }

    @Override
    public TablespaceRecord createRecord(TablespaceRecord.Builder trb) {
        try {
            return tablespace.createMetadataRecord(yamcsInstance, trb);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot create record", e);
        }
    }

    @Override
    public TablespaceRecord updateRecord(TablespaceRecord.Builder trb) {
        try {
            return tablespace.updateRecord(yamcsInstance, trb);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot update record", e);
        }
    }

    /**
     * returns the tbsIndex of the PARCHIVE_PGID2PG record, creating the record if it does not exist
     */
    private synchronized int pgTbsIndex() throws RocksDBException {
        if (pgTbsIndex == -1) {
            List<TablespaceRecord> trl = tablespace.filter(Type.PARCHIVE_PGID2PG, yamcsInstance, trb -> true);
            if (trl.size() > 1) {
                throw new DatabaseCorruptionException("Multiple records of type "
                        + Type.PARCHIVE_PGID2PG.name() + " found for instance " + yamcsInstance);
            }
            TablespaceRecord tr;
            if (trl.isEmpty()) {
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_PGID2PG);
                tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            } else {
                tr = trl.get(0);
            }
            pgTbsIndex = tr.getTbsIndex();
        }
        return pgTbsIndex;
    }

    @Override
    public void loadParameterGroups(ParameterGroupConsumer consumer) {
        try {
            YRDB db = tablespace.getRdb();
            byte[] range = new byte[TBS_INDEX_SIZE];
            ByteArrayUtils.encodeInt(pgTbsIndex(), range, 0);

            try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, range)) {
                while (it.isValid()) {
                    byte[] key = it.key();
                    int pgid = ByteArrayUtils.decodeInt(key, TBS_INDEX_SIZE);
                    IntArray pids = VarIntUtil.decodeDeltaIntArray(it.value());
                    consumer.accept(pgid, pids);
                    it.next();
                }
            }
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot load the parameter groups", e);
        }
    }

    @Override
    public void saveParameterGroup(int pgid, IntArray pids) {
        try {
            byte[] key = new byte[TBS_INDEX_SIZE + 4];
            ByteArrayUtils.encodeInt(pgTbsIndex(), key, 0);
            ByteArrayUtils.encodeInt(pgid, key, TBS_INDEX_SIZE);
            byte[] v = VarIntUtil.encodeDeltaIntArray(pids);
            tablespace.putData(key, v);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot save the parameter group " + pgid, e);
        }
    }

    @Override
    public int getVersion(long intervalStart) {
        Partition p = getPartition(intervalStart);
        return p == null ? ParameterArchive.VERSION : p.version;
    }

    /**
     * get partition for interval, creating it if it doesn't exist
     */
    private Partition createAndGetPartition(long intervalStart) throws RocksDBException {
        synchronized (partitions) {
            Partition p = partitions.getFit(intervalStart);
            if (p == null) {
                TimePartitionInfo pinfo = partitioningSchema.getPartitionInfo(intervalStart);
                p = new Partition(pinfo.getStart(), pinfo.getEnd(), pinfo.getDir(), ParameterArchive.CF_NAME,
                        ParameterArchive.VERSION);
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
     * returns the partition where the instant fits or null if there is no such partition
     */
    Partition getPartition(long instant) {
        synchronized (partitions) {
            return partitions.getFit(instant);
        }
    }

    private List<Partition> allPartitions() {
        synchronized (partitions) {
            List<Partition> r = new ArrayList<>();
            partitions.forEach(r::add);
            return r;
        }
    }

    /**
     * a copy of the partitions from start to stop inclusive
     *
     * @return a sorted list of partitions
     */
    List<Partition> getPartitions(long start, long stop, boolean ascending) {
        List<Partition> r = new ArrayList<>();
        synchronized (partitions) {
            Iterator<Partition> it;
            if (ascending) {
                it = partitions.overlappingIterator(new TimeInterval(start, stop));
            } else {
                it = partitions.overlappingReverseIterator(new TimeInterval(start, stop));
            }
            while (it.hasNext()) {
                r.add(it.next());
            }
        }
        return r;
    }

    @Override
    public ParchiveWriteBatch newWriteBatch(long intervalStart) {
        try {
            Partition p = createAndGetPartition(intervalStart);
            YRDB rdb = tablespace.getRdb(p.partitionDir, false);
            return new RdbWriteBatch(rdb, cfh(rdb, p), p.version);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot create write batch", e);
        }
    }

    @Override
    public SegmentScanner scan(ScanRequest request) {
        return new RdbSegmentScanner(request);
    }

    /**
     * Removes all the data and metadata related to the parameter archive and initialize a new database
     * <p>
     * If the parameter archive is stored in the default column family this operation will remove all the records of
     * the parameter archive.
     * <p>
     * If the parameter archive is stored into its own column family this operation will simply drop that column
     * family (for all time based partitions). The new archive is stored in its own column family.
     */
    @Override
    public synchronized void purge() {
        try {
            IntArray allPids = new IntArray();
            listRecords(Type.PARCHIVE_DATA).forEach(tr -> allPids.add(tr.getTbsIndex()));
            listRecords(Type.PARCHIVE_AGGARR_INFO).forEach(tr -> allPids.add(tr.getTbsIndex()));

            synchronized (partitions) {
                for (var p : partitions) {
                    log.debug("purging partition {}", p);
                    YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                    if (p.cfName == null || YRDB.DEFAULT_CF.equals(p.cfName)) {
                        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                            for (int i = 0; i < allPids.size(); i++) {
                                var tbsIndex = allPids.get(i);
                                wb.deleteRange(dbKey(tbsIndex), dbKey(tbsIndex + 1));
                            }
                            rdb.write(wo, wb);
                        }
                    } else {
                        rdb.dropColumnFamily(p.cfName);
                    }
                }
                partitions = new PartitionedTimeInterval<>();
            }

            log.debug("removing metadata records related to main parameter archive data");
            // data has been removed in the partition loop above
            tablespace.removeMetadataRecords(Type.PARCHIVE_DATA);

            log.debug("removing parameter groups and related metadata");
            tablespace.removeTbsIndex(Type.PARCHIVE_PGID2PG, pgTbsIndex());
            pgTbsIndex = -1;

            log.debug("removing partitions and related metadata");
            tablespace.removeTbsIndex(Type.PARCHIVE_PINFO, pinfoTablespaceRecord.getTbsIndex());

            log.debug("removing metadata storing aggregate/array composition");
            // there is no data of this type stored
            tablespace.removeMetadataRecords(Type.PARCHIVE_AGGARR_INFO);

            initializeDb();
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Error purging the parameter archive", e);
        }
    }

    @Override
    public void compact() {
        try {
            log.debug("Compacting all partitions");
            long t0 = System.currentTimeMillis();
            for (Partition p : allPartitions()) {
                YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                rdb.compactRange(cfh(rdb, p));
            }
            log.debug("Compaction finished in {} millisec", System.currentTimeMillis() - t0);
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("error compacting", e);
        }
    }

    // this method is never used
    // we leave it in if we want to experiment again with manual compaction
    public void disableAutoCompaction(long start, long stop) {
        try {
            var interval = new TimeInterval(start, stop);
            log.debug("Disabling auto-compaction on partitions overlapping with {}", interval.toStringEncoded());
            for (Partition p : getPartitions(start, stop, true)) {
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
            for (Partition p : getPartitions(start, stop, true)) {
                YRDB rdb = tablespace.getRdb(p.partitionDir, false);
                rdb.enableAutoCompaction(cfh(rdb, p));
            }
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("error compacting", e);
        }
    }

    public void printKeys(PrintStream out) throws DecodingException, RocksDBException, IOException {
        out.println("pid\t pgid\t type\tSegmentStart\tcount\tsize\tstype");
        for (Partition p : allPartitions()) {
            YRDB rdb = tablespace.getRdb(p.partitionDir, false);
            try (RocksIterator it = rdb.newIterator(cfh(rdb, p))) {
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
     * Returns the ColumnFamilyHandle for the partition.
     * <p>
     * The databases created after 5.9.0 will use a different column family; before that version the data will be
     * stored in the default column family
     */
    static ColumnFamilyHandle cfh(YRDB rdb, Partition p) throws RocksDBException {
        if (p.cfName == null) {
            return rdb.getDefaultColumnFamilyHandle();
        } else {
            return rdb.createAndGetColumnFamilyHandle(p.cfName);
        }
    }

    static byte[] encodeKey(int version, int pid, int pgid, long segmentStart, byte type) {
        return version == 0 ? SegmentKey.encodeV0(pid, pgid, segmentStart, type)
                : SegmentKey.encode(pid, pgid, segmentStart, type);
    }

    static SegmentKey decodeKey(int version, byte[] key) {
        return version == 0 ? SegmentKey.decodeV0(key) : SegmentKey.decode(key);
    }

    static class RdbWriteBatch implements ParchiveWriteBatch {
        final YRDB rdb;
        final ColumnFamilyHandle cfh;
        final int version;
        final WriteBatch writeBatch = new WriteBatch();

        RdbWriteBatch(YRDB rdb, ColumnFamilyHandle cfh, int version) {
            this.rdb = rdb;
            this.cfh = cfh;
            this.version = version;
        }

        @Override
        public int version() {
            return version;
        }

        @Override
        public void put(byte[] key, byte[] value) {
            try {
                writeBatch.put(cfh, key, value);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the batch", e);
            }
        }

        @Override
        public void merge(byte[] key, byte[] value) {
            if (version == 0) {
                throw new IllegalStateException("Cannot merge in a version 0 partition");
            }
            try {
                writeBatch.merge(cfh, key, value);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the batch", e);
            }
        }

        @Override
        public void delete(byte[] key) {
            try {
                writeBatch.delete(cfh, key);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the batch", e);
            }
        }

        @Override
        public void deleteIfExists(byte[] key) {
            try {
                if (rdb.get(cfh, key) != null) {
                    writeBatch.delete(cfh, key);
                }
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the batch", e);
            }
        }

        @Override
        public void deleteRange(byte[] start, byte[] end) {
            try {
                writeBatch.deleteRange(cfh, start, end);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the batch", e);
            }
        }

        @Override
        public void commit() {
            try (WriteOptions wo = new WriteOptions()) {
                rdb.write(wo, writeBatch);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Error writing to the parameter archive", e);
            }
        }

        @Override
        public void close() {
            writeBatch.close();
        }
    }

    /**
     * Scans the partitions one by one; in each partition, one iterator per (pid, pgid) pair is used, all with the same
     * snapshot. The iterators are merged by segment start (and pair index).
     */
    class RdbSegmentScanner implements SegmentScanner {
        final ScanRequest req;
        final Iterator<Partition> partitionIt;
        PartitionScanner current;
        SegmentRecord curValue;

        RdbSegmentScanner(ScanRequest req) {
            this.req = req;
            List<Partition> l = req.size() == 0 ? List.of()
                    : getPartitions(getIntervalStart(req.start()), getIntervalEnd(req.stop()), req.ascending());
            this.partitionIt = l.iterator();
            next();
        }

        @Override
        public boolean isValid() {
            return curValue != null;
        }

        @Override
        public SegmentRecord value() {
            return curValue;
        }

        @Override
        public void next() {
            curValue = null;
            while (true) {
                if (current != null) {
                    curValue = current.next();
                    if (curValue != null) {
                        return;
                    }
                    current.close();
                    current = null;
                }
                if (!partitionIt.hasNext()) {
                    return;
                }
                try {
                    current = new PartitionScanner(req, partitionIt.next());
                } catch (RocksDBException e) {
                    throw new ParameterArchiveException("Failed to create iterator", e);
                }
            }
        }

        @Override
        public void close() {
            if (current != null) {
                current.close();
                current = null;
            }
        }
    }

    /**
     * iterator over the keys of one (pid, pgid) pair in one partition
     */
    static class PairIterator {
        final int pairIndex;
        final DbIterator dbIterator;
        final int version;
        SegmentKey key;

        PairIterator(int pairIndex, DbIterator dbIterator, int version) {
            this.pairIndex = pairIndex;
            this.dbIterator = dbIterator;
            this.version = version;
            updateKey();
        }

        void updateKey() {
            key = dbIterator.isValid() ? decodeKey(version, dbIterator.key()) : null;
        }
    }

    class PartitionScanner {
        final ScanRequest req;
        final int version;
        final YRDB rdb;
        final ColumnFamilyHandle cfh;
        final Snapshot snapshot;
        final ReadOptions opts;
        final PriorityQueue<PairIterator> queue;
        final List<PairIterator> all = new ArrayList<>();

        // cache of the last time segment retrieved
        int lastTimePgid = -1;
        long lastTimeSegStart;
        byte[] lastTimeSegment;

        PartitionScanner(ScanRequest req, Partition partition) throws RocksDBException {
            this.req = req;
            this.version = partition.version;
            this.rdb = tablespace.getRdb(partition.partitionDir, false);
            this.cfh = cfh(rdb, partition);
            this.snapshot = rdb.getSnapshot();
            this.opts = new ReadOptions();
            opts.setSnapshot(snapshot);

            Comparator<SegmentKey> segStartCmp = Comparator.comparingLong(k -> k.segmentStart);
            if (!req.ascending()) {
                segStartCmp = segStartCmp.reversed();
            }
            final Comparator<SegmentKey> c = segStartCmp;
            this.queue = new PriorityQueue<>((a, b) -> {
                int r = c.compare(a.key, b.key);
                return r != 0 ? r : Integer.compare(a.pairIndex, b.pairIndex);
            });

            // we use the 0 and Byte.MAX_VALUE for the segment type to make sure we catch all types.
            long rangeStartTime = getIntervalStart(req.start());
            for (int i = 0; i < req.size(); i++) {
                int pid = req.pids()[i];
                int pgid = req.pgids()[i];
                byte[] rangeStart = encodeKey(version, pid, pgid, rangeStartTime, (byte) 0);
                byte[] rangeStop = encodeKey(version, pid, pgid, req.stop(), Byte.MAX_VALUE);
                RocksIterator it = rdb.newIterator(cfh, opts);
                DbIterator dbIt = req.ascending() ? new AscendingRangeIterator(it, rangeStart, rangeStop)
                        : new DescendingRangeIterator(it, rangeStart, rangeStop);
                var pit = new PairIterator(i, dbIt, version);
                all.add(pit);
                if (pit.key != null) {
                    queue.add(pit);
                }
            }
        }

        /**
         * returns the next record or null if there is no more data in this partition
         */
        SegmentRecord next() {
            PairIterator pit = queue.poll();
            if (pit == null) {
                return null;
            }
            int pid = req.pids()[pit.pairIndex];
            int pgid = req.pgids()[pit.pairIndex];
            long segStart = pit.key.segmentStart;
            boolean isTime = pid == req.timePid();

            byte[] eng = null, raw = null, status = null, gaps = null, time = null;
            while (pit.key != null && pit.key.segmentStart == segStart) {
                byte[] value = pit.dbIterator.value();
                switch (pit.key.type) {
                case SegmentKey.TYPE_ENG_VALUE:
                    if (isTime) {
                        time = value;
                    } else if (req.retrieveEngValues()) {
                        eng = value;
                    }
                    break;
                case SegmentKey.TYPE_RAW_VALUE:
                    if (req.retrieveRawValues()) {
                        raw = value;
                    }
                    break;
                case SegmentKey.TYPE_PARAMETER_STATUS:
                    if (req.retrieveParameterStatus()) {
                        status = value;
                    }
                    break;
                case SegmentKey.TYPE_GAPS:
                    gaps = value;
                    break;
                }
                if (req.ascending()) {
                    pit.dbIterator.next();
                } else {
                    pit.dbIterator.prev();
                }
                pit.updateKey();
            }
            if (pit.key != null) {
                queue.add(pit);
            }
            if (isTime) {
                // cache it for the parameters of the same group and segment start which come next
                lastTimePgid = pgid;
                lastTimeSegStart = segStart;
                lastTimeSegment = time;
            } else {
                time = getTimeSegment(pgid, segStart);
            }
            return new SegmentRecord(pit.pairIndex, pid, pgid, segStart, time, eng, raw, status, gaps);
        }

        private byte[] getTimeSegment(int pgid, long segStart) {
            if (pgid != lastTimePgid || segStart != lastTimeSegStart) {
                byte[] timeKey = encodeKey(version, req.timePid(), pgid, segStart, SegmentKey.TYPE_ENG_VALUE);
                try {
                    lastTimeSegment = rdb.get(cfh, opts, timeKey);
                } catch (RocksDBException e) {
                    throw new ParameterArchiveException("Failed extracting data from the parameter archive", e);
                }
                lastTimePgid = pgid;
                lastTimeSegStart = segStart;
            }
            return lastTimeSegment;
        }

        void close() {
            for (var pit : all) {
                pit.dbIterator.close();
            }
            opts.close();
            rdb.releaseSnapshot(snapshot);
        }
    }
}
