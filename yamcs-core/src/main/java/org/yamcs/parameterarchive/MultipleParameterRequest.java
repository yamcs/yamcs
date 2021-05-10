package org.yamcs.parameterarchive;

import java.util.Arrays;

import org.yamcs.utils.TimeEncoding;

public class MultipleParameterRequest {

    final ParameterId[] parameterIds;

    final long start;
    final long stop;
    final boolean ascending;
    final boolean retrieveEngValues;
    final boolean retrieveParamStatus;
    final boolean retrieveRawValues;
    final int[] parameterGroupIds;

    int limit = -1;

    public MultipleParameterRequest(long start, long stop, ParameterId[] parameterIds, boolean ascending) {
        this(start, stop, parameterIds, null, ascending, true, true, true);
    }

    public MultipleParameterRequest(long start, long stop, ParameterId[] parameterIds, int[] parameterGroupIds,
            boolean ascending, boolean retrieveEngValues, boolean retrieveRawValues, boolean retrieveParamStatus) {

        if (parameterGroupIds != null && parameterGroupIds.length != parameterIds.length) {
            throw new IllegalArgumentException("Different number of parameter ids than parameter group ids");
        }
        this.parameterIds = parameterIds;
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.retrieveRawValues = retrieveRawValues;
        this.retrieveEngValues = retrieveEngValues;
        this.retrieveParamStatus = retrieveParamStatus;
        this.parameterGroupIds = parameterGroupIds;
    }

    public boolean isRetrieveEngValues() {
        return retrieveEngValues;
    }

    public boolean isRetrieveParamStatus() {
        return retrieveParamStatus;
    }

    public ParameterId[] getParameterIds() {
        return parameterIds;
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
     * 
     * @param limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    public String toString() {
        return "MultipleParameterRequest [parameterIds=" + Arrays.toString(parameterIds)
                + ", start=" + TimeEncoding.toString(start) + ", stop=" + TimeEncoding.toString(stop) + ", ascending="
                + ascending + ", retrieveRawValues=" + retrieveRawValues + ", retrieveEngValues=" + retrieveEngValues
                + ", retrieveParamStatus=" + retrieveParamStatus + ", limit=" + limit + "]";
    }

}
