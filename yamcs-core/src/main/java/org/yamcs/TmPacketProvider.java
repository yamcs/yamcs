package org.yamcs;

import org.yamcs.TmProcessor;

import com.google.common.util.concurrent.Service;


/**
 * @author nm
 *
 * Provides TM packets to a Yamcs Processor.
 * 
 */
public interface TmPacketProvider extends Service {
    /**
     * true if this is a replay from archive
     * @return
     */
    public boolean isArchiveReplay();

    public void init(YProcessor yProcessor, TmProcessor tmProcessor);

}
