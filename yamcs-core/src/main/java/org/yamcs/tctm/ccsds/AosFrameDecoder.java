package org.yamcs.tctm.ccsds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.rs.ReedSolomonException;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.AosManagedParameters.ServiceType;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.FrameErrorDetection;
import org.yamcs.tctm.ccsds.AosManagedParameters.AosVcManagedParameters;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr.DecoderResult;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes frames as per CCSDS 732.0-B-3
 * 
 * @author nm
 *
 */
public class AosFrameDecoder implements TransferFrameDecoder {
    AosManagedParameters aosParams;
    CrcCciitCalculator crc;
    static Logger log = LoggerFactory.getLogger(AosFrameDecoder.class.getName());

    public AosFrameDecoder(AosManagedParameters aosParams) {
        this.aosParams = aosParams;
        if (aosParams.errorDetection == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();
        }
    }

    @Override
    public AosTransferFrame decode(byte[] data, int offset, int length) throws TcTmException {
        log.trace("decoding frame buf length: {}, dataOffset: {} , dataLength: {}", data.length, offset, length);

        int version = (data[offset] & 0xFF) >> 6;
        if(version != 1) {
            throw new TcTmException("Bad frame version number " + version + "; expected 1 (AOS)");
        }
        
        if (length != aosParams.frameLength) {
            throw new TcTmException("Bad frame length " + length + "; expected " + aosParams.frameLength);
        }
        int dataEnd = offset + length;
        

        if (crc != null) {
            dataEnd -= 2;
            int c1 = crc.compute(data, offset, dataEnd - offset);
            int c2 = ByteArrayUtils.decodeUnsignedShort(data, dataEnd);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + c1 + " in the frame: " + c2);
            }
        }
        int gvcid;
        int dataOffset = offset + 6;

        if (aosParams.frameHeaderErrorControlPresent) {
            try {
                DecoderResult dr = AosFrameHeaderErrorCorr.decode(ByteArrayUtils.decodeUnsignedShort(data, offset),
                        data[offset + 5], ByteArrayUtils.decodeUnsignedShort(data, offset + 6));
                gvcid = dr.gvcid;
            } catch (ReedSolomonException e) {
                throw new CorruptedFrameException("Failed to Reed-Solomon verify/correct the AOS frame header fields");
            }
            dataOffset += 2;
        } else {
            gvcid = ByteArrayUtils.decodeUnsignedShort(data, offset);
        }

        int vn = gvcid >> 14;
        if (vn != 1) {
            throw new TcTmException("Invalid AOS frame version number " + vn + "; expected " + 1);
        }
        int spacecraftId = (gvcid >> 6)&0xFF;
        int virtualChannelId = gvcid & 0x3F;

        AosTransferFrame atf = new AosTransferFrame(data, spacecraftId, virtualChannelId);

        AosVcManagedParameters vmp = aosParams.vcParams.get(virtualChannelId);
        if (vmp == null) {
            if (virtualChannelId == 63) {
                atf.setServiceType(ServiceType.IDLE);
                return atf;
            }
            throw new TcTmException("Received data for unknown VirtualChannel " + virtualChannelId);
        }

        dataOffset += aosParams.insertZoneLength;

        atf.setVcFrameSeq(ByteArrayUtils.decodeUnsigned3Bytes(data, offset + 2));

        if (vmp.ocfPresent) {
            dataEnd -= 4;
            atf.setOcf(ByteArrayUtils.decodeInt(data, dataEnd));
        }

        if (vmp.service == ServiceType.PACKET) {
            int fhp = ByteArrayUtils.decodeUnsignedShort(data, dataOffset) & 0x7FF;
            dataOffset += 2;
            if (fhp == 0x7FF) {
                fhp = -1;
            } else {
                fhp += dataOffset;
                if (fhp > dataEnd) {
                    throw new TcTmException("First header pointer in the M_PDU part of AOS frame is outside the data "
                            + (fhp - dataOffset) + ">" + (dataEnd - dataOffset));
                }
            }
            atf.setFirstHeaderPointer(fhp);
        }

        atf.setDataStart(dataOffset);
        atf.setDataEnd(dataEnd);
        return atf;
    }

}
