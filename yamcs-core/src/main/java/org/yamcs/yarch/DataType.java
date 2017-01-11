/**
 * 
 */
package org.yamcs.yarch;

/**
 * Types supported by yarch. Currently TUPLE and LIST do now work well.
 * ENUM is just like String, except that when it's stored on disk a two bytes integer value from a map is stored instead of the String.
 * (maximum allowed version is 2^16 (which is anyway too big considering that the map is stored as serialized yaml file)
 * 
 * PROTOBUF is a Google Protocol Buffer message
 * @author nm
 *
 */
public class DataType {
    private static final long serialVersionUID = 201101181144L;

    
    public enum _type {BYTE, SHORT, INT, DOUBLE, TIMESTAMP, STRING, BINARY, BOOLEAN, ENUM, PROTOBUF, TUPLE, LIST};
    final public _type val;
    
    private TupleDefinition td=null;//for TUPLE and LIST
    private String className=null; //for PROTOBUF
    
    static public final DataType BYTE = new DataType(_type.BYTE);
    static public final DataType SHORT = new DataType(_type.SHORT);
    static public final DataType INT = new DataType(_type.INT);
    static public final DataType DOUBLE = new DataType(_type.DOUBLE);
    static public final DataType STRING = new DataType(_type.STRING);
    static public final DataType BINARY = new DataType(_type.BINARY);
    static public final DataType BOOLEAN = new DataType(_type.BOOLEAN);
    static public final DataType TIMESTAMP = new DataType(_type.TIMESTAMP);
    static public final DataType ENUM = new DataType(_type.ENUM);


    private DataType(_type t) {
      this.val=t;
    }
    
    public static DataType tuple(TupleDefinition td) {
        DataType dt=new DataType(_type.TUPLE);
        dt.td=td;
        return dt;
    }
    
    public static DataType list(TupleDefinition td) {
        DataType dt=new DataType(_type.LIST);
        dt.td=td;
        return dt;
    }
    
    public static DataType protobuf(String className) {
        DataType dt=new DataType(_type.PROTOBUF);
        dt.className=className;
        return dt;
    }
    
    TupleDefinition tupleDefinition() {
      return td;
    }
    
    public static DataType valueOf(String name) {
        if(name==null) return null;
        if("BYTE".equalsIgnoreCase(name))return BYTE;
        if("SHORT".equalsIgnoreCase(name))return SHORT;
        if("INT".equalsIgnoreCase(name))return INT;
        if("DOUBLE".equalsIgnoreCase(name))return DOUBLE;
        if("STRING".equalsIgnoreCase(name))return STRING;
        if("BINARY".equalsIgnoreCase(name))return BINARY;
        if("BOOLEAN".equalsIgnoreCase(name))return BOOLEAN;
        if("TIMESTAMP".equalsIgnoreCase(name))return TIMESTAMP;
        if("ENUM".equalsIgnoreCase(name))return ENUM;
        if(name.toUpperCase().startsWith("PROTOBUF(")) return protobuf(name.substring(9, name.length()-1));
        
        return null;
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
      return capitalized(val.toString());
      case BINARY:
         return "byte[]";
      case TIMESTAMP:
          return "Long";
      case ENUM:
          return "String";
      case INT:
          return "Integer";
      }
      return null;
    }

    public String primitiveJavaType() {
        switch(val) {
        case BOOLEAN:
        case BYTE:
        case DOUBLE:
        case SHORT:
        case INT:
        return val.toString().toLowerCase();
        case TIMESTAMP:
            return "long";
        default:
            throw new IllegalStateException("no primitive java type for "+val);
        }
        
      }

    
    @Override
    public String toString() {
        if(val == _type.TUPLE) {
            return "TUPLE("+td.toString()+")";
        } else if(val == _type.PROTOBUF) {
            return "PROTOBUF("+className+")";
        } else { 
            return val.toString();
        }
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
            return TIMESTAMP;
        } else if(v instanceof String) {
            return STRING;
        } else {
            throw new IllegalArgumentException("invalid object of type of "+v.getClass());
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
     * Performs casting:
     *  numbers to numbers
     *  numbers to string
     *  string to numbers
     * 
     */
    public static Object castAs(DataType type, Object v) throws IllegalArgumentException{
        DataType vdt=typeOf(v);
        if(type.equals(vdt)) return v;
        
        if(v instanceof Number) {
            Number n=(Number)v;

            switch(type.val) {
            case BYTE:
                return n.byteValue();
            case DOUBLE:
                return n.doubleValue();
            case SHORT:
                return n.shortValue();
            case INT:
                return n.intValue();
            case TIMESTAMP:
                return n.longValue();
            case STRING:
            case ENUM:
                return n.toString();
            }
        } else if(v instanceof String) {
            String s=(String)v;
            switch(type.val) {
            case BYTE:
                return Byte.decode(s);
            case DOUBLE:
                return Double.valueOf(s);
            case SHORT:
                return Short.decode(s);
            case INT:
                return Integer.decode(s);
            case TIMESTAMP:
                return Long.decode(s);
            case ENUM:
                return s;
            }
        }
        throw new IllegalArgumentException("Cannot convert '"+v+"' into "+type);
    }

    public static boolean isNumber(DataType dt) {
        switch(dt.val) {
        case BYTE:
        case DOUBLE:
        case INT:
        case SHORT:
        case TIMESTAMP:
            return true;
        default:
          return false;
        }
    }
    
   
    
    public static boolean compatible(DataType dt1, DataType dt2) {
        if(dt1==dt2) return true;
        
        if(isNumber(dt1) && isNumber(dt2)) return true;
        
        
        switch(dt1.val) {
        case STRING:
        case ENUM:
            if(dt2.val==_type.STRING || dt2.val==_type.ENUM) return true;
        default:
            return false;
        }
        
    }
    /**
     * Returns the name of the class (implementing {@link com.google.protobuf.MessageLite})in case of PROTOBUF type
     * @return
     */
    public String getClassName() {
        return className;
    }
}