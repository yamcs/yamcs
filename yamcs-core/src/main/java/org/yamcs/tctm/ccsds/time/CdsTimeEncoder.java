package org.yamcs.tctm.ccsds.time;

import static org.yamcs.tctm.ccsds.time.CdsTimeDecoder.MILLIS_PER_DAY;

import java.time.LocalDate;

import org.yamcs.logging.Log;
import org.yamcs.time.TimeEncoder;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;

/**
 * Encoder for CCSDS Day Segmented time code as specified in TIME CODE FORMATS, CCSDS 301.0-B-4, Nov 2010, Chapter
 * 3.3
 * <p>
 * The {@link #encode(long, byte[], int)} method takes a Yamcs instant and writes the number of UTC days from the epoch
 * and the millisecond of day. During a leap second the millisecond of day goes from 86400000 to 86400999 (CCSDS
 * 301.0-B-4 Annex A). The submillisecond segment, if present, is set to 0.
 * <p>
 * The {@link #encodeRaw(long, byte[], int)} method takes a number of microseconds from the epoch, counting each day as
 * 86400000000 microseconds; it cannot express a leap second.
 *
 * @see CdsTimeDecoder
 */
public class CdsTimeEncoder implements TimeEncoder {
    public static final Log log = new Log(CdsTimeEncoder.class);

    final int dayBytes;
    final int subMillisBytes;
    // -1 if the pfield is not written
    final int pfield;
    // the epoch as number of days since 1970-01-01
    final long epochDay;

    /**
     * @param pfield
     *            P-field determining the length of the day and submillisecond segments
     * @param implicitPfield
     *            if true, the P-field is not written in the output
     * @param epochDate
     *            the date of day 0, which starts at 00:00:00 UTC; for example {@code LocalDate.of(1958, 1, 1)} for the
     *            CCSDS recommended epoch
     */
    public CdsTimeEncoder(int pfield, boolean implicitPfield, LocalDate epochDate) {
        CdsTimeDecoder.checkPfield(pfield);
        this.dayBytes = CdsTimeDecoder.dayBytes(pfield);
        this.subMillisBytes = CdsTimeDecoder.subMillisBytes(pfield);
        this.pfield = implicitPfield ? -1 : pfield;
        this.epochDay = epochDate.toEpochDay();
    }

    /**
     * Encodes the Yamcs instant as the number of days from the epoch and the millisecond of day.
     */
    @Override
    public int encode(long instant, byte[] buf, int offset) {
        DateTimeComponents utc = TimeEncoding.toUtc(instant);
        long unixDay = LocalDate.of(utc.getYear(), utc.getMonth(), utc.getDay()).toEpochDay();
        long msOfDay = instant - TimeEncoding.fromUnixMillisec(unixDay * MILLIS_PER_DAY);
        return encode(unixDay - epochDay, msOfDay, 0, buf, offset);
    }

    @Override
    public int encodeRaw(long time, byte[] buf, int offset) {
        long millis = Math.floorDiv(time, 1000);
        int usOfMs = (int) Math.floorMod(time, 1000);
        return encode(Math.floorDiv(millis, MILLIS_PER_DAY), Math.floorMod(millis, MILLIS_PER_DAY), usOfMs, buf,
                offset);
    }

    private int encode(long day, long msOfDay, int usOfMs, byte[] buf, int offset) {
        if (day < 0) {
            throw new IllegalArgumentException("Cannot encode in CDS a time before the epoch (day " + day + ")");
        }
        if (day >= (1L << (8 * dayBytes))) {
            throw new IllegalArgumentException(
                    "Day " + day + " does not fit in the " + (8 * dayBytes) + " bits CDS day segment");
        }
        if (log.isTraceEnabled()) {
            log.trace("Encoding time with day={} msOfDay={} usOfMs={}", day, msOfDay, usOfMs);
        }

        int offset0 = offset;
        if (pfield != -1) {
            buf[offset++] = (byte) pfield;
        }
        offset = writeUnsigned(day, dayBytes, buf, offset);
        offset = writeUnsigned(msOfDay, 4, buf, offset);
        if (subMillisBytes == 2) {
            offset = writeUnsigned(usOfMs, 2, buf, offset);
        } else if (subMillisBytes == 4) {
            offset = writeUnsigned(usOfMs * 1_000_000L, 4, buf, offset);
        }
        return offset - offset0;
    }

    private static int writeUnsigned(long x, int n, byte[] buf, int offset) {
        for (int i = n - 1; i >= 0; i--) {
            buf[offset++] = (byte) (x >> (i * 8));
        }
        return offset;
    }

    @Override
    public int getEncodedLength() {
        return (pfield == -1 ? 0 : 1) + dayBytes + 4 + subMillisBytes;
    }

    @Override
    public String toString() {
        return "CdsTimeEncoder [dayBytes=" + dayBytes + ", subMillisBytes=" + subMillisBytes + "]";
    }
}
