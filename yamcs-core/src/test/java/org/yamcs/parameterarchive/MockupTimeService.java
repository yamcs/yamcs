package org.yamcs.parameterarchive;

import org.yamcs.time.TimeService;

class MockupTimeService implements TimeService {
    long missionTime=0;
    @Override
    public long getMissionTime() {
        return missionTime;
    }
}