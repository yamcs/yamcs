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

    /**
     * If the time service is a simulated time, this gives the relation between the (simulated) mission time and the
     * wall clock time:
     * <ul>
     * <li>1.0 = realtime speed.</li>
     * <li>&gt;1.0 = faster than realtime</li>
     * <li>&lt;1.0 = slower than realtime.</li>
     * </ul>
     *
     * @return the relation between the mission time and the wall clock time
     *
     */
    default public double getSpeed() {
        return 1.0;
    }
}
