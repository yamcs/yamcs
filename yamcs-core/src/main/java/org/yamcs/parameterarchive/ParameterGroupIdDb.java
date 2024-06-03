package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rocksdb.RocksDBException;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.VarIntUtil;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * Stores a map between List&lt;parameter_id&gt; and ParameterGroup_id.
 * <p>
 * Stores data in the main tablespace:
 * <p>
 * database key = tbsIndex,ParameterGroup_id
 * <p>
 * datbase value = SortedIntArray of parameter_id, stored delta encoded
 * <p>
 * 
 * Backed by RocksDB
 *
 */
public class ParameterGroupIdDb {
    final Tablespace tablespace;
    final String yamcsInstance;

    /**
     * if true, allow parameter lists to be part of the groups even though not matching 100% all parameters
     * <p>
     * This results in gaps in the columnar data but reduces the number of groups
     **/
    final boolean sparseGroups;
    /**
     * if sparseGroups = true: minimum amount of overlap between a parameter list and an existing group, for the list to
     * be considered part of the same group
     * <p>
     * value between 0.0 and 1.0
     */
    final double minOverlap;

    // used to store the parameter groups in the RocksDB
    int tbsIndex;

    // The list of all parameter groups.
    // The index in this list is the pgid
    // May contain nulls if a group is ever removed, or if the archive comes from Yamcs prior to 5.9.5 - for some reason
    // the group 0 was not used
    List<ParameterGroup> groups = new ArrayList<>();

    Map<IntArray, ParameterGroup> pg2groupCache = new HashMap<>();

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    ParameterGroupIdDb(String yamcsInstance, Tablespace tablespace) throws RocksDBException {
        this(yamcsInstance, tablespace, false, 1);
    }

    /**
     * Create a new db storing the parameter group definition
     * 
     * @param yamcsInstance
     * @param tablespace
     *            - the tablespace is used to load and persist the group to the Rocksdb
     * @param sparseGroups
     *            - if true, allow parameter lists to be part of the groups even though not matching 100% all parameters
     * @param minOverlap
     *            - considered only if sparseGroups = true - minimum amount of overlap between a parameter list and an
     *            existing group, for the list to be considered part of the same group - should be between 0 and 1.
     * @throws RocksDBException
     */
    ParameterGroupIdDb(String yamcsInstance, Tablespace tablespace, boolean sparseGroups, double minOverlap)
            throws RocksDBException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        this.minOverlap = minOverlap;
        this.sparseGroups = sparseGroups;
        if (minOverlap < 0 || minOverlap > 1) {
            throw new IllegalArgumentException("The minOverlap parameter should be between 0 and 1");
        }
        readDb();
    }

    private void readDb() throws RocksDBException {
        List<TablespaceRecord> trl = tablespace.filter(TablespaceRecord.Type.PARCHIVE_PGID2PG, yamcsInstance,
                trb -> true);
        if (trl.size() > 1) {
            throw new DatabaseCorruptionException("Multiple records of type "
                    + TablespaceRecord.Type.PARCHIVE_PGID2PG.name() + " found for instance " + yamcsInstance);
        }
        TablespaceRecord tr;
        if (trl.isEmpty()) {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                    .setType(TablespaceRecord.Type.PARCHIVE_PGID2PG);
            tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        } else {
            tr = trl.get(0);
        }
        this.tbsIndex = tr.getTbsIndex();
        YRDB db = tablespace.getRdb();
        byte[] range = new byte[TBS_INDEX_SIZE];
        ByteArrayUtils.encodeInt(tr.getTbsIndex(), range, 0);

        try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, range)) {
            while (it.isValid()) {
                byte[] key = it.key();
                int pgid = ByteArrayUtils.decodeInt(key, TBS_INDEX_SIZE);

                IntArray pids = VarIntUtil.decodeDeltaIntArray(it.value());
                var pg = new ParameterGroup(pgid, pids);
                for (int i = groups.size(); i < pgid; i++) { // add nulls if we miss some pgid
                    groups.add(null);
                }
                assert (groups.size() == pgid);
                groups.add(pg);

                pg2groupCache.put(pids, pg);
                it.next();
            }
        }
    }

    /**
     * Returns the ParameterGroup for the given parameter id array, creating it if it does not exist yet
     * <p>
     * If a group matching the array exists, it is returned.
     * <p>
     * If it does not exist and sparseGroup is disabled, then a new group is made.
     * <p>
     * If sparseGroup is enabled, then a group overlapping the input array is searched. If no existing group matches
     * half of the input array, then a new group is created for the input array.
     * <p>
     * If an existing group overlapping the input array is found, then there are two cases:
     * <ul>
     * <li>The existing group contains all the entries from the input array. Then it is simply returned.</li>
     * <li>The existing group misses some entries from the input array. In this case the group is extended with the
     * missing entries, then it is returned.</li>
     * </ul>
     * 
     */
    public ParameterGroup getGroup(IntArray input) throws RocksDBException {
        lock.writeLock().lock();
        try {
            ParameterGroup pg = pg2groupCache.get(input);
            if (pg == null) {
                if (sparseGroups) {
                    pg = createOrModify(input);
                } else {
                    pg = new ParameterGroup(groups.size(), input);
                    groups.add(pg);
                    pg2groupCache.put(input, pg);
                    writeToDb(pg);
                }
            }
            return pg;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // this is called when sparseGroup = true
    private ParameterGroup createOrModify(IntArray input) throws RocksDBException {
        ParameterGroup max = null;
        // go through all groups and find one which overlap most with the input (but at least the minimum required)
        int maxOverlap = 0;
        for (var g : groups) {
            if (g == null) {
                continue;
            }
            int overlap = input.intersectionSize(g.pids);
            if (overlap < input.size() * minOverlap && overlap < g.pids.size() * minOverlap) {
                continue;
            }

            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                max = g;
            } else if (overlap == maxOverlap && (max == null || g.pids.size() < max.pids.size())) {
                // If two groups have the same overlap value, give preference to the one with fewer elements.
                // This strategy aims to minimise the sparsity of the columns.
                g = max;
            }
        }
        if (max == null) {// no group overlapping enough has been found, make a new one
            var pg = new ParameterGroup(groups.size(), input);
            groups.add(pg);
            pg2groupCache.put(input, pg);
            writeToDb(pg);
            return pg;
        } else if (maxOverlap == input.size()) { // a group has been found and contains all the elements from the input
            pg2groupCache.put(input, max);
            return max;
        } else {// a group has been found but it needs to be modified to add the missing elements from the input
            max.pids = IntArray.union(max.pids, input, max.pids.size() + input.size() - maxOverlap);
            writeToDb(max);
            pg2groupCache.put(max.pids, max);
            pg2groupCache.put(input, max);
            return max;
        }
    }

    private void writeToDb(ParameterGroup pg) throws RocksDBException {
        byte[] key = new byte[TBS_INDEX_SIZE + 4];
        ByteArrayUtils.encodeInt(tbsIndex, key, 0);
        ByteArrayUtils.encodeInt(pg.id, key, TBS_INDEX_SIZE);
        byte[] v = VarIntUtil.encodeDeltaIntArray(pg.pids);
        tablespace.putData(key, v);
    }

    /**
     * return the members of the pg group.
     * <p>
     * Throws {@link IllegalArgumentException} if the group does not exist
     */
    public IntArray getParameterGroup(int pg) {
        lock.readLock().lock();
        try {
            if ((pg >= groups.size()) || (groups.get(pg) == null)) {
                throw new IllegalArgumentException("No parameter group with the id " + pg);
            }
            return groups.get(pg).pids;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return pg2groupCache.toString();
    }

    /**
     * get all parameter group ids for the parameters from which this parameter id is part of
     * 
     * @param pid
     * @return the parameter group ids for the parameters groups that contain the pid
     */
    public int[] getAllGroups(int pid) {
        lock.readLock().lock();
        try {
            IntArray r = new IntArray();
            for (var pg : groups) {
                if (pg != null && pg.pids.binarySearch(pid) >= 0) {
                    r.add(pg.id);
                }
            }
            return r.toArray();
        } finally {
            lock.readLock().unlock();
        }
    }

    static public class ParameterGroup {
        /** parmeter group id */
        final int id;
        /** list of parameter ids */
        IntArray pids;

        public ParameterGroup(int pgId, IntArray pids) {
            this.id = pgId;
            this.pids = pids;
        }
    }
}
