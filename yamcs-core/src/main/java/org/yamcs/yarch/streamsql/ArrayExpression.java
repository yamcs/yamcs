package org.yamcs.yarch.streamsql;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.yarch.DataType;

public class ArrayExpression extends Expression {

    public ArrayExpression(List<Expression> children) throws GenericStreamSqlException {
        super(children.toArray(new Expression[0]));

        // we don't support empty arrays because we don't know what type they are
        if (children.size() == 0) {
            throw new GenericStreamSqlException("Empty arrays not supported");
        }
    }
    


    @Override
    protected void doBind() throws StreamSqlException {
        DataType chidlType = children[0].getType();

        for (Expression c : children) {
            if (c.getType().val != chidlType.val) {
                throw new GenericStreamSqlException("Array must have all components of the same type");
            }
        }
        type = DataType.array(chidlType);

        if (Arrays.stream(children).allMatch(c -> c.isConstant())) {
            constantValue = Arrays.stream(children).map(c -> c.getConstantValue()).collect(Collectors.toList());
        }
    }

    @Override
    protected void fillCode_Declarations(StringBuilder code) throws StreamSqlException {
        if (constantValue != null) {
            code.append("\tprivate final java.util.List const_array = java.util.Arrays.asList(");
            boolean first = true;
            for (Expression c : children) {
                if (first) {
                    first = false;
                } else {
                    code.append(", ");
                }
                c.fillCode_getValueReturn(code);
            }
            code.append(");\n");
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if (constantValue != null) {
            code.append("const_array");
        } else {
            code.append("java.util.Arrays.asList(");
            boolean first = true;
            for (Expression c : children) {
                if (first) {
                    first = false;
                } else {
                    code.append(", ");
                }
                c.fillCode_getValueReturn(code);
            }
            code.append(")");
        }

    }

}
