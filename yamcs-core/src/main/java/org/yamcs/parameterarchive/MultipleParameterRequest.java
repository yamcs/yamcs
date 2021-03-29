package org.yamcs.parameterarchive;

import java.util.Arrays;
import java.util.BitSet;

import org.yamcs.utils.TimeEncoding;

public class MultipleParameterRequest {

    final String[] parameterNames;
    final int[] parameterIds;
    final int[] parameterGroupIds;
    final long start;
    final long stop;
    final boolean ascending;
    final boolean retrieveEngValues;
    final boolean retrieveParamStatus;

    // this is a bitset matching one to one to the parameterIds and parameterGroupIds arrays
    // the reason is not a simple boolean as it is requested by the user is that not all parameters have a raw value
    // before calling this, the code will check if the parameter has a raw value and set the
    // corresponding bit to false if it does not, otherwise the eng value will be returned as raw value
    final BitSet retrieveRawValues;
    
    int limit = -1;
    
    public MultipleParameterRequest(long start, long stop, String[] parameterNames, int[] parameterIds, int[] parameterGroupIds, 
            boolean ascending, boolean retrieveEngValues, BitSet retrieveRawValues, boolean retrieveParamStatus) {
        if(parameterGroupIds.length != parameterIds.length) {
            throw new IllegalArgumentException("Different number of parameter ids than parameter group ids");
        }
        if(parameterNames.length != parameterIds.length) {
            throw new IllegalArgumentException("Different number of parameter names than parameter ids");
        }
        
        this.parameterNames = parameterNames;
        this.parameterIds = parameterIds;
        this.parameterGroupIds = parameterGroupIds;
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.retrieveRawValues = retrieveRawValues;
        this.retrieveEngValues = retrieveEngValues;
        this.retrieveParamStatus = retrieveParamStatus;
    }
    
    public boolean isRetrieveEngValues() {
        return retrieveEngValues;
    }

    public boolean isRetrieveParamStatus() {
        return retrieveParamStatus;
    }



    public String[] getParameterNames() {
        return parameterNames;
    }

    public int[] getParameterIds() {
        return parameterIds;
    }

    public int[] getParameterGroupIds() {
        return parameterGroupIds;
    }

    public long getStart() {
        return start;
    }

    public long getStop() {
        return stop;
    }

    public boolean isAscending() {
        return ascending;
    }

    public int getLimit() {
        return limit;
    }

    
    /**
     * retrieve a limited number of "lines"
     * negative means no limit
     * @param limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    public String toString() {
        return "MultipleParameterRequest [parameterNames=" + Arrays.toString(parameterNames) + ", parameterIds="
                + Arrays.toString(parameterIds) + ", parameterGroupIds=" + Arrays.toString(parameterGroupIds)
                + ", start=" + TimeEncoding.toString(start) + ", stop=" + TimeEncoding.toString(stop) + ", ascending="
                + ascending + ", retrieveRawValues=" + retrieveRawValues + ", retrieveEngValues=" + retrieveEngValues
                + ", retrieveParamStatus=" + retrieveParamStatus + ", limit=" + limit + "]";
    }

}
