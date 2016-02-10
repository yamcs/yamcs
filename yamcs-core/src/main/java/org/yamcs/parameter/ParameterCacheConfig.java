package org.yamcs.parameter;

public class ParameterCacheConfig {
    final boolean enabled;
    final boolean cacheAll;
    final long duration;
    public ParameterCacheConfig(boolean enabled, boolean cacheAll, long duration) {
        this.enabled = enabled;
        this.cacheAll = cacheAll;
        this.duration = duration;
    }
   
}
