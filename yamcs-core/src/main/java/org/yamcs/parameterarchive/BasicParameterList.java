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

/**
 * Builds list of parameter id and parameter value,
 * can be sorted on parameter ids
 */
class BasicParameterList {
    final ParameterIdDb parameterIdMap;
    final IntArray idArray = new IntArray();
    final List<BasicParameterValue> pvList = new ArrayList<>();

    public BasicParameterList(ParameterIdDb parameterIdMap) {
        this.parameterIdMap = parameterIdMap;
    }

    // add the parameter to the list but also expand if it is an aggregate or array
    void add(ParameterValue pv) {
        String fqn = pv.getParameterQualifiedNamed();
        add(fqn, pv);
    }

    void add(String name, BasicParameterValue pv) {
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        Type engType = engValue.getType();
        Type rawType = (rawValue == null) ? null : rawValue.getType();

        if (engValue instanceof AggregateValue) {
            addAggregate(name, pv);
        } else if (engValue instanceof ArrayValue) {
            addArray(name, pv);
        } else {
            int parameterId = parameterIdMap.createAndGet(name, engType, rawType);
            idArray.add(parameterId);
            pvList.add(pv);
        }
    }

    private void addAggregate(String name, BasicParameterValue pv) {
        AggregateValue engValue = (AggregateValue) pv.getEngValue();
        AggregateValue rawValue = (AggregateValue) pv.getRawValue();

        int n = engValue.numMembers();
        for (int i = 0; i < n; i++) {
            String mname = engValue.getMemberName(i);
            Value mEngvalue = engValue.getMemberValue(i);
            BasicParameterValue pv1 = new BasicParameterValue();
            pv1.setStatus(pv.getStatus());
            pv1.setEngineeringValue(mEngvalue);
            pv1.setGenerationTime(pv.getGenerationTime());

            if (rawValue != null) {
                Value mRawValue = rawValue.getMemberValue(i);
                pv1.setRawValue(mRawValue);
            }
            add(name + "." + mname, pv1);
        }
    }

    private void addArray(String name, BasicParameterValue pv) {
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
            pv1.setEngineeringValue(mEngvalue);
            if (rawValue != null) {
                Value mRawValue = rawValue.getElementValue(idx);
                pv1.setRawValue(mRawValue);
            }
            add(name + mname, pv1);

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

    // sort the parameters by id
    public void sort() {
        idArray.sort(pvList);
    }
}
