package org.yamcs.parameterarchive;

public class ParametersValueRequest {
    long start, stop;
    int[] parameterIds;
    boolean ascending;
    ValueConsumer consumer;
    
    public ParametersValueRequest(long start, long stop, int[] parameterIds, boolean ascending, ValueConsumer consumer) {
        super();
        this.start = start;
        this.stop = stop;
        this.parameterIds = parameterIds;
        this.ascending = ascending;
        this.consumer = consumer;
    }
}
