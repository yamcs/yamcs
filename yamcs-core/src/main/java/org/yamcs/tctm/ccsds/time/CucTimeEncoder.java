package org.yamcs.tctm.ccsds.time;

import org.yamcs.logging.Log;
import org.yamcs.time.TimeEncoder;

public class CucTimeEncoder implements TimeEncoder {
    public static final Log log = new Log(CucTimeEncoder.class);
    final int basicTimeBytes;
    final int fractionalTimeBytes;
    final int pfield1;
    final int pfield2;

    public CucTimeEncoder(int pfield1, boolean implicitPfield) {
        this(pfield1, -1, implicitPfield);
    }

    public CucTimeEncoder(int pfield1, int pfield2, boolean implicitPfield) {
        if (pfield1 < 0) {
            throw new IllegalArgumentException("pfield1 cannot be negative");
        }
        if ((pfield1 & 0x80) != 0 && pfield2 < 0) {
            throw new IllegalArgumentException("If pfield is on two octets, pfield2 cannot be negative");
        }
        if (pfield1 > 0xFF) {
            throw new IllegalArgumentException("pfield1 cannot be larger than 0xFF");
        }
        if (pfield2 > 0x7F) {
            throw new IllegalArgumentException("pfield2 cannot be larger than 0x7F");
        }

        if (pfield1 != -1) {
            int btBytes = 1 + ((pfield1 >> 2) & 3);
            int ftBytes = pfield1 & 3;

            if ((pfield1 & 0x80) == 1) {
                btBytes += ((pfield2 >> 6) & 3);
                ftBytes += ((pfield2 >> 2) & 7);
            }

            this.basicTimeBytes = btBytes;
            this.fractionalTimeBytes = ftBytes;
        } else {
            this.basicTimeBytes = -1;
            this.fractionalTimeBytes = -1;
        }
        if (implicitPfield) {
            this.pfield1 = -1;
            this.pfield2 = -1;
        } else {
            this.pfield1 = pfield1;
            this.pfield2 = pfield2;
        }
    }

    public int encode(long time, byte[] buf, int offset) {
        int offset0 = offset;
        long coarseTime = time / 1000;
        long fineTime = 0;

        if (pfield1 != -1) {
            buf[offset++] = (byte) pfield1;
        }

        if (pfield2 != -1) {
            buf[offset++] = (byte) pfield2;
        }

        if (fractionalTimeBytes > 0) {
            fineTime = (time % 1000) * (1 << (fractionalTimeBytes * 8)) / 1000;
        }

        if (log.isTraceEnabled()) {
            log.trace("Encoding time with coarseTime={} sec and fineTime={} millis", coarseTime, fineTime);
        }
        // Encode the coarse time
        for (int i = basicTimeBytes - 1; i >= 0; i--) {
            buf[offset++] = (byte) (coarseTime >> (i * 8));
        }

        // Encode the fine time, if applicable
        for (int i = fractionalTimeBytes - 1; i >= 0; i--) {
            buf[offset++] = (byte) (fineTime >> (i * 8));
        }

        return (offset - offset0);
    }

    public int encodeRaw(long time, byte[] buf, int offset) {
        int offset0 = offset;

        if (log.isTraceEnabled()) {
            log.trace("Encoding raw time: {}", time);
        }
        if (pfield1 != -1) {
            buf[offset++] = (byte) pfield1;
        }

        if (pfield2 != -1) {
            buf[offset++] = (byte) pfield2;
        }

        for (int i = basicTimeBytes + fractionalTimeBytes - 1; i >= 0; i--) {
            buf[offset++] = (byte) (time >> (i * 8));
        }
        return (offset - offset0);
    }

    @Override
    public String toString() {
        return "CucTimeEncoder [basicTimeBytes=" + basicTimeBytes
                + ", fractionalTimeBytes=" + fractionalTimeBytes + "]";
    }

    @Override
    public int getEncodedLength() {
        int len = basicTimeBytes + fractionalTimeBytes;
        if (pfield1 > 0) {
            len++;

            if (pfield2 > 0) {
                len++;
            }
        }
        return len;
    }
}
