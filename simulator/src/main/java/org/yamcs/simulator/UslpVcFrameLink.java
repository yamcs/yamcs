package org.yamcs.simulator;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.cfdp.CfdpCcsdsPacket;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

/**
 * Works as a child of {@link UdpUslpFrameLink} and handles commands for one Virtual Channel.
 * <p>
 * Parses USLP transfer frames as per CCSDS 732.1-B-3 and extracts command packets.
 * Assumes CRC16 error detection and no insert zone (both configurable in the future).
 * Does not implement COP-P (control frames are logged and discarded).
 */
public class UslpVcFrameLink {
    private static final Logger log = LoggerFactory.getLogger(UslpVcFrameLink.class);
    static final CrcCciitCalculator crc = new CrcCciitCalculator();

    final ColSimulator simulator;
    final int vcId;

    public UslpVcFrameLink(ColSimulator simulator, int vcId) {
        this.simulator = simulator;
        this.vcId = vcId;
    }

    void processFrame(byte[] data, int offset, int length) {
        // --- Primary header: first 4 bytes ---
        // version(4) | scId(16) | src/dest(1) | vcId(6) | mapId(4) | endOfHeader(1)
        int f4b = ByteArrayUtils.decodeInt(data, offset);
        int version = f4b >>> 28;
        if (version != 12) {
            log.warn("Invalid USLP frame version {}, expected 12", version);
            return;
        }
        boolean truncated = (f4b & 1) == 1;
        if (truncated) {
            log.warn("Truncated USLP frames not supported");
            return;
        }

        // Bytes 4-5: encoded frame length = total length - 1
        int encodedLength = ByteArrayUtils.decodeUnsignedShort(data, offset + 4);
        int frameLength = encodedLength + 1;
        if (frameLength > length) {
            log.warn("USLP frame length mismatch: header says {}, received {}", frameLength, length);
            return;
        }

        // CRC16: last 2 bytes
        int c1 = crc.compute(data, offset, frameLength - 2);
        int c2 = ByteArrayUtils.decodeUnsignedShort(data, offset + frameLength - 2);
        if (c1 != c2) {
            log.warn("CRC check failed, computed: {}, in frame: {}\nFrame: {}",
                    Integer.toHexString(c1).toUpperCase(),
                    Integer.toHexString(c2).toUpperCase(),
                    StringConverter.arrayToHexString(data, offset, frameLength, true));
            return;
        }
        int dataEnd = offset + frameLength - 2; // data area excludes CRC

        // Byte 6: bypass(1) | cmdCtrl(1) | spare(2) | ocf(1) | vcfCountLength(3)
        byte b6 = data[offset + 6];
        boolean bypassFlag  = ((b6 >> 7) & 1) == 1;
        boolean cmdCtrlFlag = ((b6 >> 6) & 1) == 1;
        boolean ocfPresent  = ((b6 >> 3) & 1) == 1;
        int vcfCountLength  = b6 & 0x7;

        int dataOffset = offset + 7;

        // Read (and discard) VC frame count
        for (int i = 0; i < vcfCountLength; i++) {
            dataOffset++;
        }

        // No insert zone support — insertZoneLength assumed 0

        if (ocfPresent) {
            dataEnd -= 4; // strip OCF field before the CRC
        }

        int spacecraftId = (f4b >>> 12) & 0xFFFF;
        int mapId        = (f4b >> 1) & 0xF;
        log.debug("USLP frame: scId={}, vcId={}, mapId={}, bypass={}, cmdCtrl={}, vcfCountLength={}",
                spacecraftId, vcId, mapId, bypassFlag, cmdCtrlFlag, vcfCountLength);

        if (cmdCtrlFlag) {
            log.debug("USLP protocol control command frame on VC {} (COP-P not supported, discarding)", vcId);
            return;
        }

        // Data zone header (1 byte: construction rule + protocol ID)
        dataOffset++; // skip zone header byte

        int cmdLength = dataEnd - dataOffset;
        if (cmdLength <= 0) {
            log.warn("No command data in USLP frame (cmdLength={})", cmdLength);
            return;
        }

        processCommand(data, dataOffset, cmdLength);
    }

    private void processCommand(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length).slice();
        SimulatorCcsdsPacket packet = (CcsdsPacket.getAPID(bb) == CfdpCcsdsPacket.APID)
                ? new CfdpCcsdsPacket(bb)
                : new ColumbusCcsdsPacket(bb);
        simulator.processTc(packet);
    }
}
