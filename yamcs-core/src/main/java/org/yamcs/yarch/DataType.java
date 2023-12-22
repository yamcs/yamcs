/**
 * 
 */
package org.yamcs.yarch;

import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.MessageLite;

/**
 * Types supported by yarch. Currently TUPLE and LIST do now work well. ENUM is just like String, except that when it's
 * stored on disk a two bytes integer value from a map is stored instead of the String. (maximum allowed version is 2^16
 * (which is anyway too big considering that the map is stored as serialised yaml file)
 * 
 * PROTOBUF is a Google Protocol Buffer message
 * 
 * @author nm
 *
 */
public class DataType {

    public enum _type {
        BYTE,
        SHORT,
        INT,
        LONG,
        DOUBLE,
        TIMESTAMP,
        STRING,
        BINARY,
        BOOLEAN,
        ENUM,
        PROTOBUF,
        PARAMETER_VALUE,
        TUPLE,
        ARRAY,
        HRES_TIMESTAMP,
        UUID
    }

    public final _type val;

    public static final DataType BYTE = new DataType(_type.BYTE, (byte) 1, true);
    public static final DataType SHORT = new DataType(_type.SHORT, (byte) 2, true);
    public static final DataType INT = new DataType(_type.INT, (byte) 3, true);
    public static final DataType LONG = new DataType(_type.LONG, (byte) 4, true);
    public static final DataType DOUBLE = new DataType(_type.DOUBLE, (byte) 5, true);
    public static final DataType STRING = new DataType(_type.STRING, (byte) 6, true);
    public static final DataType BINARY = new DataType(_type.BINARY, (byte) 7, true);
    public static final DataType BOOLEAN = new DataType(_type.BOOLEAN, (byte) 8);
    public static final DataType TIMESTAMP = new DataType(_type.TIMESTAMP, (byte) 9, true);
    public static final DataType ENUM = new DataType(_type.ENUM, (byte) 10);
    public static final DataType PARAMETER_VALUE = new DataType(_type.PARAMETER_VALUE, (byte) 11);

    public static final byte PROTOBUF_ID = 12;
    public static final byte TUPLE_ID = 13;
    public static final byte ARRAY_ID = 14;

    public static final DataType HRES_TIMESTAMP = new DataType(_type.HRES_TIMESTAMP, (byte) 15, true);
    public static final DataType UUID = new DataType(_type.UUID, (byte) 16);

    // since yamcs 5.3 the it is stored on disk before the column index, see TableDefinition
    private final byte id;

    private final boolean comparable;

    protected DataType(_type t, byte id, boolean comparable) {
        this.val = t;
        this.id = id;
        this.comparable = comparable;
    }

    protected DataType(_type t, byte id) {
        this(t, id, false);
    }

    public static DataType tuple(TupleDefinition td) {
        return new TupleDataType(td);
    }

    public static DataType array(DataType elementType) {
        return new ArrayDataType(elementType);
    }

    public static DataType protobuf(String className) {
        return new ProtobufDataType(className);
    }

    public static DataType protobuf(Class<? extends MessageLite> clazz) {
        return new ProtobufDataType(clazz.getName());
    }

    /**
     * this is the inverse of {@link #name()}
     * 
     * @param name
     * @return the DataType corresponding to the name
     * @throws IllegalArgumentException
     *             thrown in case the name is invalid
     */
    public static DataType byName(String name) throws IllegalArgumentException {
        if (name == null) {
            throw new NullPointerException();
        }
        switch (name) {
        case "BYTE":
            return BYTE;
        case "SHORT":
            return SHORT;
        case "INT":
            return INT;
        case "DOUBLE":
            return DOUBLE;
        case "STRING":
            return STRING;
        case "BINARY":
            return BINARY;
        case "BOOLEAN":
            return BOOLEAN;
        case "TIMESTAMP":
            return TIMESTAMP;
        case "ENUM":
            return ENUM;
        case "LONG":
            return LONG;
        case "HRES_TIMESTAMP":
            return HRES_TIMESTAMP;
        case "UUID":
            return UUID;
        case "PARAMETER_VALUE":
            return PARAMETER_VALUE;
        default:
            if (name.toUpperCase().startsWith("PROTOBUF(")) {
                return protobuf(name.substring(9, name.length() - 1));
            }
            if (name.toUpperCase().startsWith("ARRAY(")) {
                return array(byName(name.substring(6, name.length() - 1)));
            }

            throw new IllegalArgumentException("invalid or unsupported DataType '" + name + "'");
        }
    }

    /**
     * return the size in bytes of the encoded data type if it can be encoded on fixed size, or -1 if not.
     * 
     * @param dt
     * @return
     */
    public static int getSerializedSize(DataType dt) {
        switch (dt.val) {
        case INT:
            return 4;
        case SHORT:
        case ENUM: // intentional fall-through
            return 2;
        case BYTE:
        case BOOLEAN: // intentional fall-through
            return 1;
        case LONG:
        case DOUBLE:
        case TIMESTAMP: // intentional fall-through
            return 8;
        case HRES_TIMESTAMP:
            return 12;
        case UUID:
            return 16;
        default:
            return -1;
        }
    }

    /* returns Int, Short, etc suitable to use as getInt(), getShort() on the Object */
    public static String capitalized(String s) {
        String t = s.toString();
        return t.substring(0, 1).toUpperCase() + t.substring(1).toLowerCase();
    }

    public String javaType() {
        switch (val) {
        case BOOLEAN:
        case BYTE:
        case DOUBLE:
        case SHORT:
        case STRING:
        case LONG:
            return capitalized(val.toString());
        case BINARY:
            return "byte[]";
        case TIMESTAMP:
            return "Long";
        case ENUM:
            return "String";
        case INT:
            return "Integer";
        case PARAMETER_VALUE:
            return "ParameterValue";
        case HRES_TIMESTAMP:
            return "org.yamcs.time.Instant";
        case UUID:
            return "java.util.UUID";
        case ARRAY:
            return "java.util.List";
        default:
            throw new IllegalStateException("no java type available for " + this);
        }
    }

    public String primitiveJavaType() {
        switch (val) {
        case BOOLEAN:
        case BYTE:
        case DOUBLE:
        case SHORT:
        case INT:
        case LONG:
            return val.toString().toLowerCase();
        case TIMESTAMP:
            return "long";
        default:
            throw new IllegalStateException("no primitive java type for " + val);
        }
    }

    public boolean isPrimitiveJavaType() {
        switch (val) {
        case BOOLEAN:
        case BYTE:
        case DOUBLE:
        case SHORT:
        case INT:
        case LONG:
        case TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    @Override
    public String toString() {
        return val.toString();
    }

    /**
     * Returns type as string.
     * 
     * 
     * @return for basic types returns the enum name for PROTOBUF returns PROTOBUF(className)
     */
    public String name() {
        return val.name();
    }

    public static DataType typeOf(Object v) {
        if (v instanceof Boolean) {
            return BOOLEAN;
        } else if (v instanceof Byte) {
            return BYTE;
        } else if (v instanceof Short) {
            return SHORT;
        } else if (v instanceof Integer) {
            return INT;
        } else if (v instanceof Double) {
            return DOUBLE;
        } else if (v instanceof Long) {
            return LONG;
        } else if (v instanceof String) {
            return STRING;
        } else if (v instanceof byte[]) {
            return BINARY;
        } else if (v instanceof ParameterValue) {
            return PARAMETER_VALUE;
        } else if (v instanceof Instant) {
            return HRES_TIMESTAMP;
        } else if (v instanceof java.util.UUID) {
            return UUID;
        } else if (v instanceof List<?>) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                throw new IllegalArgumentException("Constant empty arrays not supported");
            }
            return DataType.array(typeOf(l.get(0)));
        } else if (v instanceof MessageLite) {
            return DataType.protobuf(v.getClass().getName());
        } else {
            throw new IllegalArgumentException("invalid or unsupported object of type of " + v.getClass());
        }
    }

    public static int compare(Object v1, Object v2) {
        if (v1 instanceof Boolean) {
            return ((Boolean) v1).compareTo((Boolean) v2);
        } else if (v1 instanceof Byte) {
            return ((Byte) v1).compareTo((Byte) v2);
        } else if (v1 instanceof Short) {
            return ((Short) v1).compareTo((Short) v2);
        } else if (v1 instanceof Integer) {
            return ((Integer) v1).compareTo((Integer) v2);
        } else if (v1 instanceof Double) {
            return ((Double) v1).compareTo((Double) v2);
        } else if (v1 instanceof Long) {
            return ((Long) v1).compareTo((Long) v2);
        } else if (v1 instanceof String) {
            return ((String) v1).compareTo((String) v2);
        } else {
            throw new IllegalArgumentException("cannot compare objects of type " + v1.getClass());
        }
    }

    /**
     * Performs casting of v from sourceType to targetType
     * 
     * @param sourceType
     * @param targetType
     * @param v
     * @return the casted object (can be v if no casting is performed)
     * @throws IllegalArgumentException
     */
    public static Object castAs(DataType sourceType, DataType targetType, Object v) throws IllegalArgumentException {
        if (sourceType.equals(targetType)) {
            return v;
        }

        if (v instanceof Number) {
            Number n = (Number) v;
            switch (targetType.val) {
            case BYTE:
                return n.byteValue();
            case DOUBLE:
                return n.doubleValue();
            case SHORT:
                return n.shortValue();
            case INT:
                return n.intValue();
            case LONG:
            case TIMESTAMP:
                return n.longValue();
            case HRES_TIMESTAMP:
                return Instant.get(n.longValue());
            case STRING:
            case ENUM:
                return n.toString();
            default:
                // throws exception below
            }
        } else if (v instanceof String) {
            String s = (String) v;
            switch (targetType.val) {
            case BYTE:
                return Byte.decode(s);
            case DOUBLE:
                return Double.valueOf(s);
            case SHORT:
                return Short.decode(s);
            case INT:
                return Integer.decode(s);
            case LONG:
                return Long.decode(s);
            case TIMESTAMP:
                return TimeEncoding.parse(s);
            case HRES_TIMESTAMP:
                return TimeEncoding.parseHres(s);
            case UUID:
                return java.util.UUID.fromString(s);
            case STRING:
            case ENUM:
                return s;
            default:
                // throws exception below
            }
        } else if (v instanceof Instant) {
            long n = ((Instant) v).getMillis();
            switch (targetType.val) {
            case BYTE:
                return (byte) n;
            case DOUBLE:
                return (double) n;
            case SHORT:
                return (short) n;
            case INT:
                return (int) n;
            case LONG:
            case TIMESTAMP:
                return n;
            case STRING:
            case ENUM:
                return Long.toString(n);
            default:
                // throws exception below
            }
        }

        throw new IllegalArgumentException("Cannot convert '" + v + "' from " + sourceType + " into " + targetType);
    }

    /**
     * Performs casting: numbers to numbers, numbers to string, string to numbers
     * 
     * @param targetType
     * @param v
     * @return the casted object (can be to v if no casting is performed)
     * @throws IllegalArgumentException
     * 
     */
    public static Object castAs(DataType targetType, Object v) throws IllegalArgumentException {
        return castAs(typeOf(v), targetType, v);
    }

    public static boolean isNumber(DataType dt) {
        switch (dt.val) {
        case BYTE:
        case DOUBLE:
        case INT:
        case LONG:
        case SHORT:
        case TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    public static boolean compatible(DataType dt1, DataType dt2) {
        if (dt1 == dt2) {
            return true;
        }

        if (isNumber(dt1) && isNumber(dt2)) {
            return true;
        }

        if (dt1.val == _type.STRING || dt1.val == _type.ENUM) {
            return dt2.val == _type.STRING || dt2.val == _type.ENUM;
        }
        if (dt1 instanceof ArrayDataType && dt2 instanceof ArrayDataType) {
            return compatible(((ArrayDataType) dt1).getElementType(), ((ArrayDataType) dt2).getElementType());
        }
        if (dt1 instanceof ProtobufDataType && dt2 instanceof ProtobufDataType) {
            return ((ProtobufDataType) dt1).getClassName().equals(((ProtobufDataType) dt2).getClassName());
        }
        return false;
    }

    public byte getTypeId() {
        return id;
    }

    /**
     * Return true if this data type is an enum or a composite type (array) containing an enum
     */
    public boolean hasEnums() {
        return val == _type.ENUM;
    }

    /**
     * 
     * @return true if two values of this type are comparable (i.e. if they support a natural ordering)
     */
    public boolean isComparable() {
        return comparable;
    }
}
