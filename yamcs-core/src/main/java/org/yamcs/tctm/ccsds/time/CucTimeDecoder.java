package org.yamcs.tctm.ccsds.time;

import org.yamcs.tctm.TimeDecoder;
import org.yamcs.utils.ByteSupplier;

/**
 * Decoder for CCSDS Unsegmented time Code as specified in
 * TIME CODE FORMATS, CCSDS 301.0-B-4, Nov 2010
 * 
 * The time code is composed by
 * <ul>
 * <li>P-Field (preamble field) 8 or 16 bits optional</li>
 * <li>T-Field - up to 7+10 bytes (although it could be longer for custom codes)</li>
 * </ul>
 * 
 * @author nm
 *
 */
public class CucTimeDecoder implements TimeDecoder {
    final int basicTimeBytes;
    final int fractionalTimeBytes;

    public CucTimeDecoder(int pfield1) {
        this(pfield1, -1);
        if (pfield1 > 0 && (pfield1 & 0x80) != 0) {
            throw new IllegalArgumentException("If pfield is on two octets, please use the other constructor");
        }
    }

    /**
     * Constructor for decoder.
     * 
     * @param pfield1
     *            first octet of the pfield
     *            -1 means is part of the packet, other values means it is pre-defined (implicit)
     * @param pfield2
     *            second octet of the pfield - used if the first bit of pfield1 (seen as one byte) is 1
     */
    public CucTimeDecoder(int pfield1, int pfield2) {
        if (pfield1 != -1) {
            int btBytes = 1 + ((pfield1 >> 2) & 3);
            int ftBytes = pfield1 & 3;

            if ((pfield1 & 0x80) == 1) { // extended pfield
                btBytes += ((pfield2 >> 6) & 3);
                ftBytes += ((pfield2 >> 2) & 7);
            }

            this.basicTimeBytes = btBytes;
            this.fractionalTimeBytes = ftBytes;
        } else {
            this.basicTimeBytes = -1;
            this.fractionalTimeBytes = -1;
        }
    }

    /**
     * Assuming that the basic time unit is second, return the number of milliseconds.
     */
    @Override
    public long decode(ByteSupplier s) {
        int btBytes, ftBytes;
        if (basicTimeBytes < 0) {
            int pfield = 0xFF & s.getAsByte();
            btBytes = 1 + ((pfield >> 2) & 3);
            ftBytes = pfield & 3;

            if ((pfield >> 7) == 1) { // extended pfield
                int extPfield = 0xFF & s.getAsByte();
                btBytes += ((extPfield >> 6) & 3);
                ftBytes += ((extPfield >> 2) & 7);
            }
            if (btBytes > 6) {
                throw new UnsupportedOperationException(
                        "Decoding with " + btBytes + " of basic time not supported (maximum is 6)");
            }
        } else {
            btBytes = basicTimeBytes;
            ftBytes = fractionalTimeBytes;
        }

        long coarseTime = 0;
        while (btBytes > 0) {
            coarseTime = (coarseTime << 8) + (0xFF & s.getAsByte());
            btBytes--;
        }
        long fineTime = 0;
        if (ftBytes > 0) {
            if (ftBytes > 2) {// more than 2 bytes not needed for millisecond resolution
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

    /**
     * Decodes the time in the highest available resolution.
     * <p>
     * If the length of the basicTime and fractional time is greater than 8, an exception is thrown.
     */
    @Override
    public long decodeRaw(ByteSupplier s) {
        int n;
        if (basicTimeBytes < 0) {
            int pfield = 0xFF & s.getAsByte();
            n = 1 + ((pfield >> 2) & 3);
            n += pfield & 3;

            if ((pfield >> 7) == 1) { // extended pfield
                int extPfield = 0xFF & s.getAsByte();
                n += ((extPfield >> 6) & 3);
                n += ((extPfield >> 2) & 7);
            }
        } else {
            n = basicTimeBytes + fractionalTimeBytes;
        }
        if (n > 8) {
            throw new UnsupportedOperationException("Raw time encoding on " + n + " bytes not supported");
        }

        long t = 0;
        while (n > 0) {
            t = (t << 8) + (0xFF & s.getAsByte());
            n--;
        }
        return t;
    }

    @Override
    public String toString() {
        return "CucTimeDecoder [basicTimeBytes=" + basicTimeBytes
                + " fractionalTimeBytes=" + fractionalTimeBytes + "]";
    }

}
