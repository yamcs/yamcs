package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.ccsds.TcManagedParameters.TcVcManagedParameters;
import org.yamcs.tctm.ccsds.UplinkManagedParameters.FrameErrorDetection;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

public class TcFrameFactory {
    final private TcManagedParameters tcParams;

    final CrcCciitCalculator crc;

    public TcFrameFactory(TcVcManagedParameters vcParams) {
        this.tcParams = vcParams.tcParams;
        FrameErrorDetection err = vcParams.getErrorDetection();
        if (err == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();
        } else {
            crc = null;
        }
    }

    /**
     * Makes a new frame of the given length with the generation time set to the current wall clock time
     * 
     * @param vcId
     * @param dataLength
     * @return
     */
    public TcTransferFrame makeFrame(int vcId, int dataLength) {
        return makeFrame(vcId, dataLength, TimeEncoding.getWallclockTime());
    }

    public TcTransferFrame makeFrame(int vcId, int dataLength, long generationTime) {
        if (vcId > 63 || vcId < 0) {
            throw new IllegalArgumentException("Invalid vcId");
        }

        int length = dataLength + 5;
        if (crc != null) {
            length += 2;
        }

        if (length > tcParams.getMaxFrameLength()) {
            throw new IllegalArgumentException("Resulting frame length " + length + " is more than the maximum allowed "
                    + tcParams.getMaxFrameLength());
        }
        byte[] data = new byte[length];

        TcTransferFrame ttf = new TcTransferFrame(data, tcParams.spacecraftId, vcId);
        ttf.setDataStart(5);
        ttf.setDataEnd(5 + dataLength);

        return ttf;
    }

    /**
     * retrieves the headers size + CRC size
     * 
     * @return
     */
    public int getFramingLength(int vcId) {
        int length = 5;
        if (crc != null) {
            length += 2;
        }
        return length;
    }

    public TcTransferFrame makeBCFrame(int vcId) {
        TcTransferFrame ttf = makeFrame(vcId, 3);
        ttf.setBypass(true);

        return ttf;
    }

    public byte[] encodeFrame(TcTransferFrame ttf) {

        byte[] data = ttf.getData();
        int w0 = tcParams.spacecraftId;
        if (ttf.isBypass()) {
            w0 += (1 << 13);
        }
        if (ttf.isCmdControl()) {
            w0 += (1 << 12);
        }
        ByteArrayUtils.encodeUnsignedShort(w0, data, 0);
        int w1 = (ttf.getVirtualChannelId() << 10) + (data.length - 1);
        ByteArrayUtils.encodeUnsignedShort(w1, data, 2);
        data[4] = (byte) ttf.getVcFrameSeq();

        if (crc != null) {
            int c = crc.compute(data, 0, data.length - 2);
            ByteArrayUtils.encodeUnsignedShort(c, data, data.length - 2);
        }
        return data;
    }
}
