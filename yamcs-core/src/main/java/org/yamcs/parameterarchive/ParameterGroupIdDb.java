package org.yamcs.parameterarchive;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.SortedIntArray;

/**
 * Stores a map between 
 * List&lt;parameter_id&gt; and ParameterGroup_id.
 * 
 * Stores data one column family:
 * pgid2pg 
 *     key = ParameterGroup_id
 *     value = SortedVarArray of parameter_id
 *           
 * 
 * Backed by RocksDB
 * @author nm
 *
 */
public class ParameterGroupIdDb {
    final RocksDB db;
    final ColumnFamilyHandle pgid2pg_cfh;
    int highestPgId=0;
    Map<SortedIntArray, Integer> pg2pgidCache = new HashMap<>();


    ParameterGroupIdDb(RocksDB db, ColumnFamilyHandle pgid2pg_cfh) {
        this.db = db;
        this.pgid2pg_cfh = pgid2pg_cfh;
        readDb();
    }

    /**
     * Creates (if not already there) a new ParameterGroupId for the given parameter id array
     * 
     * @param s
     * @return the parameterGroupId for the given parameter id array
     * @throws RocksDBException
     */
    public synchronized int createAndGet(SortedIntArray s) throws RocksDBException {
        Integer pgid = pg2pgidCache.get(s);
        if(pgid == null) {
            int x = ++highestPgId;;
            pgid = x;
            db.put(pgid2pg_cfh, encodeInt(x), s.encodeToVarIntArray());
            pg2pgidCache.put(s, pgid);
        }

        return pgid;
    }

    /**
     * Creates (if not already there) a new ParameterGroupId for the given parameter id array
     * 
     * The parameter id array is sorted before
     * @param parameterIdArray
     * @return he parameterGroupId for the given parameter id array
     * @throws RocksDBException
     */
    public int createAndGet(int[] parameterIdArray) throws RocksDBException {
        return createAndGet(new SortedIntArray(parameterIdArray));
    }

    private byte[] encodeInt(int x) {
        return ByteBuffer.allocate(4).putInt(x).array();
    }

    private void readDb() {
        try(RocksIterator it = db.newIterator(pgid2pg_cfh)) {
            it.seekToFirst();
            while(it.isValid()) {

                int pgid = ByteBuffer.wrap(it.key()).getInt();

                if(highestPgId < pgid) highestPgId = pgid;

                SortedIntArray svil = SortedIntArray.decodeFromVarIntArray(it.value());
                pg2pgidCache.put(svil, pgid);
                it.next();
            }
        }
    }


    public String toString() {
        return pg2pgidCache.toString();
    }

    public void print(PrintStream out) {
        for(Map.Entry<SortedIntArray, Integer> e: pg2pgidCache.entrySet()) {
            out.println(e.getValue()+": "+e.getKey());
        }
    }

    /**
     * get all parameter group ids for the parameters from which this parameter id is part of
     * @param pid
     * @return the parameter group ids for the parameters groups that contain the pid
     */
    public synchronized int[] getAllGroups(int pid) {
        IntArray r = new IntArray();
        for (Map.Entry<SortedIntArray, Integer> e: pg2pgidCache.entrySet()) {
            SortedIntArray s = e.getKey();
            if(s.contains(pid)) {
                r.add(e.getValue());
            }
        }
        return r.toArray();
    }
}
