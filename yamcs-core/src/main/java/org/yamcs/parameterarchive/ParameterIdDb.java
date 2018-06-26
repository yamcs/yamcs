package org.yamcs.parameterarchive;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.rocksdb.RocksDBException;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

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
    final Tablespace tablespace;
    final String yamcsInstance;
    //parameter fqn -> parameter type -> parameter id
    Map<String, Map<Integer, Integer>> p2pidCache = new HashMap<>();
    //used as parameterId (tbsIndex) for the time  records
    int timeParameterId;
    public static final String TIME_PARAMETER_FQN="__time_parameter_"; 

    ParameterIdDb(String yamcsInstance, Tablespace tablespace) throws RocksDBException, IOException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
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
            m = new HashMap<>();
            p2pidCache.put(paramFqn, m);
        }
        Integer pid = m.get(type);
        if(pid==null) {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.PARCHIVE_DATA)
                    .setParameterFqn(paramFqn).setParameterType(type);
            TablespaceRecord tr;
            try {
                tr = tablespace.createMetadataRecord(yamcsInstance, trb);
                pid = tr.getTbsIndex();
                m.put(type, pid);
            } catch (RocksDBException e) {
                throw new ParameterArchiveException("Cannot store key for new parameter id", e);
            }
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


    private void readDb() throws RocksDBException, IOException {
        List<TablespaceRecord> trlist = tablespace.filter(TablespaceRecord.Type.PARCHIVE_DATA, yamcsInstance, (trb)-> true);
        if(trlist.isEmpty()) {
            //new database- create a record for the time parameter
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.PARCHIVE_DATA)
                    .setParameterFqn(TIME_PARAMETER_FQN);
            TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            timeParameterId = tr.getTbsIndex();
        } else {
            for(TablespaceRecord tr: trlist) {
                String paraName = tr.getParameterFqn();
                if(TIME_PARAMETER_FQN.equals(paraName)) {
                    timeParameterId = tr.getTbsIndex();
                } else {
                    int pid = tr.getTbsIndex();
                    int type = tr.getParameterType();
                    Map<Integer, Integer> m = p2pidCache.get(paraName);
                    if(m==null) {
                        m = new HashMap<>();
                        p2pidCache.put(paraName, m);
                    }
                    m.put(type, pid);
                }
            }
        }
    }

    static Value.Type getEngType(int x) {
        int et = x>>16;
        if(et==0xFFFF) {
            return null;
        }
        else return Value.Type.valueOf(et);
    }
    
    static Value.Type getRawType(int x) {
        int rt = x&0xFFFF;
        if(rt==0xFFFF) {
            return null;
        }
        else return Value.Type.valueOf(rt);
    }
    
    public int getTimeParameterId() {
        return timeParameterId;
    }

    public void print(PrintStream out) {
        for(Map.Entry<String, Map<Integer, Integer>> me: p2pidCache.entrySet()) {
            String pname = me.getKey();
            Map<Integer, Integer> m = me.getValue();
            out.println(pname+": ");
            for(Map.Entry<Integer, Integer> e: m.entrySet()) {
                int parameterId = e.getValue();
                int type = e.getKey();
                
                out.println("\t("+getEngType(type)+", "+getRawType(type)+") -> "+parameterId);
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
    public String getParameterFqnById(int parameterId) {
        for(Map.Entry<String, Map<Integer, Integer>> e: p2pidCache.entrySet()) {
            Map<Integer, Integer> m = e.getValue();
            if(m.containsValue(parameterId)) {
                return e.getKey();
            }
        }
        return null;
    }
    /**
     * returns ParameterId based on numeric id
     */
    public ParameterId getParameterId(int pid) {
        for(Map.Entry<String, Map<Integer, Integer>> e: p2pidCache.entrySet()) {
            Map<Integer, Integer> m = e.getValue();
            for(Entry<Integer, Integer> e1: m.entrySet()) {
                if(e1.getValue()==pid) {
                    return new ParameterId(e1.getValue(), e1.getKey());
                }
            }
        }
        return null;
    }

}
