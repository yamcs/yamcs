package org.yamcs.yarch.streamsql;

import java.util.Set;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FieldReturnCompiledExpression;
import org.yamcs.yarch.ProtobufDataType;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;

/**
 * Represents a column in a query, for example x and y below: select x from table where y &gt; 0
 * 
 * @author nm
 *
 */
public class ColumnExpression extends Expression {
    String name;

    // after binding
    ColumnDefinition cdef;

    // for protobuf columns
    String className;
    String fieldName;
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
        ColumnDefinition inputCdef = inputDef.getColumn(name);
        if (inputCdef == null) {
            int idx = name.indexOf(".");
            if (idx != -1) { // protobuf column
                className = name.substring(0, idx);
                fieldName = name.substring(idx + 1);
                bindProtobuf(className, fieldName);
            } else {
                throw new GenericStreamSqlException("'" + name + "' is not an input column");
            }
            cdef = new ColumnDefinition(colName, type);
        } else {
            type = inputCdef.getType();
            if (name.equals(colName)) {
                cdef = inputCdef;
            } else {
                cdef = new ColumnDefinition(colName, type);
            }
        }
    }

    private void bindProtobuf(String className, String fieldName) throws GenericStreamSqlException {
        cdef = inputDef.getColumn(className);
        if (cdef == null) {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }

        DataType dt = cdef.getType();
        if (dt instanceof ProtobufDataType) {
            ProtobufDataType pdt = (ProtobufDataType) dt;
            Descriptor d = pdt.getDescriptor();
            fieldDescriptor = d.findFieldByName(fieldName);
            if (fieldDescriptor == null) {
                throw new GenericStreamSqlException("'" + name + "' is not an input column");
            }
            ;
            type = getType(fieldDescriptor.getType());
        } else {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }
    }

    private DataType getType(Type type) throws GenericStreamSqlException {
        switch (type) {
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
        default:
            throw new GenericStreamSqlException("Cannot use protobuf fields of type '" + type + "' in sql expressions");
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        String sname = sanitizeName(name);
        if (fieldName == null) {
            code.append("col" + sname);
        } else {
            String varName = "col" + className;
            String hasFunction = varName + ".has" + capitalizeFirstLetter(fieldName) + "()";
            String getFunction = varName + ".get" + capitalizeFirstLetter(fieldName) + "()";
            if (fieldDescriptor.getType() == Type.ENUM) {
                getFunction += ".name()";
            }
            code.append("((" + varName + " != null && " + hasFunction + ")\n");
            code.append("\t\t\t? " + getFunction + "\n");
            code.append("\t\t\t: null)");
        }
    }

    @Override
    public void collectRequiredInputs(Set<ColumnDefinition> inputs) {
        if (className == null) {
            inputs.add(inputDef.getColumn(colName));
        } else {
            inputs.add(inputDef.getColumn(className));
        }
    }

    @Override
    public CompiledExpression compile() throws StreamSqlException {
        if (className == null) {
            return new FieldReturnCompiledExpression(name, cdef);
        } else {
            return super.compile();
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public static String capitalizeFirstLetter(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }
}
