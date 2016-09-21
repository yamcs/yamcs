package org.yamcs.parameterarchive;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * Stores a map between
 * (parameter_fqn, type) and parameter_id
 * type is a 32 bit assigned corresponding (engType, rawType)
 * 
 * engType and rawType are one of the types from protobuf Value.Type - we use the numbers assuming that no more than 2^15 will ever exist.
 * 
 * 
 * Backed by RocksDB
 * 
 * @author nm
 *
 */
public class ParameterIdDb {
    final RocksDB db;
    final ColumnFamilyHandle p2pid_cfh;
    final static int TIMESTAMP_PARA_ID=0;

    //parameter fqn -> parameter type -> parameter id
    Map<String, Map<Integer, Integer>> p2pidCache = new HashMap<>();
    int highestParaId = TIMESTAMP_PARA_ID;

    ParameterIdDb(RocksDB db, ColumnFamilyHandle p2pid_cfh) {
        this.db = db;
        this.p2pid_cfh = p2pid_cfh;
        readDb();
    }


    /**
     * Get the mapping from parameter_name, type to parameter_id
     * 
     * It creates it if it does not exist
     * 
     * 
     * @param paramFqn
     * @param engType
     * @param rawType
     * 
     * @return a parameter id for the given parameter name and type
     * @throws ParameterArchiveException if there was an error creating and storing a new parameter_id
     */
    public synchronized int createAndGet(String paramFqn, Value.Type engType, Value.Type rawType) throws ParameterArchiveException {
        int type = numericType(engType, rawType);

        Map<Integer, Integer> m = p2pidCache.get(paramFqn);
        if(m==null) {
            m = new HashMap<Integer, Integer>();
            p2pidCache.put(paramFqn, m);
        }
        Integer pid = m.get(type);
        if(pid==null) {
            pid = ++highestParaId;
            m.put(type, pid);
            store(paramFqn);
        }
        return pid;
    }

    /**
     * get a parameter id for a parameter that only has engineering value
     * @param paramFqn
     * @param engType
     * @return a parameter id for the given parameter name and type
     */
    public int createAndGet(String paramFqn, Type engType) {
        return createAndGet(paramFqn, engType, null);
    }


    //compose a numeric type from engType and rawType (we assume that no more than 2^15 types will ever exist)
    private int numericType(Value.Type engType, Value.Type rawType) {
        int et = (engType==null)? 0xFFFF:engType.getNumber();
        int rt = (rawType==null)? 0xFFFF:rawType.getNumber();
        return et<<16|rt;
    }

    private void store(String paramFqn) throws ParameterArchiveException {
        Map<Integer, Integer> m = p2pidCache.get(paramFqn);
        byte[] key = paramFqn.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8*m.size());
        for(Map.Entry<Integer, Integer> me:m.entrySet()) {
            bb.putInt(me.getKey());
            bb.putInt(me.getValue());
        }
        try {
            db.put(p2pid_cfh, key, bb.array());
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot store key for new parameter id", e);
        }
    }


    private void readDb() {
        try(RocksIterator it = db.newIterator(p2pid_cfh)) {
            it.seekToFirst();
            while(it.isValid()) {
                byte[] pfqn = it.key();
                byte[] pIdTypeList = it.value();

                String paraName = new String(pfqn, StandardCharsets.UTF_8);
                Map<Integer, Integer> m = new HashMap<Integer, Integer>();

                p2pidCache.put(paraName, m);
                ByteBuffer bb = ByteBuffer.wrap(pIdTypeList);
                while(bb.hasRemaining()) {
                    int type = bb.getInt();
                    int pid = bb.getInt();            
                    m.put(type, pid);
                    if(pid > highestParaId) {
                        highestParaId = pid;
                    }
                }
                it.next();
            }
        }
    }

    static Value.Type getType(int x) {
        if(x==0xFFFF) return null;
        else return Value.Type.valueOf(x);
    }

    public void print(PrintStream out) {
        for(String pname: p2pidCache.keySet()) {
            out.println(pname+": ");
            Map<Integer, Integer> m = p2pidCache.get(pname);
            for(Map.Entry<Integer, Integer> e: m.entrySet()) {
                int parameterId = e.getValue();
                int et = e.getKey()>>16;
        int rt = e.getKey()&0xFFFF;
        out.println("\t("+getType(et)+", "+getType(rt)+") -> "+parameterId);
            }
        }
    }


    /*
     * return the number of unique parameters 
     */
    public int getSize() {
        return p2pidCache.size();
    }

    /**
     * Get all parameters ids for a given qualified name
     * 
     * return null if no parameter id exists for that fqn.
     * 
     * 
     * @param fqn - fully qualified name of the parameter for which the ids are returned
     * @return  all parameters ids for a given qualified name or null if no parameter id exists for that fqn
     */
    public synchronized ParameterId[] get(String fqn) {
        Map<Integer, Integer> m = p2pidCache.get(fqn);
        if(m==null) {
            return null;
        }

        ParameterId[] r = new ParameterId[m.size()];
        int i = 0;
        for(Entry<Integer, Integer> e: m.entrySet()) {
            r[i++]=new ParameterId(e.getValue(), e.getKey());
        }
        return r;
    }

    /**
     * returns the parameter FQN for the given parameterId - relatively expensive operation
     * @param parameterId
     * 
     * @return parameterFQN or null if there is no parameter with the given id
     */
    public String getParameterbyId(int parameterId) {
        for(Map.Entry<String, Map<Integer, Integer>> e: p2pidCache.entrySet()) {
            Map<Integer, Integer> m = e.getValue();
            if(m.containsValue(parameterId)) {
                return e.getKey();
            }
        }
        return null;
    }


    public static class ParameterId {
        public final int pid;
        public final Type engType;
        public final Type rawType;

        public ParameterId(int pid, int numericType) {
            this.pid = pid;
            int et =  numericType>>16;
            int rt =  numericType&0xFFFF;
            this.engType = getType(et);
            this.rawType = getType(rt);
        }

        @Override
        public String toString() {
            return "ParameterId [pid=" + pid + ", engType=" + engType
                    + ", rawType=" + rawType + "]";
        }
    }


}
