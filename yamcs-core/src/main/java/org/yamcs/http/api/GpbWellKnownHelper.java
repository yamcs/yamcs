package org.yamcs.http.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Helper methods for dealing with Protobuf "well-known types".
 */
public class GpbWellKnownHelper {

    /**
     * Converts a Protobuf struct to Java Map where all elements are converted to equivalent Java types.
     */
    public static Map<String, Object> toJava(Struct struct) {
        var map = new LinkedHashMap<String, Object>(struct.getFieldsCount());
        struct.getFieldsMap().forEach((k, v) -> map.put(k, toJava(v)));
        return map;
    }

    public static List<Object> toJava(ListValue value) {
        return value.getValuesList().stream()
                .map(GpbWellKnownHelper::toJava)
                .collect(Collectors.toList());
    }

    public static Object toJava(Value value) {
        switch (value.getKindCase()) {
        case NULL_VALUE:
            return null;
        case BOOL_VALUE:
            return value.getBoolValue();
        case NUMBER_VALUE:
            return value.getNumberValue();
        case STRING_VALUE:
            return value.getStringValue();
        case STRUCT_VALUE:
            return toJava(value.getStructValue());
        case LIST_VALUE:
            return toJava(value.getListValue());
        default:
            throw new IllegalStateException("Unexpected value kind '" + value.getKindCase() + "'");
        }
    }
}
