package org.yamcs.archive;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.YamcsService;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Yarch replay server
 *
 * A note about terminology: we call this replay because it provides capability to speed control/pause/resume. However,
 * it is not replay in terms of reprocessing the data - the data is sent as recorded in the streams.
 *
 * @author nm
 *
 */
public class ReplayServer extends AbstractService implements YamcsService {
    static Logger log = LoggerFactory.getLogger(ReplayServer.class);

    final int MAX_REPLAYS = 200;
    final String instance;

    AtomicInteger replayCount = new AtomicInteger();

    public ReplayServer(String instance) {
        this.instance = instance;
    }

    public ReplayServer(String instance, Map<String, Object> config) {
        this.instance = instance;
    }

    /**
     * create a new packet replay object
     * 
     * @return a replay object
     */
    public YarchReplay createReplay(ReplayRequest replayRequest, ReplayListener replayListener)
            throws YamcsException {
        if (replayCount.get() >= MAX_REPLAYS) {
            throw new YamcsException("maximum number of replays reached");
        }

        try {
            YarchReplay yr = new YarchReplay(this, replayRequest, replayListener, XtceDbFactory.getInstance(instance));
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

    public String getInstance() {
        return instance;
    }
}
