package org.yamcs.parameterarchive;

import java.util.BitSet;

public class MultipleParameterValueRequest {
    final String[] parameterNames;
    final int[] parameterIds;
    final int[] parameterGroupIds;
    final long start;
    final long stop;
    final boolean ascending;
    final BitSet retrieveRawValues;
    
    //these shall also be considered final - just that I didn't want the constructor to get very long
    boolean retrieveEngValues = true;
    boolean retrieveParamStatus = true;
    
    
    int limit = -1;
    
    public MultipleParameterValueRequest(long start, long stop, String[] parameterNames, int[] parameterIds, int[] parameterGroupIds, 
            BitSet retrieveRawValues, boolean ascending) {
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
    }
    
    public boolean isRetrieveEngValues() {
        return retrieveEngValues;
    }

    public void setRetrieveEngValues(boolean retrieveEngValues) {
        this.retrieveEngValues = retrieveEngValues;
    }

    public boolean isRetrieveParamStatus() {
        return retrieveParamStatus;
    }

    public void setRetrieveParamStatus(boolean retrieveParamStatus) {
        this.retrieveParamStatus = retrieveParamStatus;
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
}
