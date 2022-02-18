package org.yamcs.time;

/**
 * The time service provides the so call mission time.
 * <p>
 * There is one such service for each Yamcs instance.
 *
 * <p>
 * Different implementations of these can alow to simulate time in the past or in the future or provide a time
 * synchronized with a simulator.
 * 
 */
public interface TimeService {
    /**
     * @return the mission time in Yamcs millisecond resolution
     */
    public long getMissionTime();
    
    /**
     * 
     * @return the mission time in high resolution. When there is no high resolution time available, this returns an
     *         Instant equivalent with {@link #getMissionTime()}
     */
    default public Instant getHresMissionTime() {
        return Instant.get(getMissionTime());
    }
}
