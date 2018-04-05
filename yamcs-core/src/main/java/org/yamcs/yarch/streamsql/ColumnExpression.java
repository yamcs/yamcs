package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FieldReturnCompiledExpression;
import org.yamcs.yarch.ProtobufDataType;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;

/**
 * Represents a column in a query, for example x and y below:
 * select x from table where y &gt; 0
 * 
 * @author nm
 *
 */
public class ColumnExpression extends Expression {
    String name;

    // after binding
    ColumnDefinition cdef;

    //for protobuf columns
    FieldDescriptor fieldDescriptor;
    
    ColumnExpression(String name) throws ParseException {
        super(null);
        this.name = name;
        this.colName = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void doBind() throws StreamSqlException {
        cdef = inputDef.getColumn(name);
        if (cdef == null) {
            int idx = name.indexOf(".");
            if(idx!=-1) { //protobuf column
                checkProtobuf(name.substring(0, idx), name.substring(idx+1));
            }       
        } else {
            type = cdef.getType();    
        }

        if(cdef==null) {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }
    }

    private void checkProtobuf (String className, String fieldName) throws GenericStreamSqlException {
        cdef = inputDef.getColumn(className);
        if(cdef==null) {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }

        DataType dt = cdef.getType();
        if(dt instanceof ProtobufDataType) {
            ProtobufDataType pdt = (ProtobufDataType) dt;
            Descriptor d = pdt.getDescriptor();
            fieldDescriptor = d.findFieldByName(fieldName);
            if(fieldDescriptor==null) {
                throw new GenericStreamSqlException("'" + name + "' is not an input column");
            };
            type = getType(fieldDescriptor.getType());
        } else {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }
    }

    private DataType getType(Type type) throws GenericStreamSqlException {
        switch(type) {
        case BOOL:
            return DataType.BOOLEAN;
        case BYTES:
            return DataType.BINARY;
        case DOUBLE:
            return DataType.DOUBLE;
        case FLOAT:
            return DataType.DOUBLE;
        
        case FIXED32:
        case INT32:
        case SINT32:
        case SFIXED32:
        case UINT32:
            return DataType.INT;
        case FIXED64:
        case SFIXED64:
        case INT64:
        case UINT64:
        case SINT64:
            return DataType.LONG;
        case STRING:
        case ENUM:
            return DataType.STRING;
        }
        throw new GenericStreamSqlException("Cannot use protobuf fields of type '"+type+"' in sql expressions") ;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(fieldDescriptor==null) {
            code.append("col" + name);
        } else {
            code.append("col" + cdef.getName()+".get"+capitalizeFirstLetter(fieldDescriptor.getName())+"()");
            if(fieldDescriptor.getType()==Type.ENUM) {
                code.append(".name()");
            }
        }
    }

    @Override
    public CompiledExpression compile() throws StreamSqlException {
        return new FieldReturnCompiledExpression(name, cdef);
    }

    @Override
    public String toString() {
        return name;
    }
    
    
    private String capitalizeFirstLetter(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }
}
