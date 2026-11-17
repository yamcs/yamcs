package org.yamcs.tctm.ccsds.time;

import org.yamcs.logging.Log;
import org.yamcs.time.TimeDecoder;
import org.yamcs.utils.ByteSupplier;

/**
 * Decoder for CCSDS Day Segmented time code as specified in TIME CODE FORMATS, CCSDS 301.0-B-4, Nov 2010, Chapter
 * 3.3
 * <p>
 * The time code is composed by
 * <ul>
 * <li>P-Field (preamble field) 8 bits, optional</li>
 * <li>T-Field: day (16 or 24 bits), millisecond of day (32 bits) and optional submillisecond (16 bits microseconds
 * or 32 bits picoseconds of millisecond)</li>
 * </ul>
 * <p>
 * The {@link #decode(byte[], int)} method returns {@code day * 86400000 + millisecondOfDay}. Because the day count is
 * UTC-based, this value does not include the leap seconds. The submillisecond part is dropped.
 * <p>
 * The {@link #decodeRaw(byte[], int)} method returns the number of microseconds computed the same way (picoseconds
 * are truncated to microseconds).
 * <p>
 * The epoch bit of the P-field is ignored; the epoch is configured separately.
 */
public class CdsTimeDecoder implements TimeDecoder {
    public static final Log log = new Log(CdsTimeDecoder.class);

    static final long MILLIS_PER_DAY = 86_400_000L;

    final int pfield;

    /**
     * Constructor for decoder.
     *
     * @param pfield
     *            the P-field; -1 means that it is part of the packet, other values mean that it is pre-defined
     *            (implicit)
     */
    public CdsTimeDecoder(int pfield) {
        if (pfield != -1) {
            checkPfield(pfield);
        }
        this.pfield = pfield;
    }

    @Override
    public long decode(byte[] buf, int offset) {
        return decode(CucTimeDecoder.getSupplier(buf, offset));
    }

    @Override
    public long decodeRaw(byte[] buf, int offset) {
        return decodeRaw(CucTimeDecoder.getSupplier(buf, offset));
    }

    /**
     * Returns the number of milliseconds from the epoch, counting each day as 86400000 milliseconds.
     */
    public long decode(ByteSupplier s) {
        int p = getPfield(s);
        long day = readUnsigned(s, dayBytes(p));
        long msOfDay = readUnsigned(s, 4);
        readUnsigned(s, subMillisBytes(p)); // the submillisecond part is not used
        if (log.isTraceEnabled()) {
            log.trace("Extracted day={} msOfDay={}", day, msOfDay);
        }
        return day * MILLIS_PER_DAY + msOfDay;
    }

    /**
     * Returns the number of microseconds from the epoch, counting each day as 86400000000 microseconds.
     */
    public long decodeRaw(ByteSupplier s) {
        int p = getPfield(s);
        long day = readUnsigned(s, dayBytes(p));
        long msOfDay = readUnsigned(s, 4);
        int subBytes = subMillisBytes(p);
        long subMillis = readUnsigned(s, subBytes);
        long usOfMs = subBytes == 4 ? subMillis / 1_000_000 : subMillis;

        return (day * MILLIS_PER_DAY + msOfDay) * 1000 + usOfMs;
    }

    private int getPfield(ByteSupplier s) {
        if (pfield != -1) {
            return pfield;
        }
        int p = 0xFF & s.getAsByte();
        checkPfield(p);
        return p;
    }

    static void checkPfield(int pfield) {
        if (pfield < 0 || pfield > 0xFF) {
            throw new IllegalArgumentException("Invalid CDS pfield " + pfield + ": it has to be on one octet");
        }
        if ((pfield >> 4) != 4) {
            throw new IllegalArgumentException(String.format(
                    "Invalid CDS pfield 0x%02X: the extension bit has to be 0 and the time code id 100", pfield));
        }
        if ((pfield & 3) == 3) {
            throw new IllegalArgumentException(String.format(
                    "Invalid CDS pfield 0x%02X: submillisecond length 11 is reserved", pfield));
        }
    }

    static int dayBytes(int pfield) {
        return (pfield & 0x04) == 0 ? 2 : 3;
    }

    static int subMillisBytes(int pfield) {
        return switch (pfield & 3) {
        case 0 -> 0;
        case 1 -> 2;
        default -> 4;
        };
    }

    private static long readUnsigned(ByteSupplier s, int n) {
        long x = 0;
        for (int i = 0; i < n; i++) {
            x = (x << 8) + (0xFF & s.getAsByte());
        }
        return x;
    }

    @Override
    public String toString() {
        return "CdsTimeDecoder [pfield=" + (pfield == -1 ? "explicit" : String.format("0x%02X", pfield)) + "]";
    }
}
