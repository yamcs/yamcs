package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.SortedIntArray;

/**
 * Parameter Group segment - keeps references to Time and Value segments for a given parameter group and segment. 
 *  
 *  This class is used during the parameter archive buildup
 *   - it uses GenericValueSegment to store any kind of Value
 *   - once the segment is full, the consolidate method will turn each GenericValueSegment into an storage optimised one.
 *   
 * 
 * @author nm
 *
 */
public class PGSegment {
    final int parameterGroupId;
    final SortedIntArray parameterIds;
    private SortedTimeSegment timeSegment;
    private List<ValueSegment> engValueSegments;
    private List<ValueSegment> rawValueSegments;
    private List<ParameterStatusSegment> parameterStatusSegments;
    
    private List<BaseSegment> consolidatedValueSegments;
    private List<BaseSegment> consolidatedRawValueSegments;
    private List<ParameterStatusSegment> consolidatedParameterStatusSegments;
    
    private final boolean storeRawValues = ParameterArchive.STORE_RAW_VALUES;
    private long segmentStart;
    
    public PGSegment(int parameterGroupId, long segmentStart, SortedIntArray parameterIds) {
        this.parameterGroupId = parameterGroupId;
        this.parameterIds = parameterIds;
        this.segmentStart = segmentStart;
    }
    
    
    private void init(List<ParameterValue> sortedPvList) {
        timeSegment = new SortedTimeSegment(segmentStart);
        
        engValueSegments = new ArrayList<>(parameterIds.size());
        parameterStatusSegments = new ArrayList<>(parameterIds.size());
        if(storeRawValues) {
            rawValueSegments = new ArrayList<>(parameterIds.size());
        }

        for(int i=0; i<parameterIds.size(); i++) {
            ParameterValue pv = sortedPvList.get(i);
            Value v = pv.getEngValue();
            if(v!=null) {
                engValueSegments.add(getNewSegment(v.getType()));
            }
            parameterStatusSegments.add(new ParameterStatusSegment(true));
            Value rawV = pv.getRawValue();
            if(storeRawValues) {
                if(rawV==null) {
                    rawValueSegments.add(null);
                } else {
                    rawValueSegments.add(getNewSegment(rawV.getType()));
                }
            }
        }
    }
   
    
  

    static private ValueSegment getNewSegment(Type type) {
        switch(type) {
        case BINARY:
            return new BinaryValueSegment(true);
        case STRING:
            return new StringValueSegment(true);
        case SINT32:
            return new IntValueSegment(true);
        case UINT32:
            return new IntValueSegment(false);
        case FLOAT:
            return new FloatValueSegment();
        case SINT64:
            return new LongValueSegment(true);
        case UINT64:
            return new LongValueSegment(false);
        case DOUBLE:
            return new DoubleValueSegment();
       default:
         return new GenericValueSegment();
        }
    }

    /**
     * Add a new record 
     *  instant goes into the timeSegment
     *  the values goes each into a value segment
     *  
     *  the sortedPvList list has to be already sorted according to the definition of the ParameterGroup 
     * 
     * 
     * @param instant
     * @param sortedPvList
     */
    public void addRecord(long instant, List<ParameterValue> sortedPvList) {       
        if(sortedPvList.size() != parameterIds.size()) {
            throw new IllegalArgumentException("Wrong number of values passed: "+sortedPvList.size()+";expected "+engValueSegments.size());
        }
        
        if(engValueSegments==null) {
            init(sortedPvList);
        }
        
        
        int pos = timeSegment.add(instant);
        for(int i = 0; i<engValueSegments.size(); i++) {
            ParameterValue pv = sortedPvList.get(i);
            engValueSegments.get(i).add(pos, pv.getEngValue());
            Value rawValue = pv.getRawValue();
            if(storeRawValues && (rawValue!=null)) {
                rawValueSegments.get(i).add(pos, rawValue);
            }
            parameterStatusSegments.get(i).addParameterValue(pos, pv);
        }
    }

    
    public void consolidate() {
        consolidatedValueSegments  = new ArrayList<BaseSegment>(engValueSegments.size());
        for(ValueSegment gvs: engValueSegments) {
            BaseSegment bs = gvs.consolidate();
            consolidatedValueSegments.add(bs);
        }
        if(storeRawValues && rawValueSegments.size()>0) {
            consolidatedRawValueSegments  = new ArrayList<BaseSegment>(engValueSegments.size());
            
            //the raw values will only be stored if they are different than the engineering values
            for(int i=0; i<engValueSegments.size(); i++) {
                ValueSegment rvs = rawValueSegments.get(i);
                ValueSegment vs = engValueSegments.get(i);
                if((rvs==null) || rvs.equals(vs)) {
                    consolidatedRawValueSegments.add(null);
                } else {
                    consolidatedRawValueSegments.add(rvs.consolidate());
                }
            }
        }
        
        consolidatedParameterStatusSegments =  new ArrayList<>(parameterStatusSegments.size());
        for(int i=0;i<engValueSegments.size(); i++) {
            consolidatedParameterStatusSegments.add(parameterStatusSegments.get(i).consolidate());
        }
    }

    public long getSegmentStart() {
        return timeSegment.getSegmentStart();
    }

    public SortedTimeSegment getTimeSegment() {
       return timeSegment;
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }
    
    public int getParameterId(int index) {
        return parameterIds.get(index);
    }

    public List<BaseSegment> getConsolidatedValueSegments() {
        return consolidatedValueSegments;
    }

    public List<BaseSegment> getConsolidatedRawValueSegments() {
        return consolidatedRawValueSegments;
    }

    public List<ParameterStatusSegment> getConsolidatedParameterStatusSegments() {
        return consolidatedParameterStatusSegments;
    }
}
