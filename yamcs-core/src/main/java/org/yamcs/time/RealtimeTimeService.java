package org.yamcs.time;

import org.yamcs.utils.TimeEncoding;

/**
 * Simple model from TimeService implementing the mission time as the wallclock time
 * 
 *
 */
public class RealtimeTimeService implements TimeService {
    @Override
    public long getMissionTime() {
        return TimeEncoding.getWallclockTime();
    }
}
