package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;

/**
 * Cache for the last known value of each parameter.
 * <p>
 * Can also stored a number of n values for certain parameters (required by algorithms and match criteria)
 * <p>
 * it uses a readwrite lock to synchronize access from multiple threads.
 *
 */
public class LastValueCache {
    HashMap<Parameter, ParameterValue> constants = new HashMap<>();
    HashMap<Parameter, ParameterValue> params = new HashMap<>();
    HashMap<Parameter, ParamBuffer> bufferedParams = new HashMap<>();

    ReadWriteLock lock = new ReentrantReadWriteLock();

    public LastValueCache() {
    }

    public LastValueCache(Collection<ParameterValue> constants) {
        constants.forEach(pv -> this.constants.put(pv.getParameter(), pv));
    }

    /**
     * Returns the latest known value for p or null if there is none.
     * 
     * @param param
     * @return
     */
    public ParameterValue getValue(Parameter param) {
        if (param.getDataSource() == DataSource.CONSTANT) {
            return constants.get(param);
        }

        lock.readLock().lock();
        try {
            ParamBuffer pb = bufferedParams.get(param);
            if (pb != null) {
                return pb.end();
            } else {
                return params.get(param);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * return the n'th newest value or null if no such a value exist. n has to be greater or equal with 0.
     * <p>
     * If n=0 it is equivalent with {@link LastValueCache#getValue(Parameter)}
     * <p>
     * If n<0 but buffering is not enabled for the parameter or the buffer capacity is smaller than -n+1, an
     * IllegalStateException will be thrown
     * 
     * @throws IllegalArgumentException
     *             if n>0 or if n<0 and the parameter is constant
     * @throws IllegalStateException
     *             if buffering is not enabled or the buffer capacity is smaller than -n+1
     */
    public ParameterValue getValueFromEnd(Parameter param, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n has to be positive:" + n);
        }
        if (param.getDataSource() == DataSource.CONSTANT) {
            if (n > 0) {
                throw new IllegalArgumentException("Cannot request buffered data for constant prameters");
            } else {
                return constants.get(param);
            }
        }

        lock.readLock().lock();
        try {
            if (n == 0) {
                return getValue(param);
            }

            ParamBuffer pb = bufferedParams.get(param);
            if (pb == null) {
                throw new IllegalStateException("Buffering not enabled for " + param.getQualifiedName());
            }
            if (pb.capacity() < -n + 1) {
                throw new IllegalStateException("Buffering enabled for " + param.getQualifiedName()
                        + " but it's capacity " + pb.capacity() + " is smaller than " + (n + 1));
            }
            return pb.nthFromEnd(n);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Configure the parameter cache to remember at least capacity values for the parameter.
     * <p>
     * The size has to be at least 2 (because size 1 is by default)
     * 
     * @throws IllegalArgumentException
     *             if the capacity is smaller than 2 or the parameter is a constant.
     */
    public void enableBuffering(Parameter param, int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("Buffer capacity has to be at least 2");
        }
        if (param.getDataSource() == DataSource.CONSTANT) {
            throw new IllegalArgumentException("Cannot enable buffering for constant parameters");
        }
        lock.writeLock().lock();
        try {

            ParamBuffer pb = bufferedParams.get(param);
            if (pb == null) {
                pb = new ParamBuffer(capacity);
                ParameterValue pv = params.remove(param);
                if (pv != null) {
                    pb.add(pv);
                }
                bufferedParams.put(param, pb);
            } else {
                if (capacity <= pb.capacity()) {
                    return;
                } else {
                    ParamBuffer pb1 = new ParamBuffer(pb, capacity);

                    bufferedParams.put(param, pb1);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a new value. If buffering is enabled, the value is added to the buffer, otherwise it replaces the old value
     * (if any)
     * 
     * @param pv
     */
    public void add(ParameterValue pv) {
        lock.writeLock().lock();
        try {
            doAdd(pv);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doAdd(ParameterValue pv) {
        Parameter param = pv.getParameter();
        if (param.getDataSource() == DataSource.CONSTANT) {
            throw new IllegalArgumentException("Cannot add constants (they can only be added in the constructor)");
        }
        ParamBuffer pb = bufferedParams.get(param);
        if (pb == null) {
            params.put(param, pv);
        } else {
            pb.add(pv);
        }
    }

    /**
     * Add all parameters to the cache
     * 
     * @param newValues
     */
    public void addAll(Collection<ParameterValue> newValues) {
        lock.writeLock().lock();
        try {
            newValues.forEach(pv -> doAdd(pv));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return constants.size() + params.size() + bufferedParams.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * returns all the values from the cache
     * 
     * @return
     */
    public Collection<ParameterValue> getValues() {
        lock.readLock().lock();
        try {
            return params.values();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * returns a list of parameter values for all the parameters having the persistence flag set
     * <p>
     * The list may be empty if no parameter has the flag set
     */
    public List<ParameterValue> getValuesToBePersisted() {
        List<ParameterValue> pvList = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (var entry : bufferedParams.entrySet()) {
                if (entry.getKey().isPersistent()) {
                    var pv = entry.getValue().end();
                    if (pv != null) {
                        pvList.add(pv);
                    }
                }
            }
            for (var entry : params.entrySet()) {
                if (entry.getKey().isPersistent()) {
                    pvList.add(entry.getValue());
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return pvList;
    }

    // fixed size circular buffer
    static class ParamBuffer {
        final ParameterValue[] data;
        int end = -1;

        ParamBuffer(int capacity) {
            this.data = new ParameterValue[capacity];
        }

        ParamBuffer(ParamBuffer pb1, int capacity) {
            this.data = Arrays.copyOf(pb1.data, capacity);
            this.end = pb1.end;
        }

        public int capacity() {
            return data.length;
        }

        public ParameterValue end() {
            return end == -1 ? null : data[end];
        }

        /**
         * Return the element end-n (n is positive)
         * 
         */
        public ParameterValue nthFromEnd(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("n has to be positive");
            }

            int k = end - n;
            if (k < 0) {
                k += data.length;
            }
            return data[k];
        }

        public void add(ParameterValue pv) {
            end = incr(end);
            data[end] = pv;
        }

        private int incr(int k) {
            int k1 = k + 1;
            return k1 < data.length ? k1 : k1 - data.length;
        }

        public boolean isEmpty() {
            return end == -1;
        }

        @Override
        public String toString() {

            if (end == -1) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int k = end;
            while (true) {
                ParameterValue pv = data[k];
                if (pv == null) {
                    sb.append("null");
                } else {
                    sb.append(pv.getParameter().getName())
                            .append("(")
                            .append(pv.getRawValue())
                            .append(", ")
                            .append(pv.getEngValue())
                            .append(")");
                }
                k = incr(k);
                if (k == end) {
                    break;
                }

            }
            return sb.toString();
        }
    }

}
