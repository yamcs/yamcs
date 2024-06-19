package org.yamcs.tctm.ccsds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.DownlinkManagedParameters.FrameErrorDetection;
import org.yamcs.tctm.ccsds.TmManagedParameters.ServiceType;
import org.yamcs.tctm.ccsds.TmManagedParameters.TmVcManagedParameters;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes frames as per CCSDS 132.0-B-3
 *
 */
public class TmFrameDecoder implements TransferFrameDecoder {
    TmManagedParameters tmParams;
    CrcCciitCalculator crc;
    static Logger log = LoggerFactory.getLogger(TmFrameDecoder.class.getName());

    public TmFrameDecoder(TmManagedParameters tmParams) {
        this.tmParams = tmParams;
        if (tmParams.errorDetection == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();
        }
    }

    @Override
    public TmTransferFrame decode(byte[] data, int offset, int length) throws TcTmException {
        if (log.isTraceEnabled()) {
            log.trace("decoding frame buf length: {}, dataOffset: {} , dataLength: {}", data.length, offset, length);
        }

        int version = (data[offset] & 0xFF) >> 6;
        if (version != 0) {
            throw new TcTmException("Bad frame version number " + version + "; expected 0 (TM)");
        }

        if (length != tmParams.frameLength) {
            throw new TcTmException("Bad frame length " + length + "; expected " + tmParams.frameLength);
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
        gvcid = ByteArrayUtils.decodeShort(data, offset) >> 1;

        int vn = gvcid >> 13;
        if (vn != 0) {
            throw new TcTmException("Invalid TM frame version number " + vn + "; expected " + 0);
        }
        int spacecraftId = gvcid >> 3;
        int virtualChannelId = gvcid & 0x7;

        TmVcManagedParameters vmp = tmParams.vcParams.get(virtualChannelId);
        if (vmp == null) {
            throw new TcTmException("Received data for unknown VirtualChannel " + virtualChannelId);
        }

        TmTransferFrame ttf = new TmTransferFrame(data, spacecraftId, virtualChannelId);
        ttf.setVcFrameSeq(data[3] & 0xFF);

        boolean ocfPresent = (data[offset + 1] & 1) == 1;
        if (ocfPresent) {
            dataEnd -= 4;
            ttf.setOcf(ByteArrayUtils.decodeInt(data, dataEnd));
        }

        int tfdfs = ByteArrayUtils.decodeShort(data, offset + 4);
        boolean secHeaderPresent = (tfdfs & 0x8000) == 0x8000;
        boolean syncFlag = (tfdfs & 0x4000) == 0x4000;

        if (secHeaderPresent) {
            int secHeaderLength = 1 + data[dataOffset] & 0x3F;
            ttf.setShStart(dataOffset);
            ttf.setShLength(secHeaderLength);
            dataOffset += secHeaderLength;
        }
        if (vmp.service == ServiceType.PACKET) {
            if (syncFlag) {
                throw new TcTmException("VC " + virtualChannelId + " "
                        + "Wrong syncFlag 1 for service type PACKET (expected 0) for VC (expected 0)"
                        + virtualChannelId);
            }
            int fhp = tfdfs & 0x7FF;
            if (fhp == 0x7FF) {
                fhp = -1;
            } else if (fhp == 0x7FE) {
                ttf.setIdle(true);
                fhp = -1;
            } else {
                fhp += dataOffset;
                if (fhp > dataEnd) {
                    throw new TcTmException("First header pointer in the PACKET part of TM frame is outside the data "
                            + (fhp - dataOffset) + ">" + (dataEnd - dataOffset));
                }
            }
            ttf.setFirstHeaderPointer(fhp);
        }

        ttf.setDataStart(dataOffset);
        ttf.setDataEnd(dataEnd);
        return ttf;
    }
}
