package org.yamcs.tctm.ccsds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.StandardAuthMask;
import org.yamcs.security.sdls.SdlsSecurityAssociation.VerificationStatusCode;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.FrameErrorDetection;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.SdlsInfo;
import org.yamcs.tctm.ccsds.UslpManagedParameters.ServiceType;
import org.yamcs.tctm.ccsds.UslpManagedParameters.UslpVcManagedParameters;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.error.ProximityCrc32;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes frames as per CCSDS 732.1-B-1
 *
 */
public class UslpFrameDecoder implements TransferFrameDecoder {
    final UslpManagedParameters uslpParams;
    ErrorDetectionWordCalculator crc;
    static final Logger log = LoggerFactory.getLogger(TransferFrameDecoder.class.getName());

    // Auth mask is not final - can change if the header size changes
    byte[] sdlsAuthMask;
    // This tracks the previously recorded header length. If it changes, the sdlsAuthMask has to change too
    int previousPhLength;

    public UslpFrameDecoder(UslpManagedParameters uslpParams) {
        this.uslpParams = uslpParams;
        if (uslpParams.errorDetection == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();
        } else if (uslpParams.errorDetection == FrameErrorDetection.CRC32) {
            crc = new ProximityCrc32();
        }
    }

    @Override
    public DownlinkTransferFrame decode(byte[] data, int offset, int length) throws TcTmException {
        log.trace("decoding frame buf length: {}, dataOffset: {} , dataLength: {}", data.length, offset, length);

        int version = (data[offset] & 0xFF) >> 4;
        if (version != 12) {
            throw new TcTmException("Bad frame version number " + version + "; expected 12 (USLP)");
        }

        if (uslpParams.frameLength != -1) {
            if (length != uslpParams.frameLength) {
                throw new TcTmException("Bad frame length " + length + "; expected fixed length " + uslpParams.frameLength);
            }
        } else {
            if (length < uslpParams.minFrameLength || length > uslpParams.maxFrameLength) {
                throw new TcTmException("Bad frame length " + length + "; expected length between" + uslpParams.minFrameLength
                        + " and " + uslpParams.maxFrameLength);
            }
        }

        int dataEnd = offset + length;

        if (uslpParams.errorDetection == FrameErrorDetection.CRC16) {
            dataEnd -= 2;
            int c1 = crc.compute(data, offset, dataEnd - offset);
            int c2 = ByteArrayUtils.decodeUnsignedShort(data, dataEnd);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + c1 + " in the frame: " + c2);
            }
        } else if (uslpParams.errorDetection == FrameErrorDetection.CRC32) {
            dataEnd -= 4;
            int c1 = crc.compute(data, offset, dataEnd - offset);
            int c2 = ByteArrayUtils.decodeInt(data, dataEnd);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + Integer.toUnsignedString(c1)
                        + " in the frame: " + Integer.toUnsignedString(c2));
            }
        }

        int f4b = ByteArrayUtils.decodeInt(data, offset);// first four bytes

        int dataOffset = offset + 4;

        int vn = f4b >>> 28;
        if (vn != 12) {
            throw new TcTmException("Invalid USLP frame version number " + vn + "; expected " + 12);
        }
        int spacecraftId = (f4b >>> 12) & 0xFFFF;
        int virtualChannelId = (f4b >> 5) & 0x3F;
        int mapId = (f4b >> 1) & 0xF;
        boolean truncatedFrame = (f4b & 1) == 1;

        UslpTransferFrame utf = new UslpTransferFrame(data, spacecraftId, virtualChannelId);

        UslpVcManagedParameters vmp = uslpParams.vcParams.get(virtualChannelId);
        if (vmp == null) {
            if (virtualChannelId == 63) {
                utf.setServiceType(ServiceType.IDLE);
                return utf;
            } else {
                throw new TcTmException("Received data for unknown VirtualChannel " + virtualChannelId);
            }
        }

        long vcfFrameSeq;
        int vcfCountLength = 0;
        if (truncatedFrame) {
            if (length != vmp.truncatedTransferFrameLength) {
                throw new TcTmException("Received truncated frame on VC " + virtualChannelId + " whose length ("
                        + length + ") does not match the configured truncatedTranferFrameLength("
                        + vmp.truncatedTransferFrameLength + ")");
            }
            vcfFrameSeq = -1;
        } else {

            int encodedFrameLength = ByteArrayUtils.decodeShort(data, dataOffset);
            if (encodedFrameLength != length - 1) {
                throw new TcTmException(
                        "Encoded frame length does not match received data length: " + encodedFrameLength
                                + " != (" + length + "-1)");
            }
            dataOffset += 2;

            byte b6 = data[dataOffset];
            // bit 48 Bypass/Sequence Control Flag - don't care for TM
            // bit 49 Protocol Control Command Flag - don't care for TM
            // bit 52 OCF flag
            boolean ocfPresent = ((b6 >> 3) & 1) == 1;
            if (ocfPresent) {
                dataEnd -= 4;
                utf.setOcf(ByteArrayUtils.decodeInt(data, dataEnd));
            }
            // bit2 53-55 - the length of the VCF count field
            vcfCountLength = b6 & 0x7;

            dataOffset += 1;

            if (vcfCountLength == 0) {
                vcfFrameSeq = -1;
            } else {
                vcfFrameSeq = 0;
                for (int i = 0; i < vcfCountLength; i++) {
                    vcfFrameSeq = (vcfFrameSeq << 8) + (data[dataOffset++] & 0xFF);
                }
            }

            dataOffset += uslpParams.insertZoneLength;
        }

        utf.setVcFrameSeq(vcfFrameSeq);
        utf.setMapId(mapId);

        if (vmp.encryptionSpis.length > 0) {
            short receivedSpi = ByteArrayUtils.decodeShort(data, dataOffset);
            SdlsInfo sdlsInfo = uslpParams.sdlsSecurityAssociations.get(receivedSpi);
            if (sdlsInfo == null) {
                throw new TcTmException("Received USLP frame with unknown SPI " + receivedSpi);
            }

            // Calculate the current primary header length
            int phLength;
            if (truncatedFrame) {
                phLength = 4;
            } else {
                phLength = vcfCountLength + 7;
            }

            SdlsSecurityAssociation sa = sdlsInfo.sa();
            // If necessary, update the SDLS auth mask.
            // This will initialize the variables on first run.
            if (phLength != previousPhLength) {
                sdlsAuthMask = StandardAuthMask.USLP(phLength, uslpParams.insertZoneLength, sa.securityHdrAuthMask());
                previousPhLength = phLength;
            }

            int secHeaderStart = dataOffset;

            int secTrailerEnd = dataEnd;
            // Use a custom auth mask if present, or the default mask
            byte[] authMask = sdlsInfo.customAuthMask();
            if (authMask == null)
                authMask = this.sdlsAuthMask;
            // try to decrypt the frame
            VerificationStatusCode decryptionStatus = sa.processSecurity(data, offset,
                    secHeaderStart, secTrailerEnd, authMask);

            if (decryptionStatus != VerificationStatusCode.NoFailure) {
                throw new TcTmException(
                        "Failed to decrypt USLP frame for SPI " + receivedSpi + ": " + decryptionStatus);
            }

            // Update the offsets
            dataOffset += sa.getHeaderSize();
            dataEnd -= sa.getTrailerSize();
        }

        byte dataHeader = data[dataOffset];

        int constrRules = (dataHeader & 0xFF) >> 5;
        int protId = dataHeader & 0x1F;
        if (vmp.service == ServiceType.PACKET) {
            if (protId != 0) {
                throw new TcTmException("Invalid Protocol Id " + protId + " Expected 0 for packet data.");
            }

            if (constrRules == 0b000) {
                // This construction rule logic is also valid for 0b010 and 0b001 (fixed size)
                int fhp = ByteArrayUtils.decodeShort(data, dataOffset + 1);
                dataOffset += 3;

                if (fhp == 0xFFFF) {
                    fhp = -1;
                } else {
                    fhp += dataOffset;
                    if (fhp > dataEnd) {
                        throw new TcTmException(
                                "First header pointer in the date header part of USLP frame is outside the data "
                                        + (fhp - dataOffset) + ">" + (dataEnd - dataOffset));
                    }
                }

                utf.setFirstHeaderPointer(fhp);
            } else if (constrRules == 0b111) {
                // This construction rule logic is also valid for other TFDZ construction rules
                // with variable length.
                dataOffset += 1;
                utf.setFirstHeaderPointer(dataOffset);
            } else {
                throw new TcTmException(
                        "Invalid TFDZ Construction Rule Value " + constrRules + " Expected 0 for packet data.");
            }
        }


        utf.setDataStart(dataOffset);
        utf.setDataEnd(dataEnd);
        return utf;
    }

}