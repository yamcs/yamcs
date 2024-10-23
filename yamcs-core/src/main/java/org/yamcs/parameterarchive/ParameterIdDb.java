package org.yamcs.parameterarchive;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.rocksdb.RocksDBException;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

/**
 * Stores a map (parameter_fqn, type) -> parameter_id
 * <p>
 * type is a 32 bit assigned corresponding (engType, rawType)
 * <p>
 * engType and rawType are one of the types from protobuf Value.Type - the numbers are used assuming that no more than
 * 2^15 will ever exist.
 * <p>
 * The parameter_id is the tbsIndex from RocksdDb backed database.
 * <p>
 * The aggregates and arrays are also allocated parameter_ids (i.e. tbsIndex) but they do not contain any data, just a
 * list of members parameter ids stored in the tablespace metadata.
 * 
 * 
 * Backed by RocksDB
 *
 */
public class ParameterIdDb {
    final static float LOAD_FACTOR = 0.7f;
    final static int INITIAL_SIZE = 512;

    final Tablespace tablespace;
    final String yamcsInstance;

    private int size = 0;
    private Entry[] entries;

    // hash tables for pid and fqn; the values are indexes in the entries array
    private int[] pidHtable;
    private int[] fqnHtable;
    private int threshold;

    // used as parameterId (tbsIndex) for the time records
    int timeParameterId;
    public static final String TIME_PARAMETER_FQN = "__time_parameter_";
    static final int UNSET = -1;

    private ParameterGroupIdDb pgidMap;

    ParameterIdDb(String yamcsInstance, Tablespace tablespace, boolean sparseGroups, double minGroupOverlap)
            throws RocksDBException, IOException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;

        entries = new Entry[INITIAL_SIZE];
        pidHtable = new int[INITIAL_SIZE];
        fqnHtable = new int[INITIAL_SIZE];
        Arrays.fill(pidHtable, UNSET);
        Arrays.fill(fqnHtable, UNSET);
        size = 0;
        threshold = (int) (LOAD_FACTOR * INITIAL_SIZE);

        readDb();
        pgidMap = new ParameterGroupIdDb(yamcsInstance, tablespace, sparseGroups, minGroupOverlap);
    }

    /**
     * Get the mapping from (parameterFqn, type) to pid
     * <p>
     * It creates it if it does not exist
     * 
     * @param paramFqn
     * @param engType
     * @param rawType
     * 
     * @return a parameter id for the given parameter name and type
     * @throws ParameterArchiveException
     *             if there was an error creating and storing a new parameter_id
     */
    public synchronized int createAndGet(String paramFqn, Value.Type engType, Value.Type rawType)
            throws ParameterArchiveException {
        int type = numericType(engType, rawType);

        Entry e = getCachedEntry(paramFqn, type);
        if (e == null) {
            int pid = addParameterToRocksdb(paramFqn, type);
            e = new Entry(pid, type, paramFqn);
            addEntry(e);
        }

        return e.pid;
    }

    public ParameterGroupIdDb getParameterGroupIdDb() {
        return pgidMap;
    }

    private Entry getCachedEntry(String paramFqn, int type) {
        int fhash = paramFqn.hashCode() & (fqnHtable.length - 1);
        int idx = fqnHtable[fhash];
        if (idx == UNSET) {
            return null;
        } else {
            Entry e = entries[idx];
            while (e != null && !e.equals(paramFqn, type)) {
                e = e.nextFqn;
            }
            return e;
        }
    }

    private void addEntry(Entry e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (size > threshold) {
            resizeHashTables();
        }
        if (size == entries.length) {
            resizeEntries();
        }
        int idx = size++;
        entries[idx] = e;
        addHash(idx, e);
    }

    private void addHash(int idx, Entry e) {
        int fhash = e.fqn.hashCode() & (fqnHtable.length - 1);
        int phash = e.pid & (pidHtable.length - 1);

        int fidx = fqnHtable[fhash];
        int pidx = pidHtable[phash];
        Entry e1, e2;

        if (fidx == UNSET) {
            fqnHtable[fhash] = idx;
        } else {
            e1 = entries[fidx];
            while ((e2 = e1.nextFqn) != null) {
                e1 = e2;
            }
            e1.nextFqn = e;
        }

        if (pidx == UNSET) {
            pidHtable[phash] = idx;
        } else {
            e1 = entries[pidx];
            while ((e2 = e1.nextPid) != null) {
                e1 = e2;
            }
            e1.nextPid = e;
        }
    }

    private void resizeHashTables() {
        int n = fqnHtable.length;
        if (n == Integer.MAX_VALUE) {
            throw new ParameterArchiveException("too many parameters");
        }
        n *= 2;
        fqnHtable = new int[n];
        pidHtable = new int[n];
        threshold = (int) (LOAD_FACTOR * n);

        // rehash all entries
        Arrays.fill(fqnHtable, UNSET);
        Arrays.fill(pidHtable, UNSET);

        for (int i = 0; i < size; i++) {
            entries[i].nextFqn = entries[i].nextPid = null;
        }
        for (int i = 0; i < size; i++) {
            addHash(i, entries[i]);
        }
    }

    private void resizeEntries() {
        entries = Arrays.copyOf(entries, 2 * entries.length);
    }

    /**
     * get a parameter id for a parameter that only has engineering value
     * 
     * @param paramFqn
     * @param engType
     * @return a parameter id for the given parameter name and type
     */
    public int createAndGet(String paramFqn, Type engType) {
        return createAndGet(paramFqn, engType, null);
    }

    private int addParameterToRocksdb(String paramFqn, int type) {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.PARCHIVE_DATA)
                .setParameterFqn(paramFqn).setParameterType(type);
        TablespaceRecord tr;
        try {
            tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            return tr.getTbsIndex();
        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot store key for new parameter id", e);
        }
    }

    // compose a numeric type from engType and rawType (we assume that no more than 2^15 types will ever exist)
    private int numericType(Value.Type engType, Value.Type rawType) {
        int et = (engType == null) ? 0xFFFF : engType.getNumber();
        int rt = (rawType == null) ? 0xFFFF : rawType.getNumber();
        return et << 16 | rt;
    }

    private void readDb() throws RocksDBException, IOException {
        List<TablespaceRecord> trlist = tablespace.filter(TablespaceRecord.Type.PARCHIVE_DATA, yamcsInstance,
                (trb) -> true);
        if (trlist.isEmpty()) {
            // new database- create a record for the time parameter
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(TablespaceRecord.Type.PARCHIVE_DATA)
                    .setParameterFqn(TIME_PARAMETER_FQN);
            TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            timeParameterId = tr.getTbsIndex();
        } else {
            for (TablespaceRecord tr : trlist) {
                String paraName = tr.getParameterFqn();
                if (TIME_PARAMETER_FQN.equals(paraName)) {
                    timeParameterId = tr.getTbsIndex();
                } else {
                    int pid = tr.getTbsIndex();
                    int type = tr.getParameterType();
                    addEntry(new Entry(pid, type, paraName));
                }
            }
        }

        trlist = tablespace.filter(TablespaceRecord.Type.PARCHIVE_AGGARR_INFO, yamcsInstance,
                (trb) -> true);
        for (TablespaceRecord tr : trlist) {
            String paraFqn = tr.getParameterFqn();
            int pid = tr.getTbsIndex();

            IntArray memberIds = new IntArray();
            for (int i = 0; i < tr.getMemberIdCount(); i++) {
                memberIds.add(tr.getMemberId(i));
            }

            int numericType;
            if (tr.hasNumericType()) {
                numericType = tr.getNumericType();
            } else {// workaround if the parameter was created before this has been implemented
                numericType = Value.ARRAYVALUE_FIELD_NUMBER;
            }
            addEntry(new AggArrayEntry(pid, paraFqn, numericType, memberIds));

        }
    }

    static Value.Type getEngType(int x) {
        int et = x >> 16;
        if (et == 0xFFFF) {
            return null;
        } else
            return Value.Type.forNumber(et);
    }

    static Value.Type getRawType(int x) {
        int rt = x & 0xFFFF;
        if (rt == 0xFFFF) {
            return null;
        } else
            return Value.Type.forNumber(rt);
    }

    static boolean hasRawType(int x) {
        return (x & 0xFFFF) != 0xFFFF;
    }

    public int getTimeParameterId() {
        return timeParameterId;
    }

    public void print(PrintStream out) {
        for (Entry me : entries) {
            String pname = me.fqn;
            out.print(pname + ": ");
            out.println("\t(" + getEngType(me.type) + ", " + getRawType(me.type) + ") -> " + me.pid);
        }
    }

    /*
     * return the number of unique parameters
     */
    public int size() {
        return size;
    }

    /**
     * Get all parameters ids for a given qualified name
     * 
     * return null if no parameter id exists for that fqn.
     * 
     * 
     * @param fqn
     *            - fully qualified name of the parameter for which the ids are returned
     * @return all parameters ids for a given qualified name or null if no parameter id exists for that fqn
     */
    public synchronized ParameterId[] get(String fqn) {
        int fhash = fqn.hashCode() & (fqnHtable.length - 1);
        int idx = fqnHtable[fhash];
        if (idx == UNSET) {
            return null;
        }
        Entry e = entries[idx];
        int n = 0;
        Entry e1 = e;
        while (e1 != null) {
            if (fqn.equals(e1.fqn)) {
                n++;
            }
            e1 = e1.nextFqn;
        }

        ParameterId[] r = new ParameterId[n];
        e1 = e;
        int i = 0;
        while (e1 != null && i < n) {
            if (fqn.equals(e1.fqn)) {
                r[i++] = e1;
            }
            e1 = e1.nextFqn;
        }
        return r;
    }

    /**
     * returns the parameter FQN for the given parameterId
     * 
     * @param parameterId
     * 
     * @return parameterFQN or null if there is no parameter with the given id
     */
    public String getParameterFqnById(int parameterId) {
        Entry e = getCachedEntryById(parameterId);
        return e == null ? null : e.fqn;
    }

    /**
     * returns ParameterId based on numeric id or null if it does not exist
     */
    public ParameterId getParameterId(int pid) {
        Entry e = getCachedEntryById(pid);
        return e;
    }

    public Entry getCachedEntryById(int pid) {
        int phash = pid & (pidHtable.length - 1);
        int idx = pidHtable[phash];
        if (idx == UNSET) {
            return null;
        } else {
            Entry e = entries[idx];
            while (e != null && e.pid != pid) {
                e = e.nextPid;
            }
            return e;
        }
    }

    /**
     * Iterate over the parameter database, calling the function with the fqn and parameter id.
     * <p>
     * The iteration will continue as long as the function returns true
     * 
     * @param consumer
     */
    public void iterate(BiFunction<String, ParameterId, Boolean> consumer) {
        for (int i = 0; i < size; i++) {
            Entry e = entries[i];
            if (!consumer.apply(e.fqn, e)) {
                return;
            }
        }
    }

    /**
     * Creates (if not already existing) an id for the aggregate or array parameter with the given qualified name and
     * member ids.
     * <p>
     * If another parameter with the same name exists and the aggArray is either a subset or superset of the members of
     * the existing parameter, it is considered the same and is returned.
     * <p>
     * For example an array will have an id for each index of its elements a[0], a[1],.. The aggArray for that parameter
     * will consist of the list of ids corresponding to the value which had the maximum number of elements.
     * 
     * <p>
     * If a new value is encountered having more elements than the previous maximum, we do not want to create a new id
     * for that parameter. We do however want to create a new id if the elements have a different type (and thus a[i]
     * will have a different id)
     * 
     * @param paramFqn
     *            - qualified name of the parameter
     * @param engType
     *            - the type of engineering value (ARRAY or AGGREGATE)
     * @param rawType
     *            - the type of the raw value - null if the parameter has no raw value
     * @param components
     *            - the parameter ids of the components of the aggregate or array
     * @return
     */
    public synchronized int createAndGetAggrray(String paramFqn, Value.Type engType, Value.Type rawType,
            IntArray components) {
        components.sort();
        int pid = -1;
        int numericType = numericType(engType, rawType);

        int fhash = paramFqn.hashCode() & (fqnHtable.length - 1);
        int idx = fqnHtable[fhash];
        if (idx == UNSET) {
            pid = addAggArray(paramFqn, components);
            addEntry(new AggArrayEntry(pid, paramFqn, numericType, components));
        } else {
            Entry e = entries[idx];
            while (e != null) {
                if (paramFqn.equals(e.fqn) && (e instanceof AggArrayEntry agge)) {
                    int c = IntArray.compare(agge.components, components);
                    if (c != -1) {
                        pid = e.pid;
                        if (c == 1) {
                            agge.components = components;
                            modifyAggArray(pid, paramFqn, numericType, components);
                        }
                        break;
                    }
                }
                e = e.nextFqn;
            }
        }
        if (pid == -1) {
            pid = addAggArray(paramFqn, components);
            addEntry(new AggArrayEntry(pid, paramFqn, numericType, components));
        }

        return pid;
    }

    private int addAggArray(String paramFqn, IntArray aggArray) {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                .setType(TablespaceRecord.Type.PARCHIVE_AGGARR_INFO)
                .setParameterFqn(paramFqn);
        aggArray.stream().forEach(x -> trb.addMemberId(x));

        TablespaceRecord tr;
        try {
            tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            return tr.getTbsIndex();

        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot store information for new aggregate/array parameter id", e);
        }
    }

    private void modifyAggArray(int pid, String paramFqn, int numericType, IntArray aggArray) {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                .setTbsIndex(pid)
                .setType(TablespaceRecord.Type.PARCHIVE_AGGARR_INFO)
                .setParameterFqn(paramFqn)
                .setNumericType(numericType);
        aggArray.stream().forEach(x -> trb.addMemberId(x));

        TablespaceRecord tr;
        try {
            tr = tablespace.updateRecord(yamcsInstance, trb);
            tr.getTbsIndex();

        } catch (RocksDBException e) {
            throw new ParameterArchiveException("Cannot store information for new aggregate/array parameter id", e);
        }
    }

    /**
     * Get the array components of aggregate/array parameter pid which are members of the group gid
     * 
     * @param aggrayPid
     * @param gid
     * @return
     */
    public synchronized ParameterId[] getAggarrayComponents(int aggrayPid, int gid) {
        Entry e = getCachedEntryById(aggrayPid);
        if (e == null) {
            throw new IllegalArgumentException("Invalid parameter id " + aggrayPid);
        }

        if (!(e instanceof AggArrayEntry)) {
            throw new IllegalArgumentException("parameter id " + aggrayPid + " is not an id of an aggregate or array");
        }
        IntArray gidMembers = pgidMap.getParameterGroup(gid);

        return ((AggArrayEntry) e).components.stream()
                .filter(pid -> gidMembers.binarySearch(pid) >= 0)
                .mapToObj(pid -> getParameterId(pid))
                .toArray(ParameterId[]::new);
    }

    /**
     * returns an array of all parameter ids (including the time pseudo-parameter id)
     */
    public IntArray getAllPids() {
        IntArray r = new IntArray(size + 1);
        r.add(timeParameterId);
        for (var e : entries) {
            if (e != null) {
                r.add(e.pid);
            }
        }
        return r;
    }

    static class Entry implements ParameterId {
        final int pid;
        final int type;
        final String fqn;

        Entry nextPid;
        Entry nextFqn;

        public Entry(int pid, int numericType, String fqn) {
            this.pid = pid;
            this.type = numericType;
            this.fqn = fqn;
        }

        public boolean equals(String paramFqn, int type) {
            return fqn.equals(paramFqn) && this.type == type;
        }

        @Override
        public Type getRawType() {
            return ParameterIdDb.getRawType(type);
        }

        @Override
        public Type getEngType() {
            return ParameterIdDb.getEngType(type);
        }

        @Override
        public int getPid() {
            return pid;
        }

        @Override
        public String getParamFqn() {
            return fqn;
        }

        @Override
        public boolean isSimple() {
            return true;
        }

        @Override
        public boolean hasRawValue() {
            return ParameterIdDb.hasRawType(type);
        }

        @Override
        public IntArray getComponents() {
            return null;
        }

        @Override
        public String toString() {
            return "Entry [pid=" + pid + ", fqn=" + fqn + ", engType: " + getEngType()
                    + ", rawType: " + getRawType() + "]";
        }
    }

    static class AggArrayEntry extends Entry {

        IntArray components;

        public AggArrayEntry(int pid, String fqn, int numericType, IntArray components) {
            super(pid, numericType, fqn);
            if (components.size() == 0) {
                throw new IllegalArgumentException(
                        "the aggregate or array parameter has to have at least one component");
            }
            this.components = components;
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IntArray getComponents() {
            return components;
        }

        @Override
        public String toString() {
            return "AggArrayEntry [pid=" + pid + ", fqn=" + fqn + "+components=" + components + "]";
        }
    }

}
