package org.yamcs.parameterarchive;

import java.util.ArrayList;


import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.SortedIntArray;
/**
 * builds incrementally a list of parameter id and parameter value
 * , sorted by parameter ids 
 * */
class SortedParameterList {
    final ParameterIdDb parameterIdMap;
    final SortedIntArray parameterIdArray = new SortedIntArray();
    final List<ParameterValue> sortedPvList = new ArrayList<>();
    
    public SortedParameterList(ParameterIdDb parameterIdMap) {
        this.parameterIdMap = parameterIdMap;
    }
    
    void add(ParameterValue pv) {
        String fqn = pv.getParameterQualifiedNamed();
        Value engValue = pv.getEngValue();
        Value rawValue = pv.getRawValue();
        Type engType = engValue.getType();
        Type rawType = (rawValue == null) ? null : rawValue.getType();
        int parameterId = parameterIdMap.createAndGet(fqn, engType, rawType);

        int pos = parameterIdArray.insert(parameterId);
        sortedPvList.add(pos, pv);
    }

    public int size() {
        return parameterIdArray.size();
    }
}
