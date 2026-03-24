package org.yamcs.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Simulator uplink link for USLP transfer frames as per CCSDS 732.1-B-3.
 * <p>
 * Receives raw USLP frames over UDP (no CLTU encoding), routes them to a per-VC handler.
 */
public class UdpUslpFrameLink extends AbstractExecutionThreadService {
    private static final Logger log = LoggerFactory.getLogger(UdpUslpFrameLink.class);

    final ColSimulator simulator;
    final int port;
    private DatagramSocket socket;
    final DatagramPacket datagram;
    final UslpVcFrameLink[] vcHandlers = new UslpVcFrameLink[64];

    public UdpUslpFrameLink(ColSimulator simulator, int port) {
        this.simulator = simulator;
        this.port = port;
        // 65535 is the max USLP frame length
        datagram = new DatagramPacket(new byte[65535], 65535);
        vcHandlers[0] = new UslpVcFrameLink(simulator, 0);
    }

    @Override
    protected void startUp() throws IOException {
        socket = new DatagramSocket(port);
        log.info("Listening for USLP frames on UDP port {}", port);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            socket.receive(datagram);
            processFrame(datagram.getData(), datagram.getOffset(), datagram.getLength());
        }
    }

    @Override
    protected void shutDown() {
        if (socket != null) {
            socket.close();
        }
    }

    private void processFrame(byte[] data, int offset, int length) {
        if (length < 7) {
            log.warn("USLP frame too short: {} bytes", length);
            return;
        }
        int version = (data[offset] & 0xFF) >> 4;
        if (version != 12) {
            log.warn("Expected USLP version 12, got {}", version);
            return;
        }
        // bits 10-5 of the first 4-byte word
        int vcId = (ByteArrayUtils.decodeInt(data, offset) >> 5) & 0x3F;

        UslpVcFrameLink handler = (vcId < vcHandlers.length) ? vcHandlers[vcId] : null;
        if (handler == null) {
            log.warn("No USLP handler configured for VC {}", vcId);
        } else {
            handler.processFrame(data, offset, length);
        }
    }

    public int getClcw() {
        return vcHandlers[0].getCLCW();
    }
}
