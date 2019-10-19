package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.ccsds.AosManagedParameters.ServiceType;

/**
 * AOS Transfer Frame as per
 * CCSDS RECOMMENDED STANDARD FOR AOS SPACE DATA LINK PROTOCOL
 * CCSDS 732.0-B-3 September 2015
 * 
 * Primary Header is composed of
 * <ul>
 * <li>Transfer frame version number (2 bits) - shall be set to 01</li>
 * <li>Spacecraft id (8 bits)</li>
 * <li>Virtual Channel id (6 bits)</li>
 * <li>Virtual Channel frame count (24 bits)</li>
 * <li>replay flag (1 bit)</li>
 * <li>VC frame count usage flag (1 bit)</li>
 * <li>spare (2 bits)</li>
 * <li>VC frame count cycle</li>
 * <li>frame header error control (optional)</li>
 * </ul>
 * 
 * @author nm
 *
 */
public class AosTransferFrame extends DownlinkTransferFrame {
    static final int MAX_FRAME_SEQ = 0xFFFFFF;

    ServiceType serviceType;

    public AosTransferFrame(byte[] data, int spacecraftId, int virtualChannelId) {
        super(data, spacecraftId, virtualChannelId);
    }

    int signalingField;

    boolean getReplayFlag() {
        return (signalingField & 0x80) == 0x80;
    }


    @Override
    public boolean containsOnlyIdleData() {
        return serviceType == ServiceType.IDLE;
    }

    @Override
    long getSeqCountWrapArround() {
        return MAX_FRAME_SEQ;
    }

    @Override
    int getSeqInterruptionDelta() {
        return 0x1FFFFF;
    }


    public void setServiceType(ServiceType serviceType) {
        this.serviceType=serviceType;
    }

}
