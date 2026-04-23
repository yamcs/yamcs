package org.yamcs.tctm.ccsds;

import java.security.GeneralSecurityException;

import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.StandardAuthMask;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.ccsds.UplinkManagedParameters.FrameErrorDetection;
import org.yamcs.tctm.ccsds.UplinkManagedParameters.SdlsInfo;
import org.yamcs.tctm.ccsds.UslpUplinkManagedParameters.UslpUplinkVcManagedParameters;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.error.ProximityCrc32;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Builds and encodes USLP uplink transfer frames as per CCSDS 732.1-B-3.
 * <p>
 * The data zone uses construction rule 0b111 (1-byte zone header), meaning the first packet starts
 * at the beginning of the data zone with no First Header Pointer field.
 */
public class UslpUplinkFrameFactory implements UplinkFrameFactory<UslpUplinkTransferFrame> {
    static final int USLP_VERSION = 12;
    /** Source-or-Destination = 1 for uplink (spacecraft ID is the destination) */
    static final int SOURCE_OR_DEST = 1;
    /**
     * Data zone header byte: construction rule 0b111 (3 MSBs) + protocol ID 0 (5 LSBs).
     * Rule 0b111 means the first packet header starts at the beginning of the data zone.
     */
    static final byte ZONE_HEADER_BYTE = (byte) 0xE0;
    static final int ZONE_HEADER_SIZE = 1;

    final UslpUplinkManagedParameters uslpParams;
    final UslpUplinkVcManagedParameters vcParams;
    final int vcfCountLength;
    /** Size in bytes of the primary header: 4 (fixed ID) + 2 (length) + 1 (flags) + vcfCountLength */
    final int primaryHeaderSize;
    final ErrorDetectionWordCalculator crc;
    final int crcSize;

    public UslpUplinkFrameFactory(UslpUplinkVcManagedParameters vcParams) {
        this.vcParams = vcParams;
        this.uslpParams = vcParams.uslpParams;
        this.vcfCountLength = vcParams.vcfCountLength;
        this.primaryHeaderSize = 7 + vcfCountLength;

        FrameErrorDetection err = uslpParams.errorDetection;
        if (err == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();
            crcSize = 2;
        } else if (err == FrameErrorDetection.CRC32) {
            crc = new ProximityCrc32();
            crcSize = 4;
        } else {
            crc = null;
            crcSize = 0;
        }
    }

    @Override
    public UslpUplinkTransferFrame makeCtrlFrame(int dataLength) {
        return makeFrame(dataLength, 0, true, TimeEncoding.getWallclockTime());
    }

    @Override
    public UslpUplinkTransferFrame makeDataFrame(int dataLength, long generationTime) {
        return makeFrame(dataLength, vcParams.mapId, false, generationTime);
    }

    @Override
    public UslpUplinkTransferFrame makeDataFrame(int dataLength, long generationTime, byte mapId) {
        int effectiveMapId = (mapId >= 0) ? mapId : vcParams.mapId;
        return makeFrame(dataLength, effectiveMapId, false, generationTime);
    }

    private UslpUplinkTransferFrame makeFrame(int dataLength, int mapId, boolean cmdControl, long generationTime) {
        mapId = mapId & 0x0F;
        int dataStart = primaryHeaderSize + uslpParams.insertZoneLength + ZONE_HEADER_SIZE;
        int length = dataStart + dataLength + crcSize;

        short spi = vcParams.encryptionSpi;
        SdlsInfo sdlsInfo = uslpParams.sdlsSecurityAssociations.get(spi);
        boolean isEncrypted = !cmdControl && sdlsInfo != null;
        if (isEncrypted) {
            length += sdlsInfo.sa().getOverheadBytes();
        }

        if (length > uslpParams.getMaxFrameLength()) {
            throw new IllegalArgumentException("Resulting frame length " + length + " exceeds maxFrameLength "
                    + uslpParams.getMaxFrameLength());
        }

        byte[] data = new byte[length];
        UslpUplinkTransferFrame frame = new UslpUplinkTransferFrame(data, uslpParams.spacecraftId,
                vcParams.vcId, mapId, cmdControl);
        frame.genTime = generationTime;
        frame.setDataStart(dataStart);
        frame.setDataEnd(dataStart + dataLength);

        if (isEncrypted) {
            frame.setDataStart(frame.getDataStart() + sdlsInfo.sa().getHeaderSize());
            frame.setDataEnd(frame.getDataEnd() + sdlsInfo.sa().getHeaderSize());
        }

        return frame;
    }

    @Override
    public int getFramingLength(int vcId) {
        int length = primaryHeaderSize + uslpParams.insertZoneLength + ZONE_HEADER_SIZE + crcSize;
        short spi = vcParams.encryptionSpi;
        SdlsInfo sdlsInfo = uslpParams.sdlsSecurityAssociations.get(spi);
        if (sdlsInfo != null) {
            length += sdlsInfo.sa().getOverheadBytes();
        }
        return length;
    }

    @Override
    public byte[] encodeFrame(UslpUplinkTransferFrame frame) {
        byte[] data = frame.getData();

        // First 4 bytes: version(4) | scId(16) | src/dest(1) | vcId(6) | mapId(4) | end-of-header(1)
        int f4b = (USLP_VERSION << 28)
                | (frame.spacecraftId << 12)
                | (SOURCE_OR_DEST << 11)
                | (frame.virtualChannelId << 5)
                | (frame.getMapId() << 1)
                | 0; // end-of-header = 0 (normal, non-truncated frame)
        ByteArrayUtils.encodeInt(f4b, data, 0);

        // Bytes 4-5: frame length = total length - 1
        ByteArrayUtils.encodeUnsignedShort(data.length - 1, data, 4);

        // Byte 6: bypass(1) | cmdCtrl(1) | spare(2) | ocf(1) | vcfCountLength(3)
        data[6] = (byte) ((frame.bypassFlag() << 7) | (frame.cmdControlFlag() << 6) | vcfCountLength);

        // Bytes 7..7+vcfCountLength-1: VC Frame Count (big-endian)
        int vcfSeq = frame.getVcFrameSeq();
        for (int i = vcfCountLength - 1; i >= 0; i--) {
            data[7 + i] = (byte) (vcfSeq & 0xFF);
            vcfSeq >>= 8;
        }

        // Insert zone is left as zero (Java initialises byte arrays to 0)

        // Data zone header
        data[primaryHeaderSize + uslpParams.insertZoneLength] = ZONE_HEADER_BYTE;

        // SDLS encryption
        short spi = vcParams.encryptionSpi;
        SdlsInfo sdlsInfo = uslpParams.sdlsSecurityAssociations.get(spi);
        if (sdlsInfo != null && !frame.isCmdControl()) {
            try {
                SdlsSecurityAssociation sa = sdlsInfo.sa();
                byte[] authMask = sdlsInfo.customAuthMask();
                if (authMask == null) {
                    authMask = StandardAuthMask.USLP(primaryHeaderSize, uslpParams.insertZoneLength,
                            sa.securityHdrAuthMask());
                }
                sa.applySecurity(data, 0, frame.getDataStart() - sa.getHeaderSize(),
                        frame.getDataEnd() + sa.getTrailerSize(), authMask);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        // CRC
        if (crcSize == 2) {
            int c = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(c, data, data.length - 2);
        } else if (crcSize == 4) {
            int c = crc.compute(data, 0, data.length - 4);
            ByteArrayUtils.encodeInt(c, data, data.length - 4);
        }

        return data;
    }
}
