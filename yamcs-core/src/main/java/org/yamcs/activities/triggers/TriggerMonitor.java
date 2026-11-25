package org.yamcs.activities.triggers;

/**
 * Watches the condition of one trigger on one processor, and reports to the {@link TriggerService} when it is met.
 */
interface TriggerMonitor {

    /**
     * Start watching. Called on the trigger service thread.
     */
    void start() throws Exception;

    /**
     * Stop watching. Called on the trigger service thread.
     */
    void stop();
}
