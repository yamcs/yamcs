package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;
import org.yamcs.tctm.ccsds.UplinkManagedParameters.PriorityScheme;
import org.yamcs.utils.TimeEncoding;

/**
 * Multiplexes TC frames from Virtual Channels based on priority schemes.
 * <p>
 * Three priorities schemes are implemented, inspired from CCSDS 912.3-B-3
 * <ul>
 * <li>FIFO - the frame with the earliest timestamp is selected.</li>
 * <li>absolute priority - the frames are selected from the virtual channel with highest priority.</li>
 * <li>polling vector - the virtual channels are checked in accordance with the entries in the polling vector.</li>
 * </ul>
 */
public class MasterChannelFrameMultiplexer {
    Semaphore dataAvailableSemaphore = new Semaphore(0);
    volatile boolean quitting = false;
    UplinkManagedParameters<?> managedParameters;
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    List<VcUplinkHandler<?>> handlers;
    int[] pollingVector;
    int pvIdx;
    int pvCnt;
    Log log;

    public MasterChannelFrameMultiplexer(String yamcsInstance, String linkName, YConfiguration config) {
        CcsdsFrameType frameType = config.getEnum("frameType", CcsdsFrameType.class, null);
        if (frameType == CcsdsFrameType.USLP) {
            managedParameters = new UslpUplinkManagedParameters(config, yamcsInstance, linkName);
        } else {
            managedParameters = new TcManagedParameters(config, yamcsInstance, linkName);
        }
        handlers = createHandlers(managedParameters, yamcsInstance, linkName, executor);
        log = new Log(getClass(), yamcsInstance);
        log.setContext(linkName);

        for (var h : handlers) {
            h.setDataAvailableSemaphore(dataAvailableSemaphore);
        }
        if (managedParameters.priorityScheme == PriorityScheme.ABSOLUTE) {
            Collections.sort(handlers, (h1, h2) -> {
                return Integer.compare(h2.getParameters().getPriority(), h1.getParameters().getPriority());
            });
        } else if (managedParameters.priorityScheme == PriorityScheme.POLLING_VECTOR) {
            pollingVector = new int[handlers.size()];
            for (int i = 0; i < pollingVector.length; i++) {
                var hp = handlers.get(i).getParameters();
                if (hp.getPriority() < 1) {
                    throw new ConfigurationException("Invalid priority " + hp.getPriority() + " for vc "
                            + hp.getVirtualChannelId() + " and multiplexing scheme POLLING_VECTOR");
                }
                pollingVector[i] = hp.getPriority();
            }
        }
    }

    /**
     * Get the next frame blocking until one is available or until {@link #quit()} is called.
     * 
     * @return next frame or null if the multiplexer has been closed or the thread interrupted
     */
    public UplinkTransferFrame getFrame() {
        while (!quitting) {
            UplinkTransferFrame tf = null;
            if (managedParameters.priorityScheme == PriorityScheme.ABSOLUTE) {
                tf = getFrameAbsolutePriority();
            } else if (managedParameters.priorityScheme == PriorityScheme.FIFO) {
                tf = getFrameFifo();
            } else {
                tf = getFramePollingVector();
            }
            if (tf != null) {
                return tf;
            }
            try {
                dataAvailableSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private UplinkTransferFrame getFramePollingVector() {
        int pvIdx0 = pvIdx;
        do {
            VcUplinkHandler<?> h = handlers.get(pvIdx);
            pvCnt++;
            if (pvCnt == pollingVector[pvIdx]) {
                pvCnt = 0;
                pvIdx++;
                if (pvIdx == pollingVector.length) {
                    pvIdx = 0;
                }
            }
            var tf = h.getFrame();
            if (tf != null) {
                log.debug("PollingVectorPriority multiplexing: got frame {} from {}", tf, h);
                return tf;
            }
        } while (pvIdx != pvIdx0);
        return null;
    }

    private UplinkTransferFrame getFrameFifo() {
        VcUplinkHandler<?> hfirst = null;
        long tfirst = Long.MAX_VALUE;

        for (var h : handlers) {
            long t = h.getFirstFrameTimestamp();
            if (t != TimeEncoding.INVALID_INSTANT && t < tfirst) {
                tfirst = t;
                hfirst = h;
            }
        }
        if (hfirst != null) {
            var tf = hfirst.getFrame();
            if (tf != null) {
                log.debug("FifoPriority multiplexing: got frame {} from {}", tf, hfirst);
                return tf;
            }
        }
        return null;
    }

    private UplinkTransferFrame getFrameAbsolutePriority() {
        for (var h : handlers) {
            var tf = h.getFrame();
            if (tf != null) {
                log.debug("AbsolutePriority multiplexing: got frame {} from {}", tf, h);
                return tf;
            }
        }
        return null;
    }

    /**
     * Stop producing frames and unblock the getFrame() operation
     */
    public void quit() {
        quitting = true;
        dataAvailableSemaphore.release();
    }

    private <T extends UplinkTransferFrame> List<VcUplinkHandler<?>> createHandlers(
            UplinkManagedParameters<T> params, String yamcsInstance, String linkName,
            ScheduledThreadPoolExecutor executor) {
        return new ArrayList<>(params.createVcHandlers(yamcsInstance, linkName, executor));
    }

    public Collection<VcUplinkHandler<?>> getVcHandlers() {
        return handlers;
    }

}