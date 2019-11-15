package org.yamcs.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Clock;
import com.codahale.metrics.EWMA;

/**
 * Like the {@link com.codahale.metrics.Meter} but gives the data rates at 5 seconds mean rates
 * 
 * @author nm
 *
 */
public class DataRateMeter {

    private final AtomicLong lastTick;
    private final Clock clock;
    private final long startTime;

    private final EWMA rate = new EWMA(1, 2, TimeUnit.SECONDS);
    private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(2);

    public DataRateMeter() {
        this.clock = Clock.defaultClock();
        this.startTime = this.clock.getTick();
        this.lastTick = new AtomicLong(startTime);
    }

    public void mark(long n) {
        tickIfNecessary();
        rate.update(n);
    }

    private void tickIfNecessary() {
        final long oldTick = lastTick.get();
        final long newTick = clock.getTick();
        final long age = newTick - oldTick;
        if (age > TICK_INTERVAL) {
            final long newIntervalStartTick = newTick - age % TICK_INTERVAL;
            if (lastTick.compareAndSet(oldTick, newIntervalStartTick)) {
                final long requiredTicks = age / TICK_INTERVAL;
                for (long i = 0; i < requiredTicks; i++) {
                    rate.tick();
                }
            }
        }
    }

    public double getFiveSecondsRate() {
        tickIfNecessary();
        return rate.getRate(TimeUnit.SECONDS);
    }
}
