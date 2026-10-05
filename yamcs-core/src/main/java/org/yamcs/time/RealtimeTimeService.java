package org.yamcs.time;

import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;

/**
 * Simple model from TimeService implementing the mission time as the wallclock time
 * 
 *
 */
public class RealtimeTimeService implements TimeService {
    public RealtimeTimeService(String yamcsInstance, YConfiguration config) {

    }

    public RealtimeTimeService(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    public RealtimeTimeService() {
        this("", YConfiguration.emptyConfig());
    }

    @Override
    public long getMissionTime() {
        return TimeEncoding.getWallclockTime();
    }
}
