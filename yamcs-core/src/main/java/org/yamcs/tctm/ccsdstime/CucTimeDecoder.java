package org.yamcs.tctm.ccsdstime;

import org.yamcs.utils.ByteSupplier;

/**
 * Decoder for CCSDS Unsegmented time Code as specified in 
 * TIME CODE FORMATS, CCSDS 301.0-B-4, Nov 2010 
 * 
 * @author nm
 *
 */
public class CucTimeDecoder implements CcsdsTimeDecoder {
    
    final int implicitPField;

    /**
     * Constructor for decoder.
     * 
     * @param implicitPField
     *            -1 means is part of the packet, other values means it is pre-defined (implicit)
     */
    public CucTimeDecoder(int implicitPField) {
        this.implicitPField = implicitPField;
    }

    public long decode(byte[] packet, int offset) {
        return decode(new ByteSupplier() {
            int o = offset;
            @Override
            public byte getAsByte() {
                return packet[o++];
            }
        });
    }
    
    @Override
    public long decode(ByteSupplier s) {
        int pfield;
        if (implicitPField != -1) {
            pfield = implicitPField;
        } else {
            pfield = 0xFF & s.getAsByte();
        }
        int btBytes = 1 + ((pfield >> 2) & 3);
        int ftBytes = pfield & 3;

        if ((pfield >> 7) == 1) { // extended pfield
            int extPfield = 0xFF & s.getAsByte();
            btBytes += ((extPfield >> 6) & 3);
            ftBytes += ((extPfield >> 2) & 7);
        }
        long coarseTime = 0;
        while (btBytes > 0) {
            coarseTime = (coarseTime << 8) + (0xFF & s.getAsByte());
            btBytes--;
        }
        long fineTime = 0;
        if (ftBytes > 0) {
            if (ftBytes > 2) {// more than 2 bytes no needed for millisecond resolution
                ftBytes = 2;
            }
            int fb = ftBytes;
            while (fb > 0) {
                fineTime = (fineTime << 8) + (0xFF & s.getAsByte());
                fb--;
            }
            fineTime = 1000 * fineTime / (1 << (ftBytes * 8));
        }
        return coarseTime * 1000 + fineTime;
    }
    
    @Override
    public String toString() {
        return "CucTimeDecoder [implicitPField=" + implicitPField + "]";
    }

}
