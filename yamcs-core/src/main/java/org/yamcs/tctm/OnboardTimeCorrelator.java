package org.yamcs.tctm;

/**
 * Correlates on-board time with ground(yamcs) time by knowing the relationship between them at a certain time and
 * applying an optional drift.
 * 
 * 
 * @author nm
 *
 */
public class OnboardTimeCorrelator {
    //yamcs time at the synchronisation point
    private long yamscTSync;
    
    //on-board time difference at the synchronisation point
    private long obTSyncDiffMillis;
    private int obTsyncDiffPicos;
    
    //drift of the on-board time
    private double obDrift; 
    
    
}
