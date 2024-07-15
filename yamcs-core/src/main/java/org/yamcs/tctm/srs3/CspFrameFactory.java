package org.yamcs.tctm.srs3;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.security.encryption.SymmetricEncryption;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.csp.AbstractCspTcFrameLink;
import org.yamcs.tctm.csp.AbstractCspTcFrameLink.CspManagedParameters;


public class CspFrameFactory {
    ErrorDetectionWordCalculator crc;
    SymmetricEncryption se;

    CspManagedParameters cspManagedParameters;
    
    public CspFrameFactory(CspManagedParameters cspManagedParameters) {
        this.cspManagedParameters = cspManagedParameters;

        crc = cspManagedParameters.getErrorDetection();
        se = cspManagedParameters.getEncryption();
    }

    public CspManagedParameters getCspManagedParameters() {
        return cspManagedParameters;
    }

    /**
     * Makes a new CSP frame of the given length with the generation time set to the current wall clock time
     * 
     * @param dataLength
     * @return
     */
    public byte[] makeFrame(int dataLength) {
        int length = dataLength + getFramingLength();

        if (length > cspManagedParameters.getMaxFrameLength()) {
            throw new IllegalArgumentException("Resulting frame length " + length + " is more than the maximum allowed "
                    + cspManagedParameters.getMaxFrameLength());
        }

        // Enforce maxLength of the CSP Frame
        if (cspManagedParameters.enforceFrameLength)
            length = cspManagedParameters.getMaxFrameLength();

        byte[] data = new byte[length];
        return data;
    }

    public int getPaddingAndDataLength() {
        int length = cspManagedParameters.getMaxFrameLength() - getFramingLength();

        if (cspManagedParameters.enforceFrameLength){
            if (crc == null) {
                length -= cspManagedParameters.getDefaultCrcSize();
            }
        }

        return length;
    }

    /**
     * retrieves the headers size + CRC size
     * 
     * @return
     */
    public int getFramingLength() {
        int length = getCspHeaderLength() + getRadioHeaderLength();
        if (crc != null) {
            length += crc.sizeInBits() / 8;
        }

        if (se != null) {
            length += getTagLength() + getIVLength();
        }

        return length;
    }

    public int getCspHeaderLength() {
        return cspManagedParameters.getCspHeader().length;
    }

    public int getRadioHeaderLength() {
        if (cspManagedParameters.getRadioHeader() != null)
            return cspManagedParameters.getRadioHeader().length;
        
        return 0;
    }

    public byte[] encodeFrame(byte[] cspFrame, AtomicInteger dataStart, AtomicInteger dataEnd) {
        byte[] cspHeader = cspManagedParameters.getCspHeader();

        if (cspManagedParameters.getRadioHeader() != null) {
            byte[] radioHeader = cspManagedParameters.getRadioHeader();
            System.arraycopy(radioHeader, 0, cspFrame, dataStart.get(), radioHeader.length);
        }

        System.arraycopy(cspHeader, 0, cspFrame, dataStart.get() + getRadioHeaderLength(), cspHeader.length);

        if (se != null) {
            try {
                byte[] ivMessage = se.encrypt(Arrays.copyOfRange(cspFrame, dataStart.get(), dataEnd.get()));

                // Update the dataStart and dataEnd, accounting for the data that must undergo CRC
                dataStart.set(dataStart.get() - se.getIVLength());
                dataEnd.set(dataEnd.get() + se.getTagLength());
                System.arraycopy(ivMessage, 0, cspFrame, dataStart.get(), ivMessage.length);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (crc != null) {
            int c = crc.compute(cspFrame, dataStart.get(), dataEnd.get());

            if (crc.sizeInBits() / 8 == 4)
                ByteArrayUtils.encodeInt(c, cspFrame, dataEnd.get());
            
            if (crc.sizeInBits() / 8 == 2)
                ByteArrayUtils.encodeUnsignedShort(c, cspFrame, dataEnd.get());

            // Update dataEnd
            dataEnd.set(dataEnd.get() + crc.sizeInBits() / 8);
        }

        return cspFrame;
    }

    public int getTagLength() {
        if (se != null) {
            return se.getTagLength();
        }

        return 0;
    }

    public int getIVLength() {
        if (se != null) {
            return se.getIVLength();
        }

        return 0;
    }
}
