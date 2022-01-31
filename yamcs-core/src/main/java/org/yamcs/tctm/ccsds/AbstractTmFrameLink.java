package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.TcTmException;
import org.yamcs.time.Instant;

public abstract class AbstractTmFrameLink extends AbstractLink implements AggregatedDataLink {
    protected List<Link> subLinks;
    protected  MasterChannelFrameHandler frameHandler;
    protected AtomicLong frameCount = new AtomicLong(0);
    boolean derandomize;

    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        this.derandomize = config.getBoolean("derandomize", false);
        frameHandler = new MasterChannelFrameHandler(yamcsInstance, name, config);
        subLinks = new ArrayList<>();
        for (VcDownlinkHandler vch : frameHandler.getVcHandlers()) {
            if (vch instanceof Link) {
                Link l = (Link) vch;
                subLinks.add(l);
                l.setParent(this);
            }
        }
    }

    /**
     * sends a frame to the multiplexer, after derandomizing it (if necessary)
     * 
     * @param ert
     *            - earth reception time
     * @param data
     *            - buffer containing frame data
     * @param offset
     *            - offset in the buffer where the frame data starts
     * @param length
     *            - length of the frame data
     */
    protected void handleFrame(Instant ert, byte[] data, int offset, int length) {
        try {
            if (derandomize) {
                Randomizer.randomizeTm(data, offset, length);
            }
            frameCount.getAndIncrement();
            frameHandler.handleFrame(ert, data, offset, length);
        } catch (TcTmException e) {
            eventProducer.sendWarning("Error processing frame: " + e.toString());
        }
    }

    @Override
    public long getDataInCount() {
        return frameCount.get();
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        frameCount.set(0);
    }

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

}
