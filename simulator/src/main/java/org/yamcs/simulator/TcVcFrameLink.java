package org.yamcs.simulator;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.StandardAuthMask;
import org.yamcs.simulator.cfdp.CfdpCcsdsPacket;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.security.sdls.SdlsSecurityAssociation.VerificationStatusCode;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

/**
 * Works as a child of {@link UdpTcFrameLink} and handles commands for one VC.
 *
 * * It implements FARM part of the COP-1 protocol CCSDS 232.1-B-2 ( COMMUNICATIONS OPERATION PROCEDURE-1)
 *
 */
public class TcVcFrameLink {
    private static final Logger log = LoggerFactory.getLogger(TcVcFrameLink.class);
    final static CrcCciitCalculator crc = new CrcCciitCalculator();
    static final int PRIMARY_HEADER_LENGTH = 5;
    static final int MAX_FRAME_LENGTH = 1024;
    final AbstractSimulator simulator;
    final TcPacketFactory packetFactory;
    final int crcBytes;
    final int maxFrameLength;
    final int expectedScid;

    // FARM parameters
    boolean lockout;
    boolean retransmit;
    boolean waitFlag = false;// this is set to true when we are overloaded with commands, but we don't use this one
    int vR;
    int windowWidth = 15; // positive/negative sliding window width PW=NW
    final int vcId;

    int farmBCounter;

    byte[] authMask;

    // Optionally, a security association to encrypt/decrypt data on the link
    SdlsSecurityAssociation maybeSdls = null;

    public TcVcFrameLink(ColSimulator simulator, int vcId, SdlsSecurityAssociation maybeSdls) {
        this(simulator, vcId, maybeSdls, TcPacketFactory.COL_PACKET_FACTORY);
    }

    public TcVcFrameLink(AbstractSimulator simulator, int vcId, SdlsSecurityAssociation maybeSdls,
            TcPacketFactory packetFactory) {
        this(simulator, vcId, maybeSdls, packetFactory, true, MAX_FRAME_LENGTH, -1);
    }

    TcVcFrameLink(AbstractSimulator simulator, int vcId, SdlsSecurityAssociation maybeSdls,
            TcPacketFactory packetFactory, boolean withCrc, int maxFrameLength, int expectedScid) {
        this.simulator = simulator;
        this.vcId = vcId;
        this.packetFactory = packetFactory;
        this.crcBytes = withCrc ? 2 : 0;
        this.maxFrameLength = maxFrameLength;
        this.expectedScid = expectedScid;
        if (maxFrameLength < PRIMARY_HEADER_LENGTH + crcBytes || maxFrameLength > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("Invalid TC maximum frame length " + maxFrameLength);
        }
        if (expectedScid < -1 || expectedScid > 0x3FF) {
            throw new IllegalArgumentException("Invalid TC spacecraft identifier " + expectedScid);
        }

        // If we have an encryption key, configure encryption
        if (maybeSdls != null) {
            // Create an auth mask for the TC primary header,
            // the frame data is already part of authentication.
            this.maybeSdls = maybeSdls;
            authMask = StandardAuthMask.TC(false, maybeSdls.securityHdrAuthMask());
            // Don't verify the first TC sequence number, because Yamcs has sequence number persistence and the
            // simulator does not.
            maybeSdls.skipVerifyingNextSeqNum();
        }
    }

    void processTcFrame(byte[] data, int offset, int length) {
        if (length < PRIMARY_HEADER_LENGTH) {
            log.warn("Incomplete TC frame header ({} bytes)", length);
            return;
        }
        byte d0 = data[offset];
        int vn = (d0 & 0xFF) >>> 6;

        boolean bypassFlag = ((d0 >> 5) & 1) == 1;

        boolean controlCommand = ((d0 >> 4) & 1) == 1;

        int spacecraftId = ByteArrayUtils.decodeUnsignedShort(data, offset) & 0x3FF;
        int d23 = ByteArrayUtils.decodeUnsignedShort(data, offset + 2);
        int virtualChannelId = d23 >> 10;
        int frameLength = 1 + (d23 & 0x3FF);

        int frameSeq = data[offset + 4] & 0xFF;
        log.info(
                "Received TC frame data length: {}, frameLength: {}, spacecraftId: {}, VC: {}, frameSeq: {}, " +
                        "bypassFlag: {}",
                length, frameLength, spacecraftId, virtualChannelId, frameSeq, bypassFlag);

        if (vn != 0) {
            log.warn("Invalid frame version number {} received; expecting 0; ignoring frame", vn);
            return;
        }
        if (expectedScid >= 0 && spacecraftId != expectedScid) {
            log.warn("Ignoring TC frame for spacecraft {} (expected {})", spacecraftId, expectedScid);
            return;
        }
        if (frameLength < PRIMARY_HEADER_LENGTH + crcBytes || frameLength > length || frameLength > maxFrameLength) {
            log.warn("Invalid TC frame length {} (received {} bytes, configured maximum {})", frameLength, length,
                    maxFrameLength);
            return;
        }
        int dataEnd = offset + frameLength - crcBytes;
        if (crcBytes != 0 && crc.compute(data, offset, frameLength - crcBytes) != ByteArrayUtils.decodeUnsignedShort(data,
                dataEnd)) {
            log.warn("CRC check failed for TC frame: {}", StringConverter.arrayToHexString(data, offset, frameLength, true));
            return;
        }
        int headerOffset = offset;
        offset += PRIMARY_HEADER_LENGTH;
        int cmdLength = dataEnd - offset;

        // COP-1 control frames are sent in the clear.
        if (maybeSdls != null && !controlCommand) {
            if (cmdLength < maybeSdls.getOverheadBytes()) {
                log.warn("TC frame data field is shorter than its security overhead");
                return;
            }
            VerificationStatusCode decryptionStatus = maybeSdls.processSecurity(data, headerOffset, offset, dataEnd,
                    authMask);
            if (decryptionStatus != VerificationStatusCode.NoFailure) {
                log.warn("Could not decrypt frame: {}", decryptionStatus);
                return;
            }

            // Adjustments to account for security header/trailer
            cmdLength -= maybeSdls.getOverheadBytes();
            offset += maybeSdls.getHeaderSize();
        }

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
        while (length > 0) {
            if (length < 6) {
                log.warn("TC frame data field is too short for a CCSDS packet header ({} bytes)", length);
                return;
            }
            int packetLength = packetFactory.getPacketLength(data, offset, length);
            if (packetLength > length) {
                log.warn("TC packet length {} exceeds remaining frame data ({} bytes)", packetLength, length);
                return;
            }
            ByteBuffer bb = ByteBuffer.wrap(data, offset, packetLength).slice();
            SimulatorCcsdsPacket packet;
            if (CcsdsPacket.getAPID(bb) == CfdpCcsdsPacket.APID) {
                packet = new CfdpCcsdsPacket(bb);
            } else {
                if (packetLength < packetFactory.getMinLength()) {
                    log.warn("TC frame data field is too short for the configured packet type ({} bytes)", packetLength);
                    return;
                }
                packet = packetFactory.getPacket(Arrays.copyOfRange(data, offset, offset + packetLength));
            }
            simulator.processTc(packet);
            offset += packetLength;
            length -= packetLength;
        }
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
