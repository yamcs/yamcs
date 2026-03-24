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
 * Implements the FARM part of COP-1 (CCSDS 232.1-B-2).
 * Assumes CRC16 error detection and no insert zone (both configurable in the future).
 */
public class UslpVcFrameLink {
    private static final Logger log = LoggerFactory.getLogger(UslpVcFrameLink.class);
    static final CrcCciitCalculator crc = new CrcCciitCalculator();

    final ColSimulator simulator;
    final int vcId;

    // FARM parameters
    boolean lockout;
    boolean retransmit;
    boolean waitFlag = false;
    int vR;
    int windowWidth = 15;
    int farmBCounter;

    public UslpVcFrameLink(ColSimulator simulator, int vcId) {
        this.simulator = simulator;
        this.vcId = vcId;
    }

    void processFrame(byte[] data, int offset, int length) {
        // Primary header: first 4 bytes
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

        // Read VC frame count (big-endian); only the last byte is used for the 8-bit sliding window
        int nS = 0;
        for (int i = 0; i < vcfCountLength; i++) {
            nS = data[dataOffset++] & 0xFF;
        }

        // No insert zone support — insertZoneLength assumed 0

        if (ocfPresent) {
            dataEnd -= 4; // strip OCF field before the CRC
        }

        int spacecraftId = (f4b >>> 12) & 0xFFFF;
        int mapId        = (f4b >> 1) & 0xF;
        log.debug("USLP frame: scId={}, vcId={}, mapId={}, bypass={}, cmdCtrl={}, vcfCountLength={}, nS={}",
                spacecraftId, vcId, mapId, bypassFlag, cmdCtrlFlag, vcfCountLength, nS);

        // BC frame: protocol control command (per CCSDS 732.1-B-3, both bypass and cmdCtrl flags are set)
        if (cmdCtrlFlag) {
            dataOffset++; // skip data zone header
            int cmdLength = dataEnd - dataOffset;
            processControlCommand(data, dataOffset, cmdLength);
            return;
        }

        // Skip data zone header
        dataOffset++;

        int cmdLength = dataEnd - dataOffset;
        if (cmdLength <= 0) {
            log.warn("No command data in USLP frame (cmdLength={})", cmdLength);
            return;
        }

        if (bypassFlag) {
            // BD frame: expedited transfer, bypass sequence check
            farmBCounter++;
            processCommand(data, dataOffset, cmdLength);
            return;
        }

        // AD frame: sequence-controlled transfer
        if (lockout) {
            log.warn("Command received in lockout state, ignoring");
            return;
        }
        if (vcfCountLength == 0) {
            // No sequence number present in frame, accept unconditionally
            processCommand(data, dataOffset, cmdLength);
            return;
        }
        if (nS == vR) {
            vR = incr(vR);
            retransmit = false;
            processCommand(data, dataOffset, cmdLength);
        } else if (insidePositiveWindow(nS)) {
            log.warn("Command inside positive sliding window, setting retransmit=1");
            retransmit = true;
        } else if (insideNegativeWindow(nS)) {
            log.debug("Command inside negative sliding window, ignoring");
        } else {
            log.warn("Command outside sliding window, entering lockout state");
            lockout = true;
        }
    }

    private void processControlCommand(byte[] data, int offset, int length) {
        if (length == 1 && data[offset] == 0) { // unlock
            farmBCounter++;
            retransmit = false;
            waitFlag = false;
            lockout = false;
            log.info("USLP COP-1 unlock on VC {}", vcId);
        } else if (length == 3 && (data[offset] & 0xFF) == 0x82) { // set VR
            farmBCounter++;
            if (lockout) {
                log.debug("setVR command ignored because in lockout state");
            } else {
                retransmit = false;
                waitFlag = false;
                vR = data[offset + 2] & 0xFF;
                log.info("USLP COP-1 setVR={} on VC {}", vR, vcId);
            }
        } else {
            log.warn("Unknown USLP control command on VC {}: {}", vcId,
                    StringConverter.arrayToHexString(data, offset, length));
        }
    }

    private void processCommand(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length).slice();
        SimulatorCcsdsPacket packet = (CcsdsPacket.getAPID(bb) == CfdpCcsdsPacket.APID)
                ? new CfdpCcsdsPacket(bb)
                : new ColumbusCcsdsPacket(bb);
        simulator.processTc(packet);
    }

    public int getCLCW() {
        // 1 bit control word type = 0
        // 2 bits version number = 00
        // 3 bits status field = 000
        // 2 bits cop in effect = 01
        // 6 bits virtual channel identifier
        // 2 bits spare = 00
        // 1 bit no rf available = 0
        // 1 bit no bit lock = 0
        // 1 bit lockout
        // 1 bit waitFlag
        // 1 bit retransmit
        // 2 bit FARM-B counter
        // 1 bit spare
        // 8 bit vR
        return (1 << 24) + (vcId << 18) + (bit(lockout) << 13)
                + (bit(waitFlag) << 12) + (bit(retransmit) << 11)
                + ((farmBCounter & 3) << 9) + vR;
    }

    private boolean insidePositiveWindow(int nS) {
        if (nS < vR) {
            nS += 256;
        }
        return nS < vR + windowWidth;
    }

    private boolean insideNegativeWindow(int nS) {
        int v = vR;
        if (v < nS) {
            v += 256;
        }
        return nS >= v - windowWidth;
    }

    private static int incr(int v) {
        return (v + 1) & 0xFF;
    }

    static int bit(boolean b) {
        return b ? 1 : 0;
    }
}
