/**
 * 
 */
package org.yamcs.yarch;

import org.yamcs.parameter.ParameterValue;

/**
 * Types supported by yarch. Currently TUPLE and LIST do now work well.
 * ENUM is just like String, except that when it's stored on disk a two bytes integer value from a map is stored instead of the String.
 * (maximum allowed version is 2^16 (which is anyway too big considering that the map is stored as serialised yaml file)
 * 
 * PROTOBUF is a Google Protocol Buffer message
 * @author nm
 *
 */
public class DataType {
    
    public enum _type {BYTE, SHORT, INT, LONG, DOUBLE, TIMESTAMP, STRING, BINARY, BOOLEAN, ENUM, PROTOBUF, PARAMETER_VALUE, TUPLE, LIST}
    public final _type val;
    
    public static final DataType BYTE = new DataType(_type.BYTE, (byte)1);
    public static final DataType SHORT = new DataType(_type.SHORT, (byte)2);
    public static final DataType INT = new DataType(_type.INT, (byte)3);
    public static final DataType LONG = new DataType(_type.LONG, (byte)4);
    public static final DataType DOUBLE = new DataType(_type.DOUBLE, (byte)5);
    public static final DataType STRING = new DataType(_type.STRING, (byte)6);
    public static final DataType BINARY = new DataType(_type.BINARY, (byte)7);
    public static final DataType BOOLEAN = new DataType(_type.BOOLEAN, (byte)8);
    public static final DataType TIMESTAMP = new DataType(_type.TIMESTAMP, (byte)9);
    public static final DataType ENUM = new DataType(_type.ENUM, (byte)10);
    public static final DataType PARAMETER_VALUE = new DataType(_type.PARAMETER_VALUE, (byte)11);

    public static final byte PROTOBUF_ID = 12;
    public static final byte TUPLE_ID = 13;
    public static final byte LIST_ID = 14;
    
    //the id has been added in Yamcs 4.11 - it will be stored on disk as 
    private final byte id;

    protected DataType(_type t, byte id) {
      this.val = t;
      this.id = id;
    }
    
    public static DataType tuple(TupleDefinition td) {
        return new TupleDataType(td);
    }
    
    
    public static DataType list(TupleDefinition td) {
        return new ListDataType(td);
    }
    
    public static DataType protobuf(String className) {
        return new ProtobufDataType(className);
    }
    
    /**
     * this is the inverse of {@link #name()}
     * @param name
     * @return the DataType corresponding to the name
     * @throws IllegalArgumentException thrown in case the name is invalid
     */
    public static DataType byName(String name) throws IllegalArgumentException {
        if(name==null) {
            throw new NullPointerException();
        }
        if("BYTE".equals(name)){
            return BYTE;
        }
        if("SHORT".equals(name)){
            return SHORT;
        }
        if("INT".equals(name)){
            return INT;
        }
        if("DOUBLE".equals(name)){
            return DOUBLE;
        }
        
        if("STRING".equals(name)){
            return STRING;
        }
        if("BINARY".equals(name)){
            return BINARY;
        }
        if("BOOLEAN".equals(name)){
            return BOOLEAN;
        }
        if("TIMESTAMP".equals(name)){
            return TIMESTAMP;
        }
        if("ENUM".equals(name)) {
            return ENUM;
        }
        if("LONG".equals(name)) {
            return LONG;
        }
        
        if("PARAMETER_VALUE".equals(name)){
            return PARAMETER_VALUE;
        }
        
        if(name.toUpperCase().startsWith("PROTOBUF(")) {
            return protobuf(name.substring(9, name.length()-1));
        }
        
        throw new IllegalArgumentException("invalid or unsupported DataType '"+name+"'");
    }
    
    /*returns Int, Short, etc suitable to use as getInt(), getShort() on the Object*/
    public static String capitalized(String s) {
      String t=s.toString();
      return t.substring(0,1).toUpperCase()+t.substring(1).toLowerCase();
    }

    public String javaType() {
      switch(val) {
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
      default:
          throw new IllegalStateException("no java type available for "+this);
      }
    }

    public String primitiveJavaType() {
        switch(val) {
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
            throw new IllegalStateException("no primitive java type for "+val);
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
     * @return for basic types returns the enum name
     *         for PROTOBUF returns PROTOBUF(className)
     */
    public String name() {
        return val.name();
    }
    
  
    public static DataType typeOf(Object v) {
        if(v instanceof Boolean) {
            return BOOLEAN;
        } else if(v instanceof Byte) {
            return BYTE;
        } else if(v instanceof Short) {
            return SHORT;
        } else if(v instanceof Integer) {
            return INT;
        } else if(v instanceof Double) {
            return DOUBLE;
        } else if(v instanceof Long) {
            return LONG;
        } else if(v instanceof String) {
            return STRING;
        } else if(v instanceof byte[]) {
            return BINARY;
        } else if(v instanceof ParameterValue) {
            return PARAMETER_VALUE;
        } else {
            throw new IllegalArgumentException("invalid or unsupported object of type of "+v.getClass());
        }
    }

    public static int compare(Object v1, Object v2) {
        if(v1 instanceof Boolean) {
            return ((Boolean)v1).compareTo((Boolean)v2);
        } else if(v1 instanceof Byte) {
            return ((Byte)v1).compareTo((Byte)v2);
        } else if(v1 instanceof Short) {
            return ((Short)v1).compareTo((Short)v2);
        } else if(v1 instanceof Integer) {
            return ((Integer)v1).compareTo((Integer)v2);
        } else if(v1 instanceof Double) {
            return ((Double)v1).compareTo((Double)v2);
        } else if(v1 instanceof Long) {
            return ((Long)v1).compareTo((Long)v2);
        }  else if(v1 instanceof String) {
            return ((String)v1).compareTo((String)v2);
        } else {
            throw new IllegalArgumentException("cannot compare objects of type "+v1.getClass());
        }
    }

    /**
     * Performs casting of v from type1 to type2
     * 
     * @param sourceType
     * @param targetType
     * @param v
     * @return the casted object (can be v if no casting is performed)
     * @throws IllegalArgumentException
     */
    public static Object castAs(DataType sourceType, DataType targetType, Object v) throws IllegalArgumentException {
        if(sourceType.equals(targetType)) {
            return v;
        }
        
        if(v instanceof Number) {
            Number n = (Number)v;
            switch(targetType.val) {
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
            case STRING:
            case ENUM:
                return n.toString();
            }
        } else if(v instanceof String) {
            String s = (String)v;
            switch(targetType.val) {
            case BYTE:
                return Byte.decode(s);
            case DOUBLE:
                return Double.valueOf(s);
            case SHORT:
                return Short.decode(s);
            case INT:
                return Integer.decode(s);
            case LONG:
            case TIMESTAMP:
                return Long.decode(s);
            case STRING:
            case ENUM:
                return s;
            }
        }
        throw new IllegalArgumentException("Cannot convert '"+v+"' from "+sourceType+" into "+targetType);
    }
    /**
     * Performs casting:
     *  numbers to numbers
     *  numbers to string
     *  string to numbers
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
        switch(dt.val) {
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
        if(dt1==dt2) {
            return true;
        }
        
        if(isNumber(dt1) && isNumber(dt2)){
            return true;
        }
        
        if(dt1.val == _type.STRING || dt1.val == _type.ENUM) {
            return dt2.val==_type.STRING || dt2.val==_type.ENUM;
        }
        return false;
    }

    public byte getTypeId() {
        return id;
    }
}