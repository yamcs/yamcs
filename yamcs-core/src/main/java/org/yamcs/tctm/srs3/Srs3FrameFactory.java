package org.yamcs.tctm.srs3;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.security.encryption.SymmetricEncryption;
import org.yamcs.tctm.ErrorDetectionWordCalculator;


public class Srs3FrameFactory {
    ErrorDetectionWordCalculator crc;
    SymmetricEncryption se;

    Srs3ManagedParameters srs3Mp;
    
    public Srs3FrameFactory(Srs3ManagedParameters srs3Mp) {
        this.srs3Mp = srs3Mp;

        crc = srs3Mp.getErrorDetection();
        se = srs3Mp.getEncryption();
    }

    public Srs3ManagedParameters getSrs3ManagedParameters() {
        return srs3Mp;
    }

    /**
     * Makes a new SRS3 frame of the given length with the generation time set to the current wall clock time
     * 
     * @param dataLength
     * @return
     */
    public byte[] makeFrame(int dataLength) {
        int length = dataLength + getInnerFramingLength();

        // Enforce maxLength of the SRS3 Frame
        if (srs3Mp.enforceFrameLength)
            length = srs3Mp.getMaxFrameLength();

        if (length > srs3Mp.getMaxFrameLength())
            throw new IllegalArgumentException("Resulting frame length " + length + " is more than the maximum allowed "
                    + srs3Mp.getMaxFrameLength());

        length += getOuterFramingLength();

        byte[] data = new byte[length];
        return data;
    }

    public int getPaddingLength(int datalength) {
        if (!srs3Mp.enforceFrameLength)
            return 0;
        
        return srs3Mp.maxFrameLength - (getCspHeaderLength() + datalength);

    }

    public int getInnerFramingLength() {
        return getCspHeaderLength();
    }

    /**
     * retrieves the headers size + CRC size
     * 
     * @return
     */
    public int getOuterFramingLength() {
        int length = getRadioHeaderLength() + getSpacecraftIdLength();
        if (crc != null) {
            length += crc.sizeInBits() / 8;
        }

        if (se != null) {
            length += getTagLength() + getIVLength();
        }

        return length;
    }

    public int getSpacecraftIdLength() {
        if (srs3Mp.getSpacecraftId() != null)
            return srs3Mp.getSpacecraftId().length;
        
        return 0;
    }

    public int getCspHeaderLength() {
        if (srs3Mp.getCspHeader() != null)
            return srs3Mp.getCspHeader().length;

        return 0;
    }

    public int getRadioHeaderLength() {
        return srs3Mp.getRadioHeaderLength();
    }

    public byte[] encodeFrame(byte[] cspFrame, AtomicInteger dataStart, AtomicInteger dataEnd, int tcFrameLength) {
        int rh = srs3Mp.getRadioHeaderLength();
        if (rh != 0) {
            System.arraycopy(ByteArrayUtils.encodeCustomInteger(tcFrameLength, rh), 0, cspFrame, dataStart.get(), rh);
        }

        if (srs3Mp.getSpacecraftId() != null) {
            byte[] spacecraftId = srs3Mp.getSpacecraftId();
            System.arraycopy(spacecraftId, 0, cspFrame, dataStart.get() + getRadioHeaderLength(), spacecraftId.length);
        }

        if (srs3Mp.getCspHeader() != null) {
            byte[] cspHeader = srs3Mp.getCspHeader();
            System.arraycopy(cspHeader, 0, cspFrame, dataStart.get() + getRadioHeaderLength() + getSpacecraftIdLength(), cspHeader.length);
        }

        if (se != null) {
            try {
                byte[] iv = se.encrypt(Arrays.copyOfRange(cspFrame, dataStart.get(), dataEnd.get()));

                // Update the dataStart and dataEnd, accounting for the data that must undergo CRC
                dataStart.set(dataStart.get() - se.getIVLength());
                dataEnd.set(dataEnd.get() + se.getTagLength());
                System.arraycopy(iv, 0, cspFrame, dataStart.get(), iv.length);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (crc != null) {
            int c = crc.compute(cspFrame, dataStart.get(), dataEnd.get());
            int crcSize = crc.sizeInBits() / 8;

            if (crcSize == 4)
                ByteArrayUtils.encodeInt(c, cspFrame, dataEnd.get());
            
            if (crcSize == 2)
                ByteArrayUtils.encodeUnsignedShort(c, cspFrame, dataEnd.get());

            // Update dataEnd
            dataEnd.set(dataEnd.get() + crcSize);
        }

        return cspFrame;
    }

    public int getTagLength() {
        if (se != null)
            return se.getTagLength();

        return 0;
    }

    public int getIVLength() {
        if (se != null)
            return se.getIVLength();

        return 0;
    }
}
