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
     * sets the tm processor that should get all the tm packets
     */
    public void setTmProcessor(TmProcessor tmProcessor);

    /**
     * true if this is a replay from archive
     * @return
     */
    public boolean isArchiveReplay();

}
