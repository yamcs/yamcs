package org.yamcs.tctm.ccsds;

/**
 * USLP Transfer Frame for uplink as per CCSDS 732.1-B-3.
 * <p>
 * Frame structure:
 * <ul>
 * <li>Transfer Frame Primary Header (7 + vcfCountLength + insertZoneLength bytes)</li>
 * <li>Transfer Frame Data Zone Header (1 byte)</li>
 * <li>Transfer Frame Data Zone</li>
 * <li>Frame Error Control Field (2 or 4 bytes, optional)</li>
 * </ul>
 * The Transfer Frame Primary Header structure:
 * <ul>
 * <li>Transfer Frame Version Number (4 bits, value = 12)</li>
 * <li>Spacecraft ID (16 bits)</li>
 * <li>Source-or-Destination Identifier (1 bit, 0 = source/uplink)</li>
 * <li>Virtual Channel ID (6 bits)</li>
 * <li>MAP ID (4 bits)</li>
 * <li>End of Frame Primary Header Flag (1 bit)</li>
 * <li>Frame Length (16 bits)</li>
 * <li>Bypass/Sequence Control Flag (1 bit)</li>
 * <li>Protocol Control Command Flag (1 bit)</li>
 * <li>Spare (2 bits)</li>
 * <li>OCF Flag (1 bit)</li>
 * <li>VC Frame Count Length (3 bits)</li>
 * <li>VC Frame Count (0–56 bits, vcfCountLength bytes)</li>
 * </ul>
 */
public class UslpUplinkTransferFrame extends UplinkTransferFrame {
    final int mapId;

    public UslpUplinkTransferFrame(byte[] data, int spacecraftId, int virtualChannelId, int mapId,
            boolean cmdControl) {
        super(data, spacecraftId, virtualChannelId, cmdControl);
        this.mapId = mapId;
    }

    public int getMapId() {
        return mapId;
    }

    @Override
    public String toString() {
        return "UslpUplinkTransferFrame [spacecraftId=" + spacecraftId + ", virtualChannelId=" + virtualChannelId
                + ", mapId=" + mapId + ", vcFrameSeq=" + vcFrameSeq + ", bypass=" + bypass
                + ", cmdControl=" + cmdControl
                + ", numCommands: " + ((commands != null) ? commands.size() : 0) + "]";
    }
}
