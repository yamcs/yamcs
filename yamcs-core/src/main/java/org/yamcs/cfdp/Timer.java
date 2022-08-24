package org.yamcs.cfdp;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implements a timer used by CFDP for verifying the sending of EOF, FIN and NAK PDUs
 * 
 * <p>
 * This class works with one thrad executor and expects all methods to be called on the executor thread.
 * @author nm
 *
 */
public class Timer {
    final int maxNumAttempts;
    final long timeout;
    final ScheduledThreadPoolExecutor executor;

    int numAttempts;
    ScheduledFuture<?> scheduledFuture;

    public Timer(ScheduledThreadPoolExecutor executor, int maxNumAttempts, long timeout) {
        this.maxNumAttempts = maxNumAttempts;
        this.timeout = timeout;
        this.executor = executor;
    }

    public void start(Runnable onIntermediate, Runnable onFinal) {
        numAttempts = 0;

        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        
        scheduledFuture = executor.scheduleAtFixedRate(() -> {
            if (maxNumAttempts < 0 || numAttempts < maxNumAttempts) {
                onIntermediate.run();
            } else {
                scheduledFuture.cancel(true);
                onFinal.run();
            }
            numAttempts++;

        }, timeout, timeout, TimeUnit.MILLISECONDS);
    }

    public void cancel() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    boolean isActive() {
        return scheduledFuture != null;
    }
}
