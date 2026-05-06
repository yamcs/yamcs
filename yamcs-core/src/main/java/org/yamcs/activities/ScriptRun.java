package org.yamcs.activities;

/**
 * Covers the actual execution of a script run
 */
public interface ScriptRun {

    void run(ScriptExecution scriptExecution) throws Exception;

    /**
     * Called when an activity stop is requested.
     * <p>
     * Implementations are expected to finish in a timely manner.
     */
    void stop(ScriptExecution scriptExecution) throws Exception;
}
