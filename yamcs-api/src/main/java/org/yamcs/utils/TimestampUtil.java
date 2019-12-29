package org.yamcs.utils;

import java.time.Instant;

import com.google.protobuf.Timestamp;

public class TimestampUtil {
    /**
     * 
     * @return current (now) protobuf timestamp
     */
    public static Timestamp currentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
    }

    /**
     * Converts java time in milliseconds to protobuf Timestamp
     * 
     * @param timeMillisec
     *            java timestamp to be converted
     * @return
     */
    public static Timestamp java2Timestamp(long timeMillisec) {
        long sec = timeMillisec / 1000;
        int ms = (int) (timeMillisec - sec * 1000);
        return Timestamp.newBuilder().setSeconds(sec).setNanos(ms * 1000_000).build();
    }

    /**
     * Converts protobuf Timestamp into java time in milliseconds Note: this loses precision (nanoseconds to
     * milliseconds)
     * 
     * @param ts
     *            protobuf timestamp to be converted
     * @return
     */
    public static long timestamp2Java(Timestamp ts) {
        return ts.getSeconds() * 1000 + ts.getNanos() / 1000_000;
    }
}
