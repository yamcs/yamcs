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
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.StandardAuthMask;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * Simulator link implementing the TM frames using one of the three CCSDS specs:
 * 
 * <ul>
 * <li>AOS CCSDS 732.0-B-3
 * <li>TM CCSDS 132.0-B-2
 * <li>USLP CCSDS 732.1-B-1
 * </ul>
 * 
 * Sends frames of predefined size at a configured frequency. If there is no data to send, it sends idle frames.
 * 
 */
public class UdpTmFrameLink extends AbstractScheduledService {
    final String frameType;
    final String host;
    final int port;
    final int frameSize;

    DatagramSocket socket;
    static final int NUM_VC = 3;
    final int scid;
    final double framesPerSec;
    final static CrcCciitCalculator crc = new CrcCciitCalculator();
    VcBuilder[] builders = new VcBuilder[NUM_VC];
    VcBuilder idleFrameBuilder;

    private static final Logger log = LoggerFactory.getLogger(UdpTmFrameLink.class);

    int lastVcSent; // switches between 0 and 1 so we don't send always from the same vc

    InetAddress addr;
    IntSupplier clcwSupplier;

    public UdpTmFrameLink(int scid, String frameType, String host, int port, int frameLength, double framesPerSec,
            IntSupplier clcwSupplier, SdlsSecurityAssociation maybeSdls) {
        this.scid = scid;

        this.frameType = frameType;
        this.host = host;
        this.port = port;

        // If the link should be encrypted, the maximum amount of data is reduced because of encryption overhead.
        if (maybeSdls != null) {
            this.frameSize = frameLength - maybeSdls.getOverheadBytes();
        } else {
            this.frameSize = frameLength;
        }

        this.framesPerSec = framesPerSec;
        this.clcwSupplier = clcwSupplier;

        if ("AOS".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new AosVcSender(scid, i, frameLength, maybeSdls);
            }
            idleFrameBuilder = new AosVcSender(scid, 63, frameLength, maybeSdls);
        } else if ("TM".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new TmVcSender(scid, i, frameLength, true, maybeSdls);
            }
            idleFrameBuilder = builders[0];
        } else if ("USLP".equalsIgnoreCase(frameType)) {
            for (int i = 0; i < NUM_VC; i++) {
                builders[i] = new UslpVcSender(scid, i, frameLength, true, maybeSdls);
            }
            idleFrameBuilder = new UslpVcSender(scid, 63, frameLength, true, maybeSdls);
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
        protected final int scid;
        final int vcId;
        final FrameOffsets offsets;

        protected long vcSeqCount = 0;
        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(100);

        // this is updated as data is written to the frame
        protected int dataOffset;

        byte[] pendingPacket;
        int pendingPacketOffset;
        byte[] data;

        // Optionally, a security association to use for an encrypted link
        SdlsSecurityAssociation maybeSdls = null;

        boolean firstPacketInFrame = true;
        int firstHeaderPointer = -1;

        protected int clcw;

        public VcBuilder(int scid, int vcId, FrameOffsets offsets) {
            this.scid = scid;
            this.vcId = vcId;
            this.offsets = offsets;
            this.dataOffset = offsets.pduDataStart;

            if ((scid < 0) || (scid > 255)) {
                throw new IllegalArgumentException("Invalid spacecraft identifier " + scid);
            }
        }

        public void setCLCW(int clcw) {
            this.clcw = clcw;
        }

        public int emtySpaceLength() {
            return offsets.pduDataEnd - dataOffset;
        }

        abstract void encryptFrame();

        public byte[] getFrame() {
            encodeHeaderAndTrailer();

            // Encrypt the frame if we have a security association
            if (maybeSdls != null) {
                encryptFrame();
            }

            fillChecksum();
            return data;
        }

        public boolean isEmpty() {
            return dataOffset == offsets.pduDataStart();
        }

        public boolean isFull() {
            return dataOffset == offsets.pduDataEnd;
        }

        void reset() {
            vcSeqCount++;
            firstPacketInFrame = true;
            dataOffset = offsets.pduDataStart;

            // If we're running with SDLS, zero the security header and trailer
            if (this.maybeSdls != null) {
                int secHeaderSize = maybeSdls.getHeaderSize();
                Arrays.fill(data, offsets.secHeaderStart, offsets.secHeaderStart + secHeaderSize, (byte) 0);

                int secTrailerSize = maybeSdls.getTrailerSize();
                Arrays.fill(data, offsets.secTrailerStart, offsets.secTrailerStart + secTrailerSize, (byte) 0);
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
            while (dataOffset < offsets.pduDataEnd) {
                pendingPacket = queue.poll();
                if (pendingPacket == null) {
                    break;
                }
                if (firstPacketInFrame) {
                    firstHeaderPointer = dataOffset - offsets.pduDataStart();
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
            int length = Math.min(pendingPacket.length - pendingPacketOffset, offsets.pduDataEnd - dataOffset);
            log.trace("VC{} writing {} bytes from packet of length {} at offset {}",
                    vcId, length, pendingPacket.length, dataOffset);

            System.arraycopy(pendingPacket, pendingPacketOffset, data, dataOffset, length);
            dataOffset += length;
            pendingPacketOffset += length;
            if (pendingPacketOffset == pendingPacket.length) {
                pendingPacket = null;
            }
        }

        private void fillIdlePacket() {
            int n = offsets.pduDataEnd - dataOffset;
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

        // this will be called before encryption
        abstract void encodeHeaderAndTrailer();

        // this will be called after encryption
        abstract void fillChecksum();

        abstract public byte[] getIdleFrame();
    }

    static class AosVcSender extends VcBuilder {
        final byte[] sdlsAuthMask;

        public AosVcSender(int scid, int vcId, int frameSize, SdlsSecurityAssociation maybeLinkSdls) {

            super(scid, vcId, FrameOffsets.aosOffsets(frameSize, true, maybeLinkSdls));
            if ((vcId < 0) || (vcId > 63)) {
                throw new IllegalArgumentException("Invalid virtual channel id " + vcId);
            }

            this.data = new byte[frameSize];

            // Optionally encrypt the data
            byte[] securityHdrAuthMask = new byte[]{};
            if (maybeLinkSdls != null) {
                maybeSdls = maybeLinkSdls;
                securityHdrAuthMask = maybeLinkSdls.securityHdrAuthMask();
            }

            writeGvcId(data, vcId);
            sdlsAuthMask = StandardAuthMask.AOS(true, 0, securityHdrAuthMask);
        }

        void writeGvcId(byte[] frameData, int vcId) {
            ByteArrayUtils.encodeUnsignedShort((1 << 14) + (scid << 6) + vcId, frameData, 0);
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count
            ByteArrayUtils.encodeUnsigned3Bytes((int) vcSeqCount, data, 2);
            data[5] = (byte) (0x60 + ((vcSeqCount >>> 24) & 0xF));

            ByteArrayUtils.encodeInt(clcw, data, data.length - 6);

            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, offsets.frameDataStart);

            // Reed-Solomon the header
            int gvcid = ByteArrayUtils.decodeUnsignedShort(data, 0);
            int x = AosFrameHeaderErrorCorr.encode(gvcid, data[5]);
            ByteArrayUtils.encodeUnsignedShort(x, data, 6);

        }

        @Override
        void fillChecksum() {
            // CRC at the end of the frame
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }

        @Override
        public byte[] getIdleFrame() {
            vcSeqCount++;
            encodeHeaderAndTrailer();
            if (this.maybeSdls != null) {
                encryptFrame();
            }
            fillChecksum();
            return data;
        }

        @Override
        void encryptFrame() {
            try {
                maybeSdls.applySecurity(data, 0, offsets.secHeaderStart,
                        offsets.secTrailerStart + maybeSdls.getTrailerSize(), this.sdlsAuthMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame", e);
            }
        }
    }

    static class TmVcSender extends VcBuilder {
        byte[] idleFrameData;
        final int ocfFlag;
        final byte[] sdlsAuthMask;

        public TmVcSender(int scid, int vcId, int frameSize, boolean ocf, SdlsSecurityAssociation maybeLinkSdls) {
            super(scid, vcId, FrameOffsets.tmOffsets(frameSize, ocf, maybeLinkSdls));
            this.ocfFlag = ocf ? 1 : 0;
            this.data = new byte[frameSize];

            // Optionally encrypt the frame
            byte[] securityHdrAuthMask = new byte[]{};
            if (maybeLinkSdls != null) {
                this.maybeSdls = maybeLinkSdls;
                securityHdrAuthMask = maybeLinkSdls.securityHdrAuthMask();
            }
            writeGvcId(data, vcId);
            sdlsAuthMask = StandardAuthMask.TM(0, securityHdrAuthMask);
        }

        void writeGvcId(byte[] frameData, int vcId) {
            ByteArrayUtils.encodeUnsignedShort((scid << 4) + (vcId << 1) + ocfFlag, frameData, 0);
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count
            data[3] = (byte) (vcSeqCount);

            // write the first header pointer
            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, 4);

            ByteArrayUtils.encodeInt(clcw, data, data.length - 6);
        }

        @Override
        public byte[] getIdleFrame() {
            if (this.maybeSdls != null) {
                try {
                    this.maybeSdls.applySecurity(data, 0, offsets.secHeaderStart,
                            offsets.pduDataEnd + maybeSdls.getTrailerSize(), this.sdlsAuthMask);
                } catch (GeneralSecurityException e) {
                    log.warn("could not encrypt idle frame", e);
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
            try {
                this.maybeSdls.applySecurity(data, 0, offsets.secHeaderStart,
                        offsets.secTrailerStart + maybeSdls.getTrailerSize(), this.sdlsAuthMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame", e);
            }
        }

        @Override
        void fillChecksum() {
            // compute crc
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }
    }

    /**
     * This builds USLP frames with complete primary header, OCF , no insert data, and 32 bits frame count
     *
     */
    static class UslpVcSender extends VcBuilder {
        byte[] idleFrameData;
        final int ocfFlag;
        final byte[] authMask;

        public UslpVcSender(int scid, int vcId, int frameLength, boolean ocf, SdlsSecurityAssociation maybeLinkSdls) {
            super(scid, vcId, FrameOffsets.uslpOffsets(frameLength, ocf, maybeLinkSdls));
            this.data = new byte[frameLength];
            this.ocfFlag = ocf ? 1 : 0;

            ByteArrayUtils.encodeInt((12 << 28) + (scid << 12) + (vcId << 5), data, 0);

            // frame length
            ByteArrayUtils.encodeUnsignedShort(frameLength - 1, data, 4);

            data[6] = 0x0C; // ocfFlag = 1, vc frame count = 100(in binary)

            // Optionally encrypt the data
            byte[] securityHdrAuthMask = new byte[]{};
            if (maybeLinkSdls != null) {
                this.maybeSdls = maybeLinkSdls;
                securityHdrAuthMask = maybeLinkSdls.securityHdrAuthMask();
            }
            authMask = StandardAuthMask.USLP(11, 0, securityHdrAuthMask);
        }

        @Override
        void encodeHeaderAndTrailer() {
            // set the frame sequence count
            ByteArrayUtils.encodeInt((int) vcSeqCount, data, 7);

            // clear the data field header first byte to indicate packet data (in case it was changed by the
            // encryption)
            data[offsets.frameDataStart] = 0;
            // write the first header pointer
            ByteArrayUtils.encodeUnsignedShort(firstHeaderPointer, data, offsets.frameDataStart + 1);

            if (ocfFlag == 1) {
                ByteArrayUtils.encodeInt(clcw, data, data.length - 6);
            }
        }

        @Override
        public byte[] getIdleFrame() {
            vcSeqCount++;
            encodeHeaderAndTrailer();
            if (this.maybeSdls != null) {
                encryptFrame();
            }
            fillChecksum();
            return data;
        }

        @Override
        void encryptFrame() {
            try {
                int secTrailerEnd = offsets.secTrailerStart + maybeSdls.getTrailerSize();
                this.maybeSdls.applySecurity(data, 0, offsets.secHeaderStart, secTrailerEnd, this.authMask);
            } catch (GeneralSecurityException e) {
                log.warn("could not encrypt frame", e);
            }
        }

        @Override
        void fillChecksum() {
            // compute crc
            int x = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(x, data, data.length - 2);
        }
    }

    /**
     * Offsets in the frame:
     * 
     * secHeaderStart: start of the security header if present otherwise -1
     * <p>
     * frameDataStart: start of the frame data. For AOS and USLP this is where the first header pointer (fhp) goes to.
     * For TM, the fhp is part of the primary header
     * <p>
     * pduDataStart: start of the PDU (packet) data. This is where packets are concatenated one after the other (the
     * first and last packets may be incomplete)
     * <p>
     * pduDataEnd: end of the PDU (packet) data
     * 
     */
    record FrameOffsets(int secHeaderStart, int frameDataStart, int pduDataStart, int pduDataEnd, int secTrailerStart) {
        static FrameOffsets aosOffsets(int frameSize, boolean ocf, SdlsSecurityAssociation maybeSdls) {
            // AOS Frame header
            // 2 bytes master channel id
            // 3 bytes virtual channel frame count
            // 1 byte signaling field
            // 2 bytes frame header error control

            // AOS Frame trailer
            // 4 bytes are the OCF
            // 2 bytes CRC
            int secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart;

            if (maybeSdls == null) {
                secHeaderStart = secTrailerStart = -1;
                frameDataStart = 8;
                pduDataEnd = frameSize - 2 - (ocf ? 4 : 0);
            } else {
                secHeaderStart = 8;
                frameDataStart = secHeaderStart + maybeSdls.getHeaderSize();
                secTrailerStart = frameSize - 2 - (ocf ? 4 : 0) - maybeSdls.getTrailerSize();
                pduDataEnd = secTrailerStart;
            }
            pduDataStart = frameDataStart + 2;
            return new FrameOffsets(secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart);
        }

        static FrameOffsets tmOffsets(int frameSize, boolean ocf, SdlsSecurityAssociation maybeSdls) {
            // TM Frame header
            // 2 bits transfer frame version number
            // 10 bits spacecraft id
            // 3 bits virtual channel id
            // 1 bit OCF flag
            // 8 bits master channel frame count
            // 8 bits virtual channel frame count
            // 16 vits transfer frame data field status

            // TM Frame trailer
            // 4 bytes OCF
            // 2 bytes CRC
            int secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart;

            if (maybeSdls == null) {
                secHeaderStart = secTrailerStart = -1;
                frameDataStart = 6;
                pduDataEnd = frameSize - 2 - (ocf ? 4 : 0);
            } else {
                secHeaderStart = 6;
                frameDataStart = secHeaderStart + maybeSdls.getHeaderSize();
                secTrailerStart = frameSize - 2 - (ocf ? 4 : 0) - maybeSdls.getTrailerSize();
                pduDataEnd = secTrailerStart;
            }
            pduDataStart = frameDataStart;
            return new FrameOffsets(secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart);
        }

        static FrameOffsets uslpOffsets(int frameSize, boolean ocf, SdlsSecurityAssociation maybeSdls) {
            // USLP Frame header
            // 11 bytes (assuming a 32 bit sequence count)
            // Transfer frame data field header 3 bytes
            // Transfer frame data zone
            // USLP Frame trailer
            // 4 bytes OCF
            // 2 bytes CRC
            int secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart;

            if (maybeSdls == null) {
                secHeaderStart = secTrailerStart = -1;
                frameDataStart = 11;
                pduDataEnd = frameSize - 2 - (ocf ? 4 : 0);
            } else {
                secHeaderStart = 11;
                frameDataStart = secHeaderStart + maybeSdls.getHeaderSize();
                secTrailerStart = frameSize - 2 - (ocf ? 4 : 0) - maybeSdls.getTrailerSize();
                pduDataEnd = secTrailerStart;
            }
            pduDataStart = frameDataStart + 3;
            return new FrameOffsets(secHeaderStart, frameDataStart, pduDataStart, pduDataEnd, secTrailerStart);
        }
    }
}