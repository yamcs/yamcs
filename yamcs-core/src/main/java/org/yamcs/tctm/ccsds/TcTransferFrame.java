package org.yamcs.tctm.ccsds;


/**
 * 
 * TC Transfer Frame as per
 * <p>
 * CCSDS RECOMMENDED STANDARD FOR TC SPACE DATA LINK PROTOCOL CCSDS 232.0-B-4 October 2021
 *
 * <p>
 * Frame structure:
 * <ul>
 * <li>Transfer Frame Primary Header (5 bytes mandatory)</li>
 * <li>Transfer Frame Data Field (up to 1019 or 1017 bytes, mandatory)</li>
 * <li>Frame Error Control Field (2 bytes, optional).</li>
 * </ul>
 * The Transfer Frame Primary Header structure:
 * <ul>
 * <li>Transfer Frame Version Number (2 bits, mandatory)</li>
 * <li>Bypass Flag (1 bit, mandatory)</li>
 * <li>Control Command Flag (1 bit, mandatory)</li>
 * <li>Reserved Spare (2 bits, mandatory)</li>
 * <li>Spacecraft Identifier (10 bits, mandatory)</li>
 * <li>Virtual Channel Identifier (6 bits, mandatory)</li>
 * <li>Frame Length (10 bits, mandatory)</li>
 * <li>Frame Sequence Number (8 bits, mandatory)</li>
 * </ul>
 * Segment Header (optional):
 * <ul>
 * <li>Sequence flags (2 bits, mandatory)</li>
 * <li>Multiplexer Access Point (MAP) Identifier (6 bits, mandatory)</li>
 * </ul>
 */
public class TcTransferFrame extends UplinkTransferFrame {
    // can be null if the Segment Header is not present
    SegmentHeader segmentHeader;

    public TcTransferFrame(byte[] data, int spacecraftId, int virtualChannelId, boolean cmdControl) {
        super(data, spacecraftId, virtualChannelId, cmdControl);
    }


    public SegmentHeader getSegmentHeader() {
        return segmentHeader;
    }

    public void setSegmentHeader(SegmentHeader segmentHeader) {
        this.segmentHeader = segmentHeader;
    }

    @Override
    public String toString() {
        return "TcTransferFrame [masterChannelId=" + spacecraftId + ", virtualChannelId=" + virtualChannelId
                + ", vcFrameSeq=" + vcFrameSeq + ", bypass=" + bypass + ", cmdControl=" + cmdControl
                + ", numCommands: " + ((commands != null) ? commands.size() : 0) + ", segmentHeader=" + segmentHeader
                + "]";
    }

    public static record SegmentHeader(byte seqFlags, byte mapId) {
        public byte get() {
            return (byte) ((seqFlags << 6) | (mapId & 0x3F));
        }
    }
}
