package org.yamcs.parameterarchive;

public interface BackFillerListener {

    /**
     * Called when a backfilling task finished
     */
    void onBackfillFinished(long start, long stop, long processedParameters);
}
