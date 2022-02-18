package org.yamcs.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * Simulator link implementing the TM frames using one of the three CCSDS specs:
 * 
 * AOS CCSDS 732.0-B-3
 * TM CCSDS 132.0-B-2
 * USLP CCSDS 732.1-B-1
 * 
 * 
 * Sends frames of predefined size at a configured frequency. If there is no data to send, it sends idle frames.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractScheduledService {
    final String frameType;
    final String host;
    final int port;
    final int frameSize;

    DatagramSocket socket;
    static final int NUM_VC = 3;
    static final int SPACECRAFT_ID = 0xAB;
    final double framesPerSec;
    final static CrcCciitCalculator crc = new CrcCciitCalculator();
    VcBuilder[] builders = new VcBuilder[NUM_VC];
    VcBuilder idleFrameBuilder;

    private static final Logger log = LoggerFactory.getLogger(UdpTmFrameLink.class);

    int lastVcSent; // switches between 0 and 1 so we don't send always from the same vc

    InetAddress addr;
    IntSupplier clcwSupplier;

    public UdpTmFrameLink(String frameType, String host, int port, int frameLength, double framesPerSec,
            IntSupplier clcwSupplier) {
        this.frameType = frameType;
        this.host = host;
        this.port = port;
        this.frameSize = frameLength;
        this.framesPerSec = framesPerSec;
        this.clcwSupplier = clcwSupplier;

        if ("AOS".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new AosVcSender(i, frameLength);
            }
            idleFrameBuilder = new AosVcSender(63, frameLength);
        } else if ("TM".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new TmVcSender(i, frameLength);
            }
            idleFrameBuilder = builders[0];
        } else if ("USLP".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new UslpVcSender(i, frameLength);
            }
            idleFrameBuilder = new UslpVcSender(63, frameLength);
        }

    }

    @Override
    protected void startUp() throws Exception {
        addr = InetAddress.getByName(host);
        socket = new DatagramSocket();
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, (long) (1e6 / framesPerSec), TimeUnit.MICROSECONDS);
    }

    @Override
    protected void runOneIteration() throws Exception {
        // dequeue data from all frames
        for (int i = 0; i < NUM_VC; i++) {
            builders[i].dequeue();
        }
        // if one of the first VCs is full, send it
        for (int i = 0; i < 2; i++) {
            int vc = (lastVcSent + i) & 1;
            if (builders[vc].isFull()) {
                sendData(builders[vc]);
                return;
            }
        }

        int mini = -1;
        int minData = Integer.MAX_VALUE;
        // send the first one that has least empty space
        for (int i = 0; i < NUM_VC; i++) {
            if (builders[i].isEmpty()) {
                continue;
            }
            int emptySpace = builders[i].emtySpaceLength();
            if (emptySpace < minData) {
                mini = i;
                minData = emptySpace;
            }
        }
        if (mini != -1) {
            sendData(builders[mini]);
            return;
        }
        // no data available for any VC, send an idle frame
        byte[] idleData = idleFrameBuilder.getIdleFrame();
        socket.send(new DatagramPacket(idleData, idleData.length, addr, port));
    }

    private void sendData(VcBuilder vcb) throws IOException {
        vcb.setCLCW(clcwSupplier.getAsInt());
        if (!vcb.isFull()) {
            vcb.fillIdlePacket();
        }

        byte[] data = vcb.getFrame();

        socket.send(new DatagramPacket(data, data.length, addr, port));
        vcb.reset();
    }

    /**
     * queue packet for virtual channel
     * 
     * @param vcId
     * @param packet
     */
    public void queuePacket(int vcId, byte[] packet) {
        VcBuilder s = builders[vcId];

        if (!s.queue.offer(packet)) {
            log.warn("dropping packet for virtual channel {} because the queue is full", vcId);
        }
    }

    static abstract class VcBuilder {
        final int vcId;

        protected long vcSeqCount = 0;
        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(100);
        protected int dataOffset;

        byte[] pendingPacket;
        int pendingPacketOffset;
        byte[] data;

        boolean firstPacketInFrame = true;
        int firstHeaderPointer = -1;
        int dataEnd;

        protected int clcw;

        public VcBuilder(int vcId) {
            this.vcId = vcId;
            this.dataOffset = hdrSize();

        }

        public void setCLCW(int clcw) {
            this.clcw = clcw;
        }

        public int emtySpaceLength() {
            return dataEnd - dataOffset;
        }

        public byte[] getFrame() {
            encodeHeaderAndTrailer();
            return data;
        }

        public boolean isEmpty() {
            return dataOffset == hdrSize();
        }

        public boolean isFull() {
            return dataOffset == dataEnd;
        }

        void reset() {
            vcSeqCount++;
            firstPacketInFrame = true;
            dataOffset = hdrSize();
        }

        /**
         * Copy data from the queue into the frame
         * 
         * @param q
         * @return
         * @throws IOException
         */
        void dequeue() throws IOException {
            if (pendingPacket != null) {
                copyPendingToBuffer();
                if (pendingPacket != null) {// not yet fully copied but the frame is full
                    return;
                }
            }
            while (dataOffset < dataEnd) {
                pendingPacket = queue.poll();
                if (pendingPacket == null) {
                    break;
                }
                if (firstPacketInFrame) {
                    firstHeaderPointer = dataOffset - hdrSize();
                    firstPacketInFrame = false;
                }
                pendingPacketOffset = 0;
                copyPendingToBuffer();
                if (pendingPacket != null) {// not yet fully copied but the frame is full
                    break;
                }
            }
        }

        void copyPendingToBuffer() {
            int length = Math.min(pendingPacket.length - pendingPacketOffset, dataEnd - dataOffset);
            log.trace("VC{} writing {} bytes from packet of length {} at offset {}",
                    vcId, length, pendingPacket.length, dataOffset);
            ;
            System.arraycopy(pendingPacket, pendingPacketOffset, data, dataOffset, length);
            dataOffset += length;
            pendingPacketOffset += length;
            if (pendingPacketOffset == pendingPacket.length) {
                pendingPacket = null;
            }
        }

        private void fillIdlePacket() {
            int n = dataEnd - dataOffset;
            log.trace("VC{} writing idle packet of size {} at offset {}", vcId, n, dataOffset);
            if (n == 0) {
                return;
            } else if (n == 1) {
                data[dataOffset] = (byte) 0xE0;
            } else if (n < 254) {
                data[dataOffset] = (byte) 0xE1;
                data[dataOffset + 1] = (byte) n;
            } else {
                data[dataOffset] = (byte) 0xE2;
                data[dataOffset + 1] = 0;
                ByteArrayUtils.encodeUnsignedShort(n, data, dataOffset + 2);
            }
            dataOffset += n;
        }

        abstract int hdrSize();

        abstract void encodeHeaderAndTrailer();

        abstract public byte[] getIdleFrame();
    }

    static class AosVcSender extends VcBuilder {

        public AosVcSender(int vcId, int frameSize) {
            super(vcId);
            if ((vcId < 0) || (vcId > 63)) {
                throw new IllegalArgumentException("Invalid virtual channel id " + vcId);
            }

            this.data = new byte[frameSize];
            dataEnd = frameSize - 6;// last 6 bytes are the OCF and CRC
            writeGvcId(data, vcId);

        }

        void writeGvcId(byte[] frameData, int vcId) {
            ByteArrayUtils.encodeUnsignedShort((1 << 14) + (SPACECRAFT_ID << 6) + vcId, frameData, 0);
        }

        @Override
        int hdrSize() {
            // 2 bytes master channel id
            // 3 bytes virtual channel frame count
            // 1 byte signaling field
            // 2 bytes frame header error control
            // 2 bytes M_PDU header

            // NOTE: there is no insert zone; if there should be any, its size should be added as part of the header
            // size
            return 10;
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count

            ByteArrayUtils.encodeUnsigned3Bytes((int) vcSeqCount, data, 2);
            data[5] = (byte) (0x60 + ((vcSeqCount >>> 24) & 0xF));

            ByteArrayUtils.encodeInt(clcw, data, data.length - 6);

            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, 8);
            fillChecksums(data);

        }

        static void fillChecksums(byte[] data) {
            // first Reed-Solomon the header
            int gvcid = ByteArrayUtils.decodeUnsignedShort(data, 0);
            int x = AosFrameHeaderErrorCorr.encode(gvcid, data[5]);
            ByteArrayUtils.encodeUnsignedShort(x, data, 6);

            // then overall CRC
            x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }

        @Override
        public byte[] getIdleFrame() {
            vcSeqCount++;
            encodeHeaderAndTrailer();
            return data;
        }
    }

    static class TmVcSender extends VcBuilder {
        byte[] idleFrameData;
        int ocfFlag = 1;

        public TmVcSender(int vcId, int frameSize) {
            super(vcId);
            this.data = new byte[frameSize];
            dataEnd = frameSize - 4 - 2 * ocfFlag; // last 6 bytes are the OCF and CRC
            writeGvcId(data, vcId);
        }

        @Override
        int hdrSize() {
            return 6;
        }

        void writeGvcId(byte[] frameData, int vcId) {
            ByteArrayUtils.encodeUnsignedShort((SPACECRAFT_ID << 4) + (vcId << 1) + ocfFlag, frameData, 0);
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count
            data[3] = (byte) (vcSeqCount);

            // write the first header pointer
            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, 4);

            ByteArrayUtils.encodeInt(clcw, data, data.length - 6);

            // compute crc
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }

        @Override
        public byte[] getIdleFrame() {
            ByteArrayUtils.encodeUnsignedShort(0x7FE, data, 4);
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);

            vcSeqCount++;

            return data;

        }
    }

    /**
     * This builds USLP frames with complete primary header, OCF , no insert data, and 32 bits frame count
     *
     */
    static class UslpVcSender extends VcBuilder {
        byte[] idleFrameData;
        int ocfFlag = 1;

        public UslpVcSender(int vcId, int frameLength) {
            super(vcId);
            this.data = new byte[frameLength];
            dataEnd = frameLength - 4 - 2 * ocfFlag; // last 6 bytes are the OCF and CRC

            ByteArrayUtils.encodeInt((12 << 28) + (SPACECRAFT_ID << 12) + (vcId << 5), data, 0);

            // frame length
            ByteArrayUtils.encodeUnsignedShort(frameLength - 1, data, 4);

            data[6] = 0x0C; // ocfFlag = 1, vc frame count = 100(in binary)

        }

        @Override
        int hdrSize() {
            // 11 for the primary header (with a 32 bit frame length)
            // 3 bytes for the data field header
            return 14;
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count
            ByteArrayUtils.encodeInt((int) vcSeqCount, data, 7);

            // write the first header pointer
            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, 12);

            if (ocfFlag == 1) {
                ByteArrayUtils.encodeInt(clcw, data, data.length - 6);
            }
            // compute crc
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }

        @Override
        public byte[] getIdleFrame() {
            vcSeqCount++;
            encodeHeaderAndTrailer();
            return data;
        }
    }
}
