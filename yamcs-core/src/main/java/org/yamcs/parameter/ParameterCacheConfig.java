package org.yamcs.parameter;

import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;

public class ParameterCacheConfig {
    final boolean cacheAll;
    //maximum duration of the cache
    final long maxDuration;
    final int maxNumEntries;
    
    public ParameterCacheConfig(boolean enabled, boolean cacheAll, long duration, int maxNumEntries) {
        this.cacheAll = cacheAll;
        this.maxDuration = duration;
        this.maxNumEntries = maxNumEntries;
    }
    
    public ParameterCacheConfig() {
        this.cacheAll = false;
        this.maxDuration = 0;
        this.maxNumEntries = 0;
    }

    public ParameterCacheConfig(YConfiguration cacheConfig, Log log) {
        cacheAll = cacheConfig.getBoolean("cacheAll", true);
        maxDuration = 1000L * cacheConfig.getInt("duration", 600);
        maxNumEntries = cacheConfig.getInt("maxNumEntries", 4096);
    }

    @Override
    public String toString() {
        return "ParameterCacheConfig [cacheAll=" + cacheAll + ", maxDuration=" + maxDuration
                + ", maxNumEntries=" + maxNumEntries + "]";
    }
}
