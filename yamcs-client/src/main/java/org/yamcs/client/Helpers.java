package org.yamcs.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;

import com.google.protobuf.Timestamp;

public class Helpers {

    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static NamedObjectId toNamedObjectId(String name) {
        // Some API calls still require NamedObjectId objects, which are bothersome.
        // This method automatically generates them from a name which can either be the qualified name (preferred)
        // or some alias in the form NAMESPACE/NAME
        if (name.startsWith("/")) {
            return NamedObjectId.newBuilder().setName(name).build();
        } else {
            String[] parts = name.split("\\/", 2);
            if (parts.length < 2) {
                throw new IllegalArgumentException(String.format("'%s' is not a valid name."
                        + " Use fully-qualified names or, alternatively,"
                        + " an alias in the format NAMESPACE/NAME", name));
            }
            return NamedObjectId.newBuilder().setNamespace(parts[0]).setName(parts[1]).build();
        }
    }

    public static String toName(NamedObjectId id) {
        if (id.hasNamespace()) {
            return id.getNamespace() + "/" + id.getName();
        } else {
            return id.getName();
        }
    }

    /**
     * Converts a Protobuf value from the API into a Java equivalent
     */
    public static Object parseValue(Value value) {
        switch (value.getType()) {
        case FLOAT:
            return value.getFloatValue();
        case DOUBLE:
            return value.getDoubleValue();
        case SINT32:
            return value.getSint32Value();
        case UINT32:
            return value.getUint32Value() & 0xFFFFFFFFL;
        case UINT64:
            return value.getUint64Value();
        case SINT64:
            return value.getSint64Value();
        case STRING:
            return value.getStringValue();
        case BOOLEAN:
            return value.getBooleanValue();
        case TIMESTAMP:
            return Instant.parse(value.getStringValue());
        case ENUMERATED:
            return value.getStringValue();
        case BINARY:
            return value.getBinaryValue().toByteArray();
        case ARRAY:
            List<Object> arr = new ArrayList<>(value.getArrayValueCount());
            for (Value item : value.getArrayValueList()) {
                arr.add(parseValue(item));
            }
            return arr;
        case AGGREGATE:
            Map<String, Object> obj = new LinkedHashMap<>();
            for (int i = 0; i < value.getAggregateValue().getNameCount(); i++) {
                obj.put(value.getAggregateValue().getName(i), value.getAggregateValue().getValue(i));
            }
            return obj;
        default:
            throw new IllegalStateException("Unexpected value type " + value.getType());
        }
    }
}
