package org.yamcs.time;

public interface TimeService {    
    public long getMissionTime();
    
    default public Instant getHresMissionTime() {
        return Instant.get(getMissionTime());
    }
}
