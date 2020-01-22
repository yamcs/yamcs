package org.yamcs.parameter;

import java.util.Set;

import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;

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
    
    public ParameterCacheConfig() {
        this.enabled = false;
        this.cacheAll = false;
        this.maxDuration = 0;
        this.maxNumEntries = 0;
    }

    public ParameterCacheConfig(YConfiguration cacheConfig, Log log) {
        
        enabled = cacheConfig.getBoolean("enabled", false);
        if (!enabled) { // this is the default but print a warning if there are some things configured
            Set<String> keySet = cacheConfig.getRoot().keySet();
            keySet.remove("enabled");
            if (!keySet.isEmpty()) {
                log.warn(
                        "Parmeter cache is disabled, the following keys are ignored: {}, use enable: true to enable the parameter cache",
                        keySet);
            }
        }
        cacheAll = cacheConfig.getBoolean("cacheAll", false);
        maxDuration = 1000L * cacheConfig.getInt("duration", 300);
        maxNumEntries = cacheConfig.getInt("maxNumEntries", 512);
    }

    @Override
    public String toString() {
        return "ParameterCacheConfig [enabled=" + enabled + ", cacheAll=" + cacheAll + ", maxDuration=" + maxDuration
                + ", maxNumEntries=" + maxNumEntries + "]";
    }
}
