package org.yamcs.time;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.TimeEncoding;

/**
 * Simple model from TimeService covering two cases:
 * where time is is equal to wall clock time
 * 
 * 
 * @author nm
 *
 */
public class RealtimeTimeService implements TimeService {
    String instance;
    static Map<String, TimeService> instances = new HashMap<>();
    long javaTime;
    long missionTime;
    double speed = 1;
    
    
    @Override
    public long getMissionTime() {
        return TimeEncoding.getWallclockTime();
    }
    
    
}
