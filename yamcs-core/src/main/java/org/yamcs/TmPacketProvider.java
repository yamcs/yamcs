package org.yamcs;

/**
 * @author nm
 *
 * Provides TM packets to a Yamcs Processor.
 * 
 */
public interface TmPacketProvider extends ProcessorService {
    /**
     * true if this is a replay from archive
     * @return
     */
    public boolean isArchiveReplay();

}
