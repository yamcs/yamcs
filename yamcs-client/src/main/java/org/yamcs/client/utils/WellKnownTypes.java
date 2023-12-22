package org.yamcs.client.utils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.protobuf.*;

public class WellKnownTypes {

    static final public Instant TIMESTAMP_MIN = Instant.parse("0001-01-01T00:00:00Z");
    static final public Instant TIMESTAMP_MAX = Instant.parse("9999-12-31T23:59:59Z");

    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static Timestamp toTimestamp(Instant instant) {
        if (instant.isBefore(TIMESTAMP_MIN)) {
            throw new IllegalArgumentException("instant too small");
        }
        if (instant.isAfter(TIMESTAMP_MAX)) {
            throw new IllegalArgumentException("instant too big");
        }

        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    public static Struct toStruct(Map<String, ?> map) {
        Struct.Builder structb = Struct.newBuilder();
        for (Entry<String, ?> entry : map.entrySet()) {
            structb.putFields(entry.getKey(), toValue(entry.getValue()));
        }
        return structb.build();
    }

    public static ListValue toListValue(List<?> list) {
        ListValue.Builder listb = ListValue.newBuilder();
        for (Object el : list) {
            listb.addValues(toValue(el));
        }
        return listb.build();
    }


    public static ListValue toListValue(Object[] array) {
        ListValue.Builder listb = ListValue.newBuilder();
        for (Object el : array) {
            listb.addValues(toValue(el));
        }
        return listb.build();
    }

    public static String toHex(byte[] array) {
        char[] HEXCHARS = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[array.length * 2];
        for (int j = 0; j < array.length; j++) {
            int v = array[j] & 0xFF;
            hexChars[j * 2] = HEXCHARS[v >>> 4];
            hexChars[j * 2 + 1] = HEXCHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    @SuppressWarnings("unchecked")
    public static Value toValue(Object value) {
        if (value == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        } else if (value instanceof byte[]) {
            return Value.newBuilder().setStringValue(toHex((byte[]) value)).build();
        } else if (value instanceof Boolean) {
            return Value.newBuilder().setBoolValue((Boolean) value).build();
        } else if (value instanceof Float) {
            return Value.newBuilder().setNumberValue((Float) value).build();
        } else if (value instanceof Double) {
            return Value.newBuilder().setNumberValue((Double) value).build();
        } else if (value instanceof Byte) {
            return Value.newBuilder().setNumberValue((Byte) value).build();
        } else if (value instanceof Short) {
            return Value.newBuilder().setNumberValue((Short) value).build();
        } else if (value instanceof Integer) {
            return Value.newBuilder().setNumberValue((Integer) value).build();
        } else if (value instanceof List) {
            return Value.newBuilder().setListValue(toListValue((List<?>) value)).build();
        } else if (value.getClass().isArray()) {
            return Value.newBuilder().setListValue(toListValue((Object[]) value)).build();
        } else if (value instanceof Map) {
            return Value.newBuilder().setStructValue(toStruct((Map<String, ?>) value)).build();
        } else { // Especially "Long", which we don't want to put in a double
            return Value.newBuilder().setStringValue(value.toString()).build();
        }
    }
}
