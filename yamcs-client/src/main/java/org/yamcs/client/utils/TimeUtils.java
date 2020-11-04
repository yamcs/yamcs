package org.yamcs.client.utils;

import java.time.Instant;

import com.google.protobuf.Timestamp;

public class TimeUtils {
    static final public Instant TIMESTAMP_MIN = Instant.parse("0001-01-01T00:00:00Z");
    static final public Instant TIMESTAMP_MAX = Instant.parse("9999-12-31T23:59:59Z");

    public static Timestamp toTimestamp(Instant instant) {
        if (instant.isBefore(TIMESTAMP_MIN)) {
            throw new IllegalArgumentException("instant too small");
        }
        if (instant.isAfter(TIMESTAMP_MAX)) {
            throw new IllegalArgumentException("instant too big");
        }

        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

}
