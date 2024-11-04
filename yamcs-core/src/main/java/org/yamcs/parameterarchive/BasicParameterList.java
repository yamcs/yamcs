package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.IntHashSet;

/**
 * Builds list of parameter id and parameter value.
 * <p>
 * The list can be sorted on parameter ids using the {@link #sort()} method
 * <p>
 * Any parameter which is not in the ParameterIdDb will be added. This includes the aggregates and arrays.
 * <p>
 * In order to handle the case when the same parameter has multiple values (with the same timestamp), we have a chain of
 * these lists. The first list in the chain contains maximum of parameter values, the next in chain contains the next
 * values for the parameters that have already some value in the first list and so on such that each list of the chain
 * contains only one single value for each parameter.
 * <p>
 * Note that elements of arrays are considered different parameters, not duplicates of the same parameter.
 * 
 */
class BasicParameterList {
    final ParameterIdDb parameterIdMap;
    final IntArray idArray;
    // unique parameter ids for this list
    final IntHashSet uniquePids = new IntHashSet();

    final List<BasicParameterValue> pvList;
    BasicParameterList next = null;

    public BasicParameterList(ParameterIdDb parameterIdMap) {
        this.parameterIdMap = parameterIdMap;
        this.idArray = new IntArray();
        this.pvList = new ArrayList<>();
    }

    // used for unit tests
    BasicParameterList(IntArray idArray, List<BasicParameterValue> pvList) {
        this.idArray = idArray;
        this.parameterIdMap = null;
        this.pvList = pvList;
    }

    // add the parameter to the list but also expand if it is an aggregate or array
    void add(ParameterValue pv) {
        String fqn = pv.getParameterQualifiedName();
        if (pv.getEngValue() instanceof AggregateValue) {
            IntArray aggrray = new IntArray();
            add(fqn, pv, aggrray);

            Type engType = pv.getEngValue().getType();
            Type rawType = (pv.getRawValue() == null) ? null : pv.getRawValue().getType();

            parameterIdMap.createAndGetAggrray(fqn, engType, rawType, aggrray);
        } else if (pv.getEngValue() instanceof ArrayValue arrv) {
            // for the moment we have no way to store empty arrays in the parameter archive, so we just skip over
            if (!arrv.isEmpty()) {
                IntArray aggrray = new IntArray();
                add(fqn, pv, aggrray);
                Type engType = pv.getEngValue().getType();
                Type rawType = (pv.getRawValue() == null) ? null : pv.getRawValue().getType();

                parameterIdMap.createAndGetAggrray(fqn, engType, rawType, aggrray);
            }
        } else {
            add(fqn, pv, null);
        }
    }

    void add(String name, BasicParameterValue pv, IntArray aggrray) {
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        Type engType = engValue.getType();
        Type rawType = (rawValue == null) ? null : rawValue.getType();

        if (engValue instanceof AggregateValue) {
            addAggregate(name, pv, aggrray);
        } else if (engValue instanceof ArrayValue) {
            addArray(name, pv, aggrray);
        } else {
            int parameterId = parameterIdMap.createAndGet(name, engType, rawType);
            doAdd(parameterId, pv);
            if (aggrray != null) {
                aggrray.add(parameterId);
            }
        }
    }

    private void addAggregate(String name, BasicParameterValue pv, IntArray aggrray) {
        AggregateValue engValue = (AggregateValue) pv.getEngValue();
        AggregateValue rawValue = (AggregateValue) pv.getRawValue();

        int n = engValue.numMembers();
        for (int i = 0; i < n; i++) {
            String mname = engValue.getMemberName(i);
            Value mEngvalue = engValue.getMemberValue(i);
            BasicParameterValue pv1 = new BasicParameterValue();
            pv1.setStatus(pv.getStatus());
            pv1.setEngValue(mEngvalue);
            pv1.setGenerationTime(pv.getGenerationTime());

            if (rawValue != null) {
                Value mRawValue = rawValue.getMemberValue(i);
                pv1.setRawValue(mRawValue);
            }
            add(name + "." + mname, pv1, aggrray);
        }
    }

    private void addArray(String name, BasicParameterValue pv, IntArray aggrray) {
        ArrayValue engValue = (ArrayValue) pv.getEngValue();
        ArrayValue rawValue = (ArrayValue) pv.getRawValue();

        int[] dim = engValue.getDimensions();
        int n = dim.length;
        int[] idx = new int[n];

        while (true) {
            String mname = toIndexSpecifier(idx);
            Value mEngvalue = engValue.getElementValue(idx);
            BasicParameterValue pv1 = new BasicParameterValue();
            pv1.setStatus(pv.getStatus());
            pv1.setEngValue(mEngvalue);
            if (rawValue != null) {
                Value mRawValue = rawValue.getElementValue(idx);
                pv1.setRawValue(mRawValue);
            }
            add(name + mname, pv1, aggrray);

            int k = n - 1;
            while (k >= 0 && ++idx[k] >= dim[k]) {
                k--;
            }
            if (k < 0) {
                break;
            }
            while (++k < n) {
                idx[k] = 0;
            }
        }
    }

    private void doAdd(int pid, BasicParameterValue pv) {
        if (uniquePids.add(pid)) {
            idArray.add(pid);
            pvList.add(pv);
        } else {
            if (next == null) {
                next = new BasicParameterList(parameterIdMap);
            }
            next.doAdd(pid, pv);
        }
    }

    private static String toIndexSpecifier(int[] dims) {
        String[] dimStrings = Arrays.stream(dims).mapToObj(String::valueOf).toArray(String[]::new);
        return "[" + String.join("][", dimStrings) + "]";
    }

    public int size() {
        return idArray.size();
    }

    public IntArray getPids() {
        return idArray;
    }

    public List<BasicParameterValue> getValues() {
        return pvList;
    }

    /**
     * returns the next list containing values for parameters that already had a value in this list
     * <p>
     * If there are no parameters with two values, this returns null
     */
    public BasicParameterList next() {
        return next;
    }

    // sort the parameters by id
    public void sort() {
        idArray.sort(pvList);
        if (next != null) {
            next.sort();
        }
    }

    public String toString() {
        return pvList.toString();
    }
}
