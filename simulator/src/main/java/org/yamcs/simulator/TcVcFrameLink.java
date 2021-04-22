package org.yamcs.simulator;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

/**
 * Works as a child of {@link UdpTcFrameLink} and handles commands for one VC.
 * 
 * * It implements FARM part of the COP-1 protocol
 * CCSDS 232.1-B-2 ( COMMUNICATIONS OPERATION PROCEDURE-1)
 * 
 * @author nm
 *
 */
public class TcVcFrameLink {
    private static final Logger log = LoggerFactory.getLogger(TcVcFrameLink.class);
    final static CrcCciitCalculator crc = new CrcCciitCalculator();
    final ColSimulator simulator;

    // FARM parameters
    boolean lockout;
    boolean retransmit;
    boolean waitFlag = false;// this is set to true when we are overloaded with commands, but we don't use this one
    int vR;
    int windowWidth = 15; // positive/negative sliding window width PW=NW
    final int vcId;

    int farmBCounter;

    public TcVcFrameLink(ColSimulator simulator, int vcId) {
        this.simulator = simulator;
        this.vcId = vcId;
    }

    void processTcFrame(byte[] data, int offset, int length) {

        byte d0 = data[offset];
        int vn = d0 >>> 6;

        boolean bypassFlag = ((d0 >> 5) & 1) == 1;

        boolean controlCommand = ((d0 >> 4) & 1) == 1;

        int spacecraftId = ByteArrayUtils.decodeUnsignedShort(data, offset) & 0x3FF;
        int d23 = ByteArrayUtils.decodeUnsignedShort(data, offset + 2);
        int virtualChannelId = d23 >> 10;
        int frameLength = 1 + (d23 & 0x3FF);

        int frameSeq = data[offset + 4] & 0xFF;
        log.info(
                "Received TC frame data length: {}, frameLength: {}, spacecraftId: {}, VC: {}, frameSeq: {}, bypassFlag: {}",
                length, frameLength, spacecraftId, virtualChannelId, frameSeq, bypassFlag);

        if (vn != 0) {
            log.warn("Invalid frame version number {} received; expecting 0; ignoring frame", vn);
            return;
        }
        if (frameLength > length) {
            log.warn("Bad decoded frame length {}, expected max {}", frameLength, length);
            return;
        }
        int c1 = crc.compute(data, offset, frameLength - 2);
        int c2 = ByteArrayUtils.decodeShort(data, offset + frameLength - 2) & 0xFFFF;
        if (c1 != c2) {
            log.warn("CRC check failed, computed CRC: {}, frame data: {}", Integer.toHexString(c1).toUpperCase(),
                    StringConverter.arrayToHexString(data, offset, frameLength, true));
            return;
        }
        int cmdLength = frameLength - 7;
        offset += 5;
        if (controlCommand) {// BC frame
            if (bypassFlag) {
                log.warn("Invalid frame with both control and bypass flags set, ignoring");
                return;
            }
            processControlCommand(data, offset, cmdLength);
            return;
        } else if (bypassFlag) { // BD frame
            farmBCounter++;
            processCommand(data, offset, cmdLength);
        } else {// AD frame
            if (lockout) {
                log.warn("Command received in lockout state, ignoring");
                return;
            }
            if (frameSeq == vR) {
                vR = incr(vR);
                retransmit = false;
                processCommand(data, offset, cmdLength);
            } else if (insidePositiveWindow(frameSeq)) {
                log.warn("Command inside positive sliding window, ignoring command, setting retransmit=1");
                retransmit = true;
            } else if (insideNegativeWindow(frameSeq)) {
                log.debug("Command inside negative sliding window, ignoring ");
            } else { // outside window
                log.warn("Command outside sliding window, ignoring command, entering lockout state");
                lockout = true;
            }
        }
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

    private static int incr(int vR) {
        return (vR + 1) & 0xFF;
    }

    private void processCommand(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length).slice();
        simulator.processTc(new ColumbusCcsdsPacket(bb));
    }

    private void processControlCommand(byte[] data, int offset, int length) {
        if (length == 1 && data[offset] == 0) {// unlock
            farmBCounter++;
            retransmit = false;
            waitFlag = false;
            lockout = false;
        } else if (length == 3 && data[offset] == 0x82) {// set VR
            farmBCounter++;
            if (lockout) {
                log.debug("setVR command ignored because in lockout state");
            } else {
                retransmit = false;
                waitFlag = false;
                vR = data[offset + 2] & 0xFF;
            }
        } else {
            log.warn("Unknown control command {}", StringConverter.arrayToHexString(data, offset, length));
        }
    }

    public int getCLCW() {
        // 1 bit control word = 0
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

    static int bit(boolean b) {
        return b ? 1 : 0;
    }
}
