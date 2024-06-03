package org.yamcs.archive;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.AbstractYamcsService;
import org.yamcs.YamcsException;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;

/**
 * Yarch replay server
 *
 * A note about terminology: we call this replay because it provides capability to speed control/pause/resume. However,
 * it is not replay in terms of reprocessing the data - the data is sent as recorded in the streams.
 *
 */
public class ReplayServer extends AbstractYamcsService {

    final int MAX_REPLAYS = 200;

    AtomicInteger replayCount = new AtomicInteger();

    /**
     * create a new packet replay object
     * 
     * @return a replay object
     */
    public YarchReplay createReplay(ReplayOptions replayRequest, ReplayListener replayListener)
            throws YamcsException {
        if (replayCount.get() >= MAX_REPLAYS) {
            throw new YamcsException("maximum number of replays reached");
        }

        try {
            Mdb mdb = MdbFactory.getInstance(yamcsInstance);
            YarchReplay yr = new YarchReplay(this, replayRequest, replayListener, mdb);
            replayCount.incrementAndGet();
            return yr;
        } catch (YamcsException e) {
            log.warn("Got YamcsException when creating a replay object", e);
            throw e;
        } catch (Exception e) {
            log.warn("Got exception when creating a replay object", e);
            throw new YamcsException("Got exception when creating a replay. " + e.getMessage(), e);
        }
    }

    public void replayFinished() {
        replayCount.decrementAndGet();
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }
}
