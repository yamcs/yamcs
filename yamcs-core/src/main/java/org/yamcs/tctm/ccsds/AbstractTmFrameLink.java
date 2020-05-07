package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;

public abstract class AbstractTmFrameLink extends AbstractLink implements AggregatedDataLink {
    protected List<Link> subLinks;
    protected  MasterChannelFrameHandler frameHandler;
    protected volatile int frameCount;

    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
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

    @Override
    public long getDataInCount() {
        return frameCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        frameCount = 0;
    }

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

}
