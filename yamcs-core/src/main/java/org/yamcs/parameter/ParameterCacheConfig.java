package org.yamcs.parameter;

public class ParameterCacheConfig {
    final boolean enabled;
    final boolean cacheAll;
    //maximum duration of the cache
    final long maxDuration;
    final int maxNumEntries;
    
    public ParameterCacheConfig(boolean enabled, boolean cacheAll, long duration, int maxNumEntries) {
        this.enabled = enabled;
        this.cacheAll = cacheAll;
        this.maxDuration = duration;
        this.maxNumEntries = maxNumEntries;
    }
   
}
