package org.yamcs.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.SdlsSecurityAssociation;
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

    SdlsSecurityAssociation maybeSdls = null;

    public UdpTmFrameLink(String frameType, String host, int port, int frameLength, double framesPerSec,
                          IntSupplier clcwSupplier, byte[] maybeSdlsKey, short sdlsSpi,
                          int encryptionSeqNumWindow, boolean verifySeqNum) {
        this.frameType = frameType;
        this.host = host;
        this.port = port;

        // If the link should be encrypted, the maximum amount of data is reduced because of encryption overhead.
        if (maybeSdlsKey != null) {
            this.frameSize = frameLength - SdlsSecurityAssociation.getOverheadBytes();
            this.maybeSdls = new SdlsSecurityAssociation(maybeSdlsKey, sdlsSpi,
                    encryptionSeqNumWindow, verifySeqNum);
        } else {
            this.frameSize = frameLength;
        }

        this.framesPerSec = framesPerSec;
        this.clcwSupplier = clcwSupplier;

        if ("AOS".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new AosVcSender(i, frameLength, maybeSdls);
            }
            idleFrameBuilder = new AosVcSender(63, frameLength, maybeSdls);
        } else if ("TM".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new TmVcSender(i, frameLength, maybeSdls);
            }
            idleFrameBuilder = builders[0];
        } else if ("USLP".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new UslpVcSender(i, frameLength, maybeSdls);
            }
            idleFrameBuilder = new UslpVcSender(63, frameLength, maybeSdls);
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

        // Optionally, a security assciation to use for an encrypted link
        SdlsSecurityAssociation maybeSdls = null;
        byte[] authMask;

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

        abstract void encryptFrame();

        public byte[] getFrame() {
            // Encrypt the frame if we have a security association
            if (maybeSdls != null) {
                encryptFrame();
            }
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

            // If we're running with SDLS, zero the security header and trailer
            if (this.maybeSdls != null) {
                int secHeaderSize = SdlsSecurityAssociation.getHeaderSize();
                int secHeaderOffset = dataOffset - secHeaderSize;
                Arrays.fill(data, secHeaderOffset, secHeaderOffset + secHeaderSize, (byte) 0);

                int secTrailerSize = SdlsSecurityAssociation.getTrailerSize();
                int secTrailerOffset = dataEnd;
                Arrays.fill(data, secTrailerOffset, secTrailerOffset + secTrailerSize, (byte) 0);
            }
        }

        /**
         * Copy data from the queue into the frame
         *
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

        public AosVcSender(int vcId, int frameSize, SdlsSecurityAssociation maybeLinkSdls) {
            super(vcId);
            if ((vcId < 0) || (vcId > 63)) {
                throw new IllegalArgumentException("Invalid virtual channel id " + vcId);
            }

            this.data = new byte[frameSize];
            dataEnd = frameSize - 6;// last 6 bytes are the OCF and CRC

            // Optionally encrypt the data
            if (maybeLinkSdls != null) {
                maybeSdls = maybeLinkSdls;
                // Create an auth mask for the primary header,
                // the frame data is already part of authentication.
                // No need to authenticate data, already part of GCM
                authMask = new byte[frameHdrSize()];
                authMask[1] = 0b0011_1111; // authenticate only virtual channel ID

                // SDLS reduces end of data by the size of the trailer
                dataEnd -= SdlsSecurityAssociation.getTrailerSize();
                // And the start of data by the size of the headers
                dataOffset = hdrSize();
            }

            writeGvcId(data, vcId);

        }

        void writeGvcId(byte[] frameData, int vcId) {
            ByteArrayUtils.encodeUnsignedShort((1 << 14) + (SPACECRAFT_ID << 6) + vcId, frameData, 0);
        }

        int frameHdrSize() {
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
        int hdrSize() {
            // If we're encrypting, the header size includes the security header.
            // Otherwise, it's just the primary header.
            int size = frameHdrSize();
            if (this.maybeSdls != null) {
                size += SdlsSecurityAssociation.getHeaderSize();
            }
            return size;
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
            if (this.maybeSdls != null) {
                encryptFrame();
            }
            encodeHeaderAndTrailer();
            return data;
        }

        @Override
        void encryptFrame() {
            if (this.maybeSdls == null) {
                log.warn("Tried to encrypt a frame, but no Security Association is present");
                return;
            }
            try {
                this.maybeSdls.applySecurity(data, 0, hdrSize(),
                        dataEnd + SdlsSecurityAssociation.getTrailerSize(), authMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame: {}", e);
            }
        }
    }

    static class TmVcSender extends VcBuilder {
        byte[] idleFrameData;
        int ocfFlag = 1;

        public TmVcSender(int vcId, int frameSize, SdlsSecurityAssociation maybeLinkSdls) {
            super(vcId);
            this.data = new byte[frameSize];
            dataEnd = frameSize - 4 - 2 * ocfFlag; // last 6 bytes are the OCF and CRC

            // Optionally encrypt the frame
            if (maybeLinkSdls != null) {
                // Create an auth mask for the primary header,
                // the frame data is already part of authentication.
                // No need to authenticate data, already part of GCM
                authMask = new byte[frameHdrSize()];
                authMask[1] = 0b0000_1110; // authenticate virtual channel ID

                this.maybeSdls = maybeLinkSdls;

                // SDLS reduces end of data by the size of the trailer
                dataEnd -= SdlsSecurityAssociation.getTrailerSize();
                // And start of data by the headers
                dataOffset = hdrSize();
            }
            writeGvcId(data, vcId);

        }

        @Override
        int hdrSize() {
            // If we're encrypting, the header size includes the security header.
            // Otherwise, it's just the primary header.
            int size = frameHdrSize();
            if (this.maybeSdls != null) {
                size += SdlsSecurityAssociation.getHeaderSize();
            }
            return size;
        }

        // Without encryption
        int frameHdrSize() {
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
            if (this.maybeSdls != null) {
                try {
                    this.maybeSdls.applySecurity(data, 0, hdrSize(),
                            dataEnd + SdlsSecurityAssociation.getTrailerSize(), authMask);
                } catch (GeneralSecurityException e) {
                    log.warn("could not encrypt idle frame: {}", e);
                }
            }

            ByteArrayUtils.encodeUnsignedShort(0x7FE, data, 4);
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);

            vcSeqCount++;

            return data;

        }

        @Override
        void encryptFrame() {
            if (this.maybeSdls == null) {
                log.warn("Tried to encrypt a frame, but no Security Association is present");
                return;
            }
            try {
                this.maybeSdls.applySecurity(data, 0, hdrSize(),
                        dataEnd + SdlsSecurityAssociation.getTrailerSize(), authMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame: {}", e);
            }
        }

    }

    /**
     * This builds USLP frames with complete primary header, OCF , no insert data, and 32 bits frame count
     *
     */
    static class UslpVcSender extends VcBuilder {
        byte[] idleFrameData;
        int ocfFlag = 1;

        public UslpVcSender(int vcId, int frameLength, SdlsSecurityAssociation maybeLinkSdls) {
            super(vcId);
            this.data = new byte[frameLength];
            dataEnd = frameLength - 4 - 2 * ocfFlag; // last 6 bytes are the OCF and CRC

            ByteArrayUtils.encodeInt((12 << 28) + (SPACECRAFT_ID << 12) + (vcId << 5), data, 0);

            // frame length
            ByteArrayUtils.encodeUnsignedShort(frameLength - 1, data, 4);

            data[6] = 0x0C; // ocfFlag = 1, vc frame count = 100(in binary)

            // Optionally encrypt the data
            if (maybeLinkSdls != null) {
                authMask = new byte[frameHdrSize()];
                authMask[2] = 0b111; // top 3 bits of vcid
                authMask[3] = (byte) 0b1111_1110; // bottom 3 bits of vcid, 4 bits of map id
                this.maybeSdls = maybeLinkSdls;

                // SDLS reduces end of data by the size of the trailer
                dataEnd -= SdlsSecurityAssociation.getTrailerSize();
                dataOffset = hdrSize();
            }
        }

        @Override
        int hdrSize() {
            // If we're encrypting, the header size includes the security header.
            // Otherwise, it's just the primary header.
            int size = frameHdrSize();
            if (this.maybeSdls != null) {
                size += SdlsSecurityAssociation.getHeaderSize();
            }
            return size;
        }

        int frameHdrSize() {
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
            if (this.maybeSdls != null) {
                encryptFrame();
            }
            encodeHeaderAndTrailer();
            return data;
        }

        @Override
        void encryptFrame() {
            if (this.maybeSdls == null) {
                log.warn("Tried to encrypt a frame, but no Security Association is present");
                return;
            }
            try {
                int dataStart = hdrSize();
                int secTrailerEnd = dataEnd + SdlsSecurityAssociation.getTrailerSize();
                this.maybeSdls.applySecurity(data, 0, dataStart, secTrailerEnd, authMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame: {}", e);
            }
        }
    }
}