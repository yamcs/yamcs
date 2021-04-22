package org.yamcs.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tctm.ccsds.error.BchCltuGenerator.BchEncoder;
import org.yamcs.tctm.ccsds.error.CltuGenerator.Encoding;
import org.yamcs.tctm.ccsds.error.Ldpc64CltuGenerator.Ldpc64Encoder;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Simulator TC link implementing the
 * CCSDS 232.0-B-3 (TC SPACE DATA LINK PROTOCOL)
 * <p>
 * and partly
 * CCSDS 231.0-B-3 (TC SYNCHRONIZATION AND CHANNEL CODING)
 * <p>
 * 
 * It receives TCs as CLTUs via UDP (one UDP frame = one CLTU)
 * 
 */
public class UdpTcFrameLink extends AbstractExecutionThreadService {
    final ColSimulator simulator;
    int port;
    private DatagramSocket socket;
    DatagramPacket datagram;
    private static final Logger log = LoggerFactory.getLogger(UdpTcFrameLink.class);

    Encoding enc = Encoding.BCH;
    boolean ldpcTailSeq; // if enc is LDPC64 or LDPC256
    TcVcFrameLink[] vcHandlers;
    int[] clcw;

    public UdpTcFrameLink(ColSimulator simulator, int port) {
        this.simulator = simulator;
        this.port = port;
        datagram = new DatagramPacket(new byte[2048], 2048);
        vcHandlers = new TcVcFrameLink[] { new TcVcFrameLink(simulator, 0) };
        clcw = new int[] { vcHandlers[0].getCLCW() };
    }

    @Override
    public void startUp() throws IOException {
        socket = new DatagramSocket(port);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            socket.receive(datagram);
            if (enc == Encoding.BCH) {
                processBCH_CLTU(datagram.getData(), datagram.getOffset(), datagram.getLength());
            } else {
                processLDPC_CLTU(datagram.getData(), datagram.getOffset(), datagram.getLength());
            }
        }

    }

    private void processBCH_CLTU(byte[] data, int offset, int length) {
        if (length < 10) {
            log.warn("Invalid CLTU, length {} (expected at least 10)", length);
            return;
        }
        int ss = ByteArrayUtils.decodeUnsignedShort(data, offset);
        if (ss != 0xEB90) {
            log.warn("Invalid BCH start sequence 0x" + Integer.toHexString(ss)+" expected 0xEB90");
            return;
        }
        offset += 2;
        length -= 2;
        long ts = ByteArrayUtils.decodeLong(data, offset + length - 8);
        if (ts != 0xC5C5_C5C5_C5C5_C579L) {
            log.warn("Invalid BCH tail sequence " + Long.toHexString(ts));
            return;
        }
        length -= 8;
        if ((length & 7) != 0) {
            log.warn("Invalid length of encoded data {}, expected multiple of 8 bytes (BCH codeblock length)", length);
            return;
        }
        int cb = length >> 3;
        byte[] tcframe = new byte[7 * cb];
        int tcoff = 0;
        for (int i = 0; i < cb; i++) {
            // we don't have a BCH decoder so we just verify that the data matches
            byte b = BchEncoder.encode(data, offset);
            if (data[offset + 7] != b) {
                log.warn("Failed to decode BCH data at offset " + offset);
                return;
            }
            System.arraycopy(data, offset, tcframe, tcoff, 7);
            offset += 8;
            tcoff += 7;
        }
        processTcFrame(tcframe, 0, tcframe.length);
    }

    private void processLDPC_CLTU(byte[] data, int offset, int length) {
        int minLength = 8 + (ldpcTailSeq ? 16 : 0);
        if (length < minLength) {
            log.warn("Invalid CLTU, length {} (expected at least {})", length, minLength);
            return;
        }
        long ss = ByteArrayUtils.decodeLong(data, offset);
        if (ss != 0x0347_76C7_2728_95B0L) {
            log.warn("Invalid LDLC start sequence {}", Long.toHexString(ss));
            return;
        }
        offset += 8;
        length -= 8;
        if (ldpcTailSeq) {
            long ts0 = ByteArrayUtils.decodeLong(data, offset + length - 16);
            long ts1 = ByteArrayUtils.decodeLong(data, offset + length - 8);
            if (ts0 != 0x5555_5556_AAAA_AAAAL || ts1 != 0x5555_5555_5555_5555L) {
                log.warn("Invalid LDLC tail sequence {}{}", Long.toHexString(ts0), Long.toHexString(ts1));
                return;
            }
            length -= 16;
        }
        if (enc == Encoding.LDCP64) {
            processLDPC64(data, offset, length);
        } else {
            processLDPC256(data, offset, length);
        }
    }

    private void processLDPC64(byte[] data, int offset, int length) {
        if ((length & 0xF) != 0) {
            log.warn("Invalid length of encoded data {}, expected multiple of 16 bytes (LDPC64 codeblock length)",
                    length);
            return;
        }
        int cb = length >> 4;
        byte[] tcframe = new byte[8 * cb];
        int tcoff = 0;
        byte[] tmp = new byte[8];
        for (int i = 0; i < cb; i++) {
            // we don't have a LDPC decoder so we just verify that the data matches
            Ldpc64Encoder.encode(data, offset, tmp, 0);
            if (!equals(data, offset + 8, tmp, 0, 8)) {
                log.warn("Failed to decode LDPC data at offset " + offset);
                return;
            }
            System.arraycopy(data, offset, tcframe, tcoff, 8);
            offset += 16;
            tcoff += 8;
        }
        processTcFrame(tcframe, 0, tcframe.length);

    }

    private void processLDPC256(byte[] data, int offset, int length) {
        if ((length & 0x1F) != 0) {
            log.warn("Invalid length of encoded data {}, expected multiple of 32 bytes (LDPC256 codeblock length)",
                    length);
            return;
        }
        int cb = length >> 5;
        byte[] tcframe = new byte[16 * cb];
        int tcoff = 0;
        byte[] tmp = new byte[16];

        for (int i = 0; i < cb; i++) {
            // we don't have a LDPC decoder so we just verify that the data matches
            Ldpc64Encoder.encode(data, offset, tmp, 0);
            if (!equals(data, offset + 16, tmp, 0, 16)) {
                log.warn("Failed to decode LDPC data at offset " + offset);
                return;
            }
            System.arraycopy(data, offset, tcframe, tcoff, 16);
            offset += 32;
            tcoff += 16;
        }
        processTcFrame(tcframe, 0, tcframe.length);
    }

    private boolean equals(byte[] a1, int a1Offset, byte[] a2, int a2Offset, int length) {
        for (int i = 0; i < length; i++)
            if (a1[i + a1Offset] != a2[i + a2Offset])
                return false;

        return true;
    }

    private void processTcFrame(byte[] data, int offset, int length) {
        int vcId = (data[offset + 2] & 0xFF) >> 2;
        TcVcFrameLink vcfl;
        if (vcId >= vcHandlers.length || (vcfl = vcHandlers[vcId]) == null) {
            log.warn("No TC handler for VC {}", vcId);
        } else {
            vcfl.processTcFrame(data, offset, length);
            clcw[vcId] = vcfl.getCLCW();
        }
    }

    private int nextClcwVc;

    public int getClcw() {
        nextClcwVc++;
        if (nextClcwVc >= vcHandlers.length) {
            nextClcwVc = 0;
        }
        return clcw[nextClcwVc];
    }
}
