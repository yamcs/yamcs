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
 * CDS counts UTC days. During a leap second the millisecond of day goes from 86400000 to 86400999 (CCSDS 301.0-B-4
 * Annex A), so the leap second is represented unambiguously. {@link #decodeDayTime(byte[], int)} returns the day and
 * the millisecond of day; the packet preprocessor uses it to compute the time including the leap seconds.
 * <p>
 * The {@link #decode(byte[], int)} method returns {@code day * 86400000 + millisecondOfDay}. This count has no room
 * for the leap seconds: a time inside a leap second gives the same value as the first second of the following day.
 * The submillisecond part is dropped.
 * <p>
 * The {@link #decodeRaw(byte[], int)} method returns the number of microseconds computed the same way (picoseconds
 * are truncated to microseconds).
 * <p>
 * The epoch bit of the P-field is ignored; the epoch is configured separately.
 */
public class CdsTimeDecoder implements TimeDecoder {
    public static final Log log = new Log(CdsTimeDecoder.class);

    public static final long MILLIS_PER_DAY = 86_400_000L;
    /**
     * The largest millisecond of day, reached during a leap second.
     */
    public static final long MAX_MILLIS_OF_DAY = MILLIS_PER_DAY + 999;

    final int pfield;

    /**
     * Day and millisecond of day read from a CDS time code.
     *
     * @param day
     *            number of days from the epoch
     * @param msOfDay
     *            millisecond of the day, from 86400000 to 86400999 during a leap second
     */
    public record DayTime(long day, long msOfDay) {
    }

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
     * Returns the day and the millisecond of day; the submillisecond part is dropped.
     */
    public DayTime decodeDayTime(byte[] buf, int offset) {
        return decodeDayTime(CucTimeDecoder.getSupplier(buf, offset));
    }

    /**
     * Returns the day and the millisecond of day; the submillisecond part is dropped.
     */
    public DayTime decodeDayTime(ByteSupplier s) {
        int p = getPfield(s);
        long day = readUnsigned(s, dayBytes(p));
        long msOfDay = readMsOfDay(s);
        readUnsigned(s, subMillisBytes(p)); // the submillisecond part is not used
        if (log.isTraceEnabled()) {
            log.trace("Extracted day={} msOfDay={}", day, msOfDay);
        }
        return new DayTime(day, msOfDay);
    }

    /**
     * Returns the number of milliseconds from the epoch, counting each day as 86400000 milliseconds.
     */
    public long decode(ByteSupplier s) {
        DayTime dt = decodeDayTime(s);
        return dt.day() * MILLIS_PER_DAY + dt.msOfDay();
    }

    /**
     * Returns the number of microseconds from the epoch, counting each day as 86400000000 microseconds.
     */
    public long decodeRaw(ByteSupplier s) {
        int p = getPfield(s);
        long day = readUnsigned(s, dayBytes(p));
        long msOfDay = readMsOfDay(s);
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

    private static long readMsOfDay(ByteSupplier s) {
        long msOfDay = readUnsigned(s, 4);
        if (msOfDay > MAX_MILLIS_OF_DAY) {
            throw new IllegalArgumentException("Invalid CDS millisecond of day " + msOfDay
                    + ": the maximum is " + MAX_MILLIS_OF_DAY + ", reached during a leap second");
        }
        return msOfDay;
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
