package org.yamcs.examples.ccsdsframes;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.DownlinkTransferFrame;
import org.yamcs.tctm.ccsds.PacketDecoder;
import org.yamcs.tctm.ccsds.VcDownlinkHandler;
import org.yamcs.time.Instant;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.XtceDb;

/**
 * Example of a VCA (Virtual Channel Access) handler.
 * <p>
 * Extracts CCSDS packets from a frame which does not include the first header pointer.
 * <p>
 * Each frame it starts a new packet decoder.
 *
 */
public class SampleVcaHandler extends AbstractTmDataLink implements VcDownlinkHandler {
    private Instant ertime;
    private long frameSeqCount;
    private int virtualChannelId;

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
    }

    @Override
    public void handle(DownlinkTransferFrame frame) {
        if (isDisabled()) {
            log.trace("Dropping frame for VC {} because the link is disabled", frame.getVirtualChannelId());
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Processing frame VC {}, SEQ {}, FHP {}, DS {}, DE {}", frame.getVirtualChannelId(),
                    frame.getVcFrameSeq(),
                    frame.getFirstHeaderPointer(), frame.getDataStart(), frame.getDataEnd());
        }
        ertime = frame.getEarthRceptionTime();
        frameSeqCount = frame.getVcFrameSeq();
        virtualChannelId = frame.getVirtualChannelId();

        PacketDecoder packetDecoder = new PacketDecoder(frame.getDataEnd() - frame.getDataStart(),
                p -> handlePacket(p));
        try {
            packetDecoder.process(frame.getData(), frame.getDataStart(), frame.getDataEnd() - frame.getDataStart());
        } catch (TcTmException e) {
            log.warn("Exception processing frame data: ", e);
        }
    }

    private void handlePacket(byte[] p) {
        log.info("Received packet of length {}: {}", p.length, StringConverter.arrayToHexString(p, true));
        TmPacket pwt = new TmPacket(timeService.getMissionTime(), p);
        pwt.setEarthReceptionTime(ertime);
        pwt.setFrameSeqCount(frameSeqCount);

        // Add vcId as metadata
        ParameterValue vcIdPv = new ParameterValue(XtceDb.YAMCS_TM_PACKET_METADATA_SPACESYSTEM_NAME + "/vcId");
        vcIdPv.setEngValue(ValueUtility.getUint32Value(virtualChannelId));
        pwt.addMetadataParameter(vcIdPv);

        pwt = packetPreprocessor.process(pwt);
        processPacket(pwt);
        updateStats(p.length);
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
