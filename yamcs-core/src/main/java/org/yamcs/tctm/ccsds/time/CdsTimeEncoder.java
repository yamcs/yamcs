package org.yamcs.tctm.ccsds.time;

import static org.yamcs.tctm.ccsds.time.CdsTimeDecoder.MILLIS_PER_DAY;

import org.yamcs.logging.Log;
import org.yamcs.time.TimeEncoder;

/**
 * Encoder for CCSDS Day Segmented time code as specified in TIME CODE FORMATS, CCSDS 301.0-B-4, Nov 2010, Chapter
 * 3.3
 * <p>
 * The {@link #encode(long, byte[], int)} method takes the number of milliseconds from the epoch, counting each day as
 * 86400000 milliseconds (i.e. without leap seconds). The submillisecond segment, if present, is set to 0.
 * <p>
 * The {@link #encodeRaw(long, byte[], int)} method takes the number of microseconds counted the same way.
 *
 * @see CdsTimeDecoder
 */
public class CdsTimeEncoder implements TimeEncoder {
    public static final Log log = new Log(CdsTimeEncoder.class);

    final int dayBytes;
    final int subMillisBytes;
    // -1 if the pfield is not written
    final int pfield;

    /**
     * @param pfield
     *            P-field determining the length of the day and submillisecond segments
     * @param implicitPfield
     *            if true, the P-field is not written in the output
     */
    public CdsTimeEncoder(int pfield, boolean implicitPfield) {
        CdsTimeDecoder.checkPfield(pfield);
        this.dayBytes = CdsTimeDecoder.dayBytes(pfield);
        this.subMillisBytes = CdsTimeDecoder.subMillisBytes(pfield);
        this.pfield = implicitPfield ? -1 : pfield;
    }

    @Override
    public int encode(long time, byte[] buf, int offset) {
        return encode(time, 0, buf, offset);
    }

    @Override
    public int encodeRaw(long time, byte[] buf, int offset) {
        long millis = Math.floorDiv(time, 1000);
        int usOfMs = (int) Math.floorMod(time, 1000);
        return encode(millis, usOfMs, buf, offset);
    }

    private int encode(long millis, int usOfMs, byte[] buf, int offset) {
        if (millis < 0) {
            throw new IllegalArgumentException("Cannot encode negative time " + millis + " in CDS");
        }
        long day = millis / MILLIS_PER_DAY;
        long msOfDay = millis % MILLIS_PER_DAY;
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
