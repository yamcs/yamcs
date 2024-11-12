package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

/**
 * This is another implementation of the parameter cache using arrays to store primitive values (instead of storing
 * {@link Value}).
 * <p>
 * It should consume less memory than {@link ParameterCacheImpl} in case of large number of parameter values.
 *
 */
public class ArrayParameterCache implements ParameterCache {
    SimpleParameterIdMap pidMap = new SimpleParameterIdMap();
    final Log log;
    long cacheStartTime = 0;
    ConcurrentHashMap<SortedIntArray, ParameterValueTable> tables = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Parameter, Boolean> parametersToCache;
    final ParameterCacheConfig cacheConfig;

    ArrayParameterCache(String instance, ParameterCacheConfig cacheConfig) {
        log = new Log(this.getClass(), instance);
        this.cacheConfig = cacheConfig;
        parametersToCache = cacheConfig.cacheAll ? null : new ConcurrentHashMap<>();
    }

    @Override
    public void update(Collection<ParameterValue> pvs) {
        Map<Long, SortedParameterList> m = new HashMap<>();
        for (ParameterValue pv : pvs) {
            long t = pv.getGenerationTime();
            if (t < cacheStartTime) {
                continue;
            }
            if (!(cacheConfig.cacheAll || parametersToCache.containsKey(pv.getParameter()))) {
                continue;
            }
            SortedParameterList l = m.get(t);
            if (l == null) {
                l = new SortedParameterList(pidMap);
                m.put(t, l);
            }
            l.add(pv);
        }
        long maxTimestamp = -1;
        for (Map.Entry<Long, SortedParameterList> entry : m.entrySet()) {
            long t = entry.getKey();
            SortedParameterList pvList = entry.getValue();
            addToCache(t, pvList);
            if (t > maxTimestamp) {
                maxTimestamp = t;
            }
        }
    }

    private void addToCache(long t, SortedParameterList pvList) {
        SortedIntArray sia = pvList.getParameterIdArray();
        ParameterValueTable table = tables.get(sia);
        if (table == null) {
            table = new ParameterValueTable(sia, cacheConfig.maxDuration, cacheConfig.maxNumEntries);
            ParameterValueTable table1 = tables.putIfAbsent(sia, table);
            if (table1 != null) {
                table = table1;
            }
        }

        table.add(t, pvList.getParameterValueList());
    }

    @Override
    public ParameterValue getLastValue(Parameter pdef) {
        List<ParameterId> pidlist = getParameterIds(pdef);
        ParameterValue result = null;
        long tmax = Long.MIN_VALUE;
        for (ParameterId p : pidlist) {
            SortedIntArray sia = findLatestTableContaining(p.id);
            if (sia == null) {
                continue;
            }

            ParameterValueTable table = tables.get(sia);
            long t = table.getLastTime();
            if (t < tmax) {
                continue;
            }

            ParameterValue pv = table.getLastValue(p);
            if (t == tmax) {
                if (result != null && result.getAcquisitionTime() < pv.getAcquisitionTime()) {
                    result = pv;
                }
            } else {
                result = pv;
            }

            tmax = t;
        }

        return result;

    }

    public ParameterValue getLastValue1(Parameter p) {
        Map<Integer, Integer> m = pidMap.get(p);
        if (m == null) {
            return null;
        }
        ParameterValue pvr = null;
        for (Map.Entry<Integer, Integer> me : m.entrySet()) {
            int type = me.getKey();
            int pid = me.getValue();

            for (Map.Entry<SortedIntArray, ParameterValueTable> me1 : tables.entrySet()) {
                SortedIntArray sai = me1.getKey();
                int pos = sai.search(pid);
                if (pos < 0) {
                    continue;
                }
                ParameterValueTable table = me1.getValue();
                long t = table.getLastTime();
                if (pvr != null && pvr.getGenerationTime() >= t) {
                    continue;
                }
                pvr = table.getLastValue(new ParameterId(p, pid,
                        SimpleParameterIdMap.getRawType(type), SimpleParameterIdMap.getEngType(type)));
            }
        }

        return pvr;
    }

    @Override
    public List<ParameterValue> getValues(List<Parameter> plist) {
        List<ParameterId> pidlist = getParameterIds(plist);

        List<ParameterValue> result = new ArrayList<>(plist.size());

        for (int i = 0; i < pidlist.size(); i++) {
            ParameterId p = pidlist.get(i);
            if (p == null) {
                continue;
            }
            pidlist.set(i, null);

            SortedIntArray sai = findLatestTableContaining(p.id);
            if (sai == null) {
                continue;
            }
            ParameterValueTable table = tables.get(sai);
            List<ParameterId> sublist = new ArrayList<>();
            sublist.add(p);
            for (int j = i + 1; j < pidlist.size(); j++) {
                ParameterId p1 = pidlist.get(j);
                if (p1 == null) {
                    continue;
                }
                if (sai.contains(p1.id)) {
                    sublist.add(p1);
                    pidlist.set(j, null);
                }
            }
            table.retrieveLastValues(sublist, result);
        }

        long now = TimeEncoding.getWallclockTime();
        // check expiration
        for (ParameterValue pv : result) {
            if ((pv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED) && pv.isExpired(now)) {
                pv.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
            }
        }

        return result;
    }

    private SortedIntArray findLatestTableContaining(int pid) {
        long tmax = Long.MIN_VALUE;
        SortedIntArray result = null;
        for (Map.Entry<SortedIntArray, ParameterValueTable> me : tables.entrySet()) {
            SortedIntArray sai = me.getKey();
            long t = me.getValue().getLastTime();
            if (sai.contains(pid) && t > tmax) {
                result = sai;
                tmax = t;
            }
        }
        return result;
    }

    @Override
    public List<ParameterValue> getAllValues(Parameter pdef) {
        return getAllValues(pdef, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Override
    public List<ParameterValue> getAllValues(Parameter pdef, long start, long stop) {
        List<ParameterId> pidlist = getParameterIds(pdef);
        List<ParameterValue> result = new ArrayList<>();
        int numTables = 0;
        for (ParameterId p : pidlist) {
            for (Map.Entry<SortedIntArray, ParameterValueTable> me : tables.entrySet()) {
                SortedIntArray sia = me.getKey();
                if (sia.contains(p.id)) {
                    numTables++;
                    me.getValue().retrieveAll(p, start, stop, result);
                }
            }
        }

        // if values are retrieved from multiple tables, we need to sort them by generation time
        // (in reverse order such that the newest is first)
        if (numTables > 1) {
            Collections.sort(result, (pv1, pv2) -> Long.compare(pv2.getGenerationTime(), pv1.getGenerationTime()));
        }
        if (result.isEmpty()) {
            return null;
        }
        return result;
    }

    private List<ParameterId> getParameterIds(List<Parameter> pdefList) {
        List<ParameterId> result = new ArrayList<>();
        for (Parameter pdef : pdefList) {
            Map<Integer, Integer> m = pidMap.get(pdef);
            if (m == null) {
                if (!cacheConfig.cacheAll) {
                    parametersToCache.put(pdef, Boolean.TRUE);
                }
            } else {
                for (Map.Entry<Integer, Integer> me : m.entrySet()) {
                    int pid = me.getValue();
                    int type = me.getKey();
                    result.add(new ParameterId(pdef, pid, SimpleParameterIdMap.getRawType(type),
                            SimpleParameterIdMap.getEngType(type)));
                }
            }
        }

        return result;
    }

    private List<ParameterId> getParameterIds(Parameter pdef) {
        List<ParameterId> result = new ArrayList<>();
        Map<Integer, Integer> m = pidMap.get(pdef);
        if (m == null) {
            if (!cacheConfig.cacheAll) {
                parametersToCache.put(pdef, Boolean.TRUE);
            }
        } else {
            for (Map.Entry<Integer, Integer> me : m.entrySet()) {
                int pid = me.getValue();
                int type = me.getKey();
                result.add(new ParameterId(pdef, pid, SimpleParameterIdMap.getRawType(type),
                        SimpleParameterIdMap.getEngType(type)));
            }
        }
        return result;
    }

    static class ParameterId {
        Parameter pdef;
        int id;
        Type engType;
        Type rawType;

        public ParameterId(Parameter p, int pid, Type rawType, Type engType) {
            this.pdef = p;
            this.id = pid;
            this.rawType = rawType;
            this.engType = engType;
        }

    }

    static class SimpleParameterIdMap {
        // parameter fqn -> parameter type -> parameter id
        Map<Parameter, Map<Integer, Integer>> p2pidCache = new HashMap<>();
        AtomicInteger pidGenerator = new AtomicInteger();

        public synchronized int createAndGet(Parameter param, Type engType, Type rawType) {
            int type = numericType(engType, rawType);

            Map<Integer, Integer> m = p2pidCache.get(param);
            if (m == null) {
                m = new HashMap<>();
                p2pidCache.put(param, m);
            }
            Integer pid = m.get(type);
            if (pid == null) {
                pid = pidGenerator.incrementAndGet();
                m.put(type, pid);
            }
            return pid;
        }

        Parameter getParameterForPid(int x) {
            for (Map.Entry<Parameter, Map<Integer, Integer>> me : p2pidCache.entrySet()) {
                for (Map.Entry<Integer, Integer> me1 : me.getValue().entrySet()) {
                    if (x == me1.getValue()) {
                        return me.getKey();
                    }
                }
            }
            return null;
        }

        public Map<Integer, Integer> get(Parameter p) {
            return p2pidCache.get(p);
        }

        // compose a numeric type from engType and rawType (we assume that no more than 2^15 types will ever exist)
        static int numericType(Type engType, Type rawType) {
            int et = (engType == null) ? 0xFFFF : engType.getNumber();
            int rt = (rawType == null) ? 0xFFFF : rawType.getNumber();
            return et << 16 | rt;
        }

        static Type getEngType(int numericType) {
            int et = numericType >> 16;
            if (et == 0xFFFF) {
                return null;
            } else {
                return Type.forNumber(et);
            }
        }

        static Type getRawType(int numericType) {
            int rt = numericType & 0xFFFF;
            if (rt == 0xFFFF) {
                return null;
            } else {
                return Type.forNumber(rt);
            }

        }
    }

    static class SortedParameterList {
        final SimpleParameterIdMap parameterIdMap;
        final SortedIntArray parameterIdArray = new SortedIntArray();
        final List<ParameterValue> sortedPvList = new ArrayList<>();

        public SortedParameterList(SimpleParameterIdMap paraId) {
            this.parameterIdMap = paraId;
        }

        public void add(ParameterValue pv) {
            Value engValue = pv.getEngValue();
            Value rawValue = pv.getRawValue();

            Type engType = (engValue == null) ? null : engValue.getType();
            Type rawType = (rawValue == null) ? null : rawValue.getType();
            int parameterId = parameterIdMap.createAndGet(pv.getParameter(), engType, rawType);

            int pos = parameterIdArray.insert(parameterId);
            sortedPvList.add(pos, pv);
        }

        public int size() {
            return parameterIdArray.size();
        }

        public SortedIntArray getParameterIdArray() {
            return parameterIdArray;
        }

        public List<ParameterValue> getParameterValueList() {
            return sortedPvList;
        }
    }

    /**
     * Stores values for list of parameters of predefined types
     * 
     * It's like a big table:
     * 
     * <pre>
     * t0, ev01, rv01, ps01, ev02, rv02, ps02 ... 
     * t1, ev11, rv11, ps11, ev12, rv12, ps12 ... ....
     * </pre>
     * 
     * where: t = timestamp ev = engineering value rv = raw value ps = parameter status
     *
     * Each column is stored as an array of different type (depending on the parameter type). The array works as a
     * circular list
     * 
     */
    static class ParameterValueTable {
        static final int MAX_NUM_ENTRIES = 1024;
        static final int INITIAL_CAPACITY = 16;
        long[] generationTimeColumn;
        final Object[] rawValueColumns;
        final Object[] engValueColumns;
        final Object[] statusColumns;
        final long[][] acquisitionTimeColumns;
        final int numParams;
        final long timeToCache;
        int head = 0;
        int tail = head;
        int maxNumEntries = MAX_NUM_ENTRIES;
        final SortedIntArray pids;

        ReadWriteLock lock = new ReentrantReadWriteLock();

        ParameterValueTable(SortedIntArray pids, long timeToCache, int maxNumEntries) {
            this.numParams = pids.size();
            this.pids = pids;
            this.rawValueColumns = new Object[numParams];
            this.engValueColumns = new Object[numParams];
            this.statusColumns = new Object[numParams];
            this.acquisitionTimeColumns = new long[numParams][];
            this.timeToCache = timeToCache;
            this.maxNumEntries = maxNumEntries;
        }

        private void init(List<ParameterValue> sortedPvList) {
            this.generationTimeColumn = new long[INITIAL_CAPACITY];
            for (int i = 0; i < sortedPvList.size(); i++) {
                ParameterValue pv = sortedPvList.get(i);
                Value v = pv.getEngValue();
                if (v != null) {
                    engValueColumns[i] = getNewColumn(v.getType());
                }
                Value rawV = pv.getRawValue();
                if (rawV != null) {
                    rawValueColumns[i] = getNewColumn(rawV.getType());
                }
                statusColumns[i] = new ParameterStatus[INITIAL_CAPACITY];
                acquisitionTimeColumns[i] = new long[INITIAL_CAPACITY];
            }
        }

        public void add(long t, List<ParameterValue> sortedPvList) {
            lock.writeLock().lock();
            try {
                if (numParams != sortedPvList.size()) {
                    throw new IllegalArgumentException("Invalid number of parameters, expected " + sortedPvList.size());
                }
                int _head = head;
                if (generationTimeColumn == null) {
                    init(sortedPvList);
                } else if (_head == tail) {
                    long t0 = generationTimeColumn[_head];
                    if (t < t0) {
                        // parameter older than the last one in the queue -> ignore
                        return;
                    }
                    boolean doubled = false;
                    if (t - t0 < timeToCache) {
                        doubled = doubleCapacity();
                        _head = head;
                    }
                    if (!doubled) {
                        tail = (tail + 1) & (generationTimeColumn.length - 1);
                    }
                }
                generationTimeColumn[_head] = t;

                for (int i = 0; i < numParams; i++) {
                    storeParameter(i, _head, sortedPvList.get(i));
                }
                head = (_head + 1) & (generationTimeColumn.length - 1);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public void retrieveLastValues(List<ParameterId> sublist, List<ParameterValue> result) {
            lock.readLock().lock();
            try {
                int row = (head - 1) & (generationTimeColumn.length - 1);
                for (ParameterId p : sublist) {
                    result.add(getParameterValue(row, p));
                }
            } finally {
                lock.readLock().unlock();
            }
        }

        public ParameterValue getLastValue(ParameterId p) {
            lock.readLock().lock();
            try {
                int row = (head - 1) & (generationTimeColumn.length - 1);
                return getParameterValue(row, p);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void retrieveAll(ParameterId p, long start, long stop, List<ParameterValue> result) {
            lock.readLock().lock();
            try {
                // col1 will be different than col2 when there are multiple values for the same parameter
                int col2 = pids.higherBound(p.id);
                int col1 = col2;
                while (col1 > 0 && pids.get(col1 - 1) == p.id) {
                    col1--;
                }
                int _tail = tail;
                int _head = head;
                int n = generationTimeColumn.length - 1;
                int row = _head;
                do {
                    row = (row - 1) & n;
                    for (int col = col2; col >= col1; col--) {
                        if (generationTimeColumn[row] > start && generationTimeColumn[row] <= stop) {
                            result.add(getParameterValue(row, col, p));
                        }
                    }
                } while (row != _tail);
            } finally {
                lock.readLock().unlock();
            }
        }

        private ParameterValue getParameterValue(int row, ParameterId p) {
            int col = pids.search(p.id);
            return getParameterValue(row, col, p);
        }

        private ParameterValue getParameterValue(int row, int col, ParameterId p) {
            ParameterValue pv = new ParameterValue(p.pdef);
            if (p.rawType != null) {
                pv.setRawValue(getValue(rawValueColumns[col], p.rawType, row));
            }

            if (p.engType != null) {
                pv.setEngValue(getValue(engValueColumns[col], p.engType, row));
            }
            pv.setGenerationTime(generationTimeColumn[row]);
            pv.setAcquisitionTime(acquisitionTimeColumns[col][row]);

            pv.setStatus((ParameterStatus) ((Object[]) statusColumns[col])[row]);
            return pv;
        }

        private Value getValue(Object o, Type type, int idx) {
            switch (type) {
            case BOOLEAN:
                return ValueUtility.getBooleanValue(((BitSet) o).get(idx));
            case DOUBLE:
                return ValueUtility.getDoubleValue(((double[]) o)[idx]);
            case FLOAT:
                return ValueUtility.getFloatValue(((float[]) o)[idx]);
            case SINT32:
                return ValueUtility.getSint32Value(((int[]) o)[idx]);
            case UINT32:
                return ValueUtility.getUint32Value(((int[]) o)[idx]);
            case SINT64:
                return ValueUtility.getSint64Value(((long[]) o)[idx]);
            case UINT64:
                return ValueUtility.getUint64Value(((long[]) o)[idx]);
            case TIMESTAMP:
                return ValueUtility.getTimestampValue(((long[]) o)[idx]);
            case STRING:
                return ValueUtility.getStringValue((String) ((Object[]) o)[idx]);
            case BINARY:
                return ValueUtility.getBinaryValue((byte[]) (((Object[]) o)[idx]));
            case AGGREGATE:
            case ARRAY:
            case ENUMERATED:
                return (Value) (((Object[]) o)[idx]);
            default:
                throw new IllegalStateException("Unknown type " + type);
            }
        }

        public long getLastTime() {
            lock.readLock().lock();
            try {
                int row = (head - 1) & (generationTimeColumn.length - 1);
                return generationTimeColumn[row];
            } finally {
                lock.readLock().unlock();
            }
        }

        private void storeParameter(int col, int row, ParameterValue pv) {
            Value v = pv.getEngValue();
            if (v != null) {
                storeValue(engValueColumns[col], row, v);
            }

            v = pv.getRawValue();
            if (v != null) {
                storeValue(rawValueColumns[col], row, v);
            }
            ParameterStatus status = pv.getStatus();

            if (row > 0) { // avoid filling up memory with identical ParameterStatus
                ParameterStatus prevStatus = (ParameterStatus) ((Object[]) statusColumns[col])[row - 1];
                if (prevStatus.equals(status)) {
                    status = prevStatus;
                }
            }
            ((Object[]) statusColumns[col])[row] = status;
            acquisitionTimeColumns[col][row] = pv.getAcquisitionTime();
        }

        private void storeValue(Object o, int pos, Value v) {
            Type type = v.getType();

            switch (type) {
            case BOOLEAN -> ((BitSet) o).set(pos, v.getBooleanValue());
            case DOUBLE -> ((double[]) o)[pos] = v.getDoubleValue();
            case FLOAT -> ((float[]) o)[pos] = v.getFloatValue();
            case SINT32 -> ((int[]) o)[pos] = v.getSint32Value();
            case UINT32 -> ((int[]) o)[pos] = v.getUint32Value();
            case SINT64 -> ((long[]) o)[pos] = v.getSint64Value();
            case UINT64 -> ((long[]) o)[pos] = v.getUint64Value();
            case TIMESTAMP -> ((long[]) o)[pos] = v.getTimestampValue();
            case STRING -> {
                Object[] objArray = (Object[]) o;
                String stringValue = v.getStringValue();
                if (pos > 0 && stringValue.equals(objArray[pos - 1])) {
                    objArray[pos] = objArray[pos - 1];
                } else {
                    objArray[pos] = stringValue;
                }
            }
            case BINARY -> {
                Object[] objArray = (Object[]) o;
                byte[] binaryValue = v.getBinaryValue();
                if (pos > 0 && binaryValue.equals(objArray[pos - 1])) {
                    objArray[pos] = objArray[pos - 1];
                } else {
                    objArray[pos] = binaryValue;
                }
            }
            case AGGREGATE, ARRAY, ENUMERATED -> {
                Object[] objArray = (Object[]) o;
                if (pos > 0 && v.equals(objArray[pos - 1])) {
                    objArray[pos] = objArray[pos - 1];
                } else {
                    objArray[pos] = v;
                }
            }
            default -> throw new IllegalStateException("Unknown type " + type);
            }
        }

        private Object getNewColumn(Type type) {
            switch (type) {
            case BOOLEAN:
                return new BitSet(INITIAL_CAPACITY);
            case DOUBLE:
                return new double[INITIAL_CAPACITY];
            case FLOAT:
                return new float[INITIAL_CAPACITY];
            case SINT32:
            case UINT32:
                return new int[INITIAL_CAPACITY];
            case SINT64:
            case UINT64:
            case TIMESTAMP:
                return new long[INITIAL_CAPACITY];
            case STRING:
            case BINARY:
            case AGGREGATE:
            case ARRAY:
            case ENUMERATED:
                return new Object[INITIAL_CAPACITY];
            default:
                throw new IllegalStateException("Unknown type " + type);
            }
        }

        private boolean doubleCapacity() {
            int capacity = generationTimeColumn.length;
            if (capacity >= maxNumEntries) {
                return false;
            }

            int newCapacity = 2 * capacity;

            long[] o2 = new long[newCapacity];
            System.arraycopy(generationTimeColumn, head, o2, 0, capacity - head);
            System.arraycopy(generationTimeColumn, 0, o2, capacity - head, head);
            generationTimeColumn = o2;

            for (int i = 0; i < numParams; i++) {
                Object c = engValueColumns[i];
                if (c != null) {
                    engValueColumns[i] = growCapacity(c, newCapacity);
                }

                c = rawValueColumns[i];
                if (c != null) {
                    rawValueColumns[i] = growCapacity(c, newCapacity);
                }

                c = statusColumns[i];
                if (c != null) {
                    statusColumns[i] = growCapacity(c, newCapacity);
                }
                c = acquisitionTimeColumns[i];
                acquisitionTimeColumns[i] = (long[]) growCapacity(c, newCapacity);
            }
            tail = 0;
            head = capacity;
            return true;
        }

        private Object growCapacity(Object o, int newCapacity) {
            if (o instanceof int[]) {
                int[] o1 = (int[]) o;
                int[] o2 = new int[newCapacity];
                System.arraycopy(o1, head, o2, 0, o1.length - head);
                System.arraycopy(o1, 0, o2, o1.length - head, head);
                return o2;
            } else if (o instanceof double[]) {
                double[] o1 = (double[]) o;
                double[] o2 = new double[newCapacity];
                System.arraycopy(o1, head, o2, 0, o1.length - head);
                System.arraycopy(o1, 0, o2, o1.length - head, head);
                return o2;
            } else if (o instanceof float[]) {
                float[] o1 = (float[]) o;
                float[] o2 = new float[newCapacity];
                System.arraycopy(o1, head, o2, 0, o1.length - head);
                System.arraycopy(o1, 0, o2, o1.length - head, head);
                return o2;
            } else if (o instanceof long[]) {
                long[] o1 = (long[]) o;
                long[] o2 = new long[newCapacity];
                System.arraycopy(o1, head, o2, 0, o1.length - head);
                System.arraycopy(o1, 0, o2, o1.length - head, head);
                return o2;
            } else if (o instanceof Object[]) {
                Object[] o1 = (Object[]) o;
                Object[] o2 = new Object[newCapacity];
                System.arraycopy(o1, head, o2, 0, o1.length - head);
                System.arraycopy(o1, 0, o2, o1.length - head, head);
                return o2;
            } else if (o instanceof BitSet) {
                return o;
            } else {
                throw new IllegalArgumentException("Cannot double objects of type " + o.getClass());
            }
        }
    }

    @Override
    public void clear() {
        tables.clear();
    }
}
