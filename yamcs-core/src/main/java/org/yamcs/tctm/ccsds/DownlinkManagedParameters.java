package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.Link;
import org.yamcs.utils.YObjectLoader;

/**
 * Stores configuration related to Master channels for downlink.
 * 
 * @author nm
 *
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
    };

    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;

    public DownlinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class);
    }

    abstract int getMaxFrameLength();

    abstract int getMinFrameLength();

    abstract public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName);

    protected VcDownlinkHandler createVcaHandler(String yamcsInstance, String linkName,
            VcDownlinkManagedParameters vmp) {
        VcDownlinkHandler handler = YObjectLoader.loadObject(vmp.vcaHandlerClassName);
        if (handler instanceof Link) {
            ((Link) handler).init(yamcsInstance, linkName + ".vc" + vmp.vcId, vmp.config);
        }
        return handler;
    }
}
