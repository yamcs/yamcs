package org.yamcs.tctm.ccsds;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;

/**
 * Handles incoming TM frames by distributing them to different VirtualChannelHandlers
 * 
 * @author nm
 *
 */
public class MasterChannelFrameHandler {
    CcsdsFrameType frameType;
    TransferFrameDecoder frameDecoder;
    Map<Integer, VcDownlinkHandler> handlers = new HashMap<>();
    int idleFrameCount;
    int frameCount;
    DownlinkManagedParameters params;
    ClcwStreamHelper clcwHelper;
    String yamcsInstance;
    Log log;
    /**
     * Constructs based on the configuration
     * 
     * @param config
     */
    public MasterChannelFrameHandler(String yamcsInstance, String linkName, YConfiguration config) {
        log = new Log(getClass(), yamcsInstance);
        log.setContext(linkName);
        
        frameType = config.getEnum("frameType", CcsdsFrameType.class);
        String clcwStreamName = config.getString("clcwStream", null);
        if (clcwStreamName != null) {
            clcwHelper = new ClcwStreamHelper(yamcsInstance, clcwStreamName);
        }
        switch (frameType) {
        case AOS:
            AosManagedParameters amp = new AosManagedParameters(config);
            frameDecoder = new AosFrameDecoder(amp);
            params = amp;
            break;
        case TM:
            TmManagedParameters tmp = new TmManagedParameters(config);
            frameDecoder = new TmFrameDecoder(tmp);
            params = tmp;
            break;
        case USLP:
            UslpManagedParameters ump = new UslpManagedParameters(config);
            frameDecoder = new UslpFrameDecoder(ump);
            params = ump;
            break;
        default:
            throw new ConfigurationException("Unsupported frame type '" + frameType + "'");
        }
        handlers = params.createVcHandlers(yamcsInstance, linkName);
    }

    public void handleFrame(long ertime, byte[] data, int offset, int length) throws TcTmException {
        DownlinkTransferFrame frame = frameDecoder.decode(data, offset, length);
        if (frame.getSpacecraftId() != params.spacecraftId) {
            log.warn("Ignoring frame with unexpected spacraftId {} (expected {})", frame.getSpacecraftId(), params.spacecraftId);
            return;
        }
        frame.setEearthRceptionTime(ertime);
        frameCount++;
        
        if (frame.hasOcf() && clcwHelper != null) {
            clcwHelper.sendClcw(frame.getOcf());
        }

        if (frame.containsOnlyIdleData()) {
            idleFrameCount++;
            return;
        }
        
        int vcid = frame.getVirtualChannelId();
        VcDownlinkHandler vch = handlers.get(vcid);
        if (vch == null) {
            throw new TcTmException("No handler for vcId: " + vcid);
        }
        vch.handle(frame);
    }

    public int getMaxFrameSize() {
        return params.getMaxFrameLength();
    }

    public int getMinFrameSize() {
        return params.getMinFrameLength();
    }

    public Collection<VcDownlinkHandler> getVcHandlers() {
        return handlers.values();
    }
    
    public int getSpacecraftId() {
        return params.spacecraftId;
    }
    public CcsdsFrameType getFrameType() {
        return frameType;
    }
    
}
