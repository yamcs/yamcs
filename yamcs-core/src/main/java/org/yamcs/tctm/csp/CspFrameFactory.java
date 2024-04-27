package org.yamcs.tctm.csp;

import org.yamcs.tctm.csp.AbstractCspTcFrameLink.CspManagedParameters.FrameErrorDetection;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.csp.AbstractCspTcFrameLink.CspManagedParameters;


public class CspFrameFactory {
    final CrcCciitCalculator crc;

    CspManagedParameters cspManagedParameters;
    
    public CspFrameFactory(CspManagedParameters cspManagedParameters) {
        this.cspManagedParameters = cspManagedParameters;

        FrameErrorDetection err = cspManagedParameters.getErrorDetection();
        if (err == FrameErrorDetection.CRC16) {
            crc = new CrcCciitCalculator();

        } else {
            crc = null;
        }
    }

    /**
     * Makes a new CSP frame of the given length with the generation time set to the current wall clock time
     * 
     * @param dataLength
     * @return
     */
    public byte[] makeFrame(int dataLength) {
        int length = dataLength + getCspHeaderLength();
        if (crc != null) {
            length += 2;
        }

        if (length > cspManagedParameters.getMaxFrameLength()) {
            throw new IllegalArgumentException("Resulting frame length " + length + " is more than the maximum allowed "
                    + cspManagedParameters.getMaxFrameLength());
        }
        byte[] data = new byte[length];
        return data;
    }

    /**
     * retrieves the headers size + CRC size
     * 
     * @return
     */
    public int getFramingLength() {
        int length = getCspHeaderLength();
        if (crc != null) {
            length += 2;
        }

        return length;
    }

    public int getCspHeaderLength() {
        return cspManagedParameters.getCspHeader().length;
    }

    public byte[] encodeFrame(byte[] cspFrame) {
        if (crc != null) {
            int c = crc.compute(cspFrame, 0, cspFrame.length - 2);
            ByteArrayUtils.encodeUnsignedShort(c, cspFrame, cspFrame.length - 2);
        }

        byte[] cspHeader = cspManagedParameters.getCspHeader();
        System.arraycopy(cspHeader, 0, cspFrame, 0, cspHeader.length);

        return cspFrame;
    }
}
