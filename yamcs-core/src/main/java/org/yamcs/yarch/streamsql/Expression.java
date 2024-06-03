package org.yamcs.yarch.streamsql;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public abstract class Expression {
    protected DataType type = null;
    protected Expression[] children;
    protected TupleDefinition inputDef;

    protected boolean hasAggregates;
    protected Object constantValue;
    Object[] args;

    String colName;
    static Logger log = LoggerFactory.getLogger(Expression.class);

    public Expression(Expression[] children) {
        this.children = children;
        hasAggregates = false;
        if (children != null) {
            for (Expression c : children) {
                if (c.isAggregate() || c.hasAggregates) {
                    hasAggregates = true;
                }
            }
        }
        colName = String.format("%s0x%xd", this.getClass().getSimpleName(), this.hashCode());
    }

    // TODO: this is now called from within the parser at query preparation time.
    // when we add PreparedStatements, we should allow passing the args at execution time
    public void setArgs(Object[] args) {
        this.args = args;
        if (children != null) {
            for (Expression c : children) {
                c.setArgs(args);
            }
        }
    }

    protected boolean isAggregate() {
        return false;
    }

    final public boolean isConstant() {
        return constantValue != null;
    }

    /**
     * add a filter to the table if applicable.
     * 
     * @param tableStream
     * @throws StreamSqlException
     */
    public void addFilter(FilterableTarget tableStream) throws StreamSqlException {
        // by default do nothing
    }

    public void collectAggregates(List<AggregateExpression> list) {
        if (isAggregate()) {
            list.add((AggregateExpression) this);
        } else if (children != null) {
            for (Expression c : children) {
                if (c.hasAggregates || c.isAggregate()) {
                    c.collectAggregates(list);
                }
            }
        }
    }

    protected abstract void doBind() throws StreamSqlException;

    public void bind(TupleDefinition inputDef2) throws StreamSqlException {
        this.inputDef = inputDef2;
        if (children != null) {
            for (Expression c : children) {
                c.bind(inputDef);
            }
        }
        doBind();
    }

    public DataType getType() {
        return type;
    }

    protected void fillCode_Declarations(StringBuilder code) throws StreamSqlException {
        if (children != null) {
            for (Expression c : children) {
                c.fillCode_Declarations(code);
            }
        }
        if (constantValue instanceof byte[]) {
            byte[] v = (byte[]) constantValue;
            code.append("\tbyte[] const_").append(getColumnName()).append(" = ")
                    .append("org.yamcs.utils.StringConverter.hexStringToArray(\"")
                    .append(StringConverter.arrayToHexString(v))
                    .append("\");\n");
        }
    }

    protected void fillCode_Constructor(StringBuilder code) throws StreamSqlException {
        if (children != null) {
            for (Expression c : children) {
                c.fillCode_Constructor(code);
            }
        }
    }

    public void collectRequiredInputs(Set<ColumnDefinition> inputs) {
        if (children != null) {
            for (Expression c : children) {
                c.collectRequiredInputs(inputs);
            }
        }
    }

    protected void fillCode_InputDefVars(Collection<ColumnDefinition> inputs, StringBuilder code) {
        for (ColumnDefinition cd : inputs) {
            String javaColIdentifier = "col" + sanitizeName(cd.getName());
            DataType dtype = cd.getType();
            if (dtype.isPrimitiveJavaType()) {
                code.append("\t\t" + dtype.javaType() + " " + javaColIdentifier +
                        " =  (" + dtype.javaType() + ")tuple.getColumn(\""
                        + cd.getName() + "\");\n");
            } else {
                code.append("\t\t" + dtype.javaType() + " " + javaColIdentifier +
                        " =  (" + dtype.javaType() + ")tuple.getColumn(\""
                        + cd.getName() + "\");\n");
            }
        }
    }

    protected void fillCode_getValueBody(StringBuilder code) throws StreamSqlException {
        if (children != null) {
            for (Expression c : children) {
                c.fillCode_getValueBody(code);
            }
        }
    }

    public abstract void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException;

    // TODO: when adding support for PreparedStatements we should remember the result of the compilation
    // and create new instances of that class with different arguments
    // (currently the arguments are passed from the parser.)
    // additional code should be added to verify that the arguments match the expected type
    public CompiledExpression compile() throws StreamSqlException {
        String className = "Expression_generated";
        StringBuilder source = new StringBuilder();
        source.append("package org.yamcs.yarch;\n")
                .append("import org.yamcs.parameter.ParameterValue;\n")
                .append("import org.yamcs.yarch.utils.*;\n")
                .append("import java.util.Objects;\n")
                .append("public class " + className + " implements CompiledExpression {\n")
                .append("\tColumnDefinition cdef;\n")
                .append("\tObject[] __sql_args;\n")
                .append("\n");
        fillCode_Declarations(source);

        source.append("\tpublic " + className + "(ColumnDefinition cdef, Object[] args) {\n")
                .append("\t\tthis.cdef = cdef;\n")
                .append("\t\tthis.__sql_args = args;\n");
        fillCode_Constructor(source);
        source.append("\t}\n");

        source.append("\tpublic Object getValue(Tuple tuple) {\n");
        if (!isConstant()) {
            Set<ColumnDefinition> inputs = new HashSet<>();
            collectRequiredInputs(inputs);

            fillCode_InputDefVars(inputs, source);
        }
        fillCode_getValueBody(source);

        // source.append("Value colid=t.getColumn(\"id\");\n");
        source.append("\n\t\treturn ");
        fillCode_getValueReturn(source);
        source.append(";\n");
        source.append("\t}\n")
                .append("\tpublic ColumnDefinition getDefinition() {\n")
                .append("\t\treturn cdef;\n")
                .append("\t}\n")
                .append("}\n");

        // System.out.println("source: " + source);
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(new StringReader(source.toString()));

            @SuppressWarnings("unchecked")
            Class<CompiledExpression> cexprClass = (Class<CompiledExpression>) compiler.getClassLoader()
                    .loadClass("org.yamcs.yarch." + className);
            Constructor<CompiledExpression> cexprConstructor = cexprClass.getConstructor(ColumnDefinition.class,
                    Object[].class);
            ColumnDefinition cdef = new ColumnDefinition(colName, type);
            return cexprConstructor.newInstance(cdef, args);
        } catch (Exception e) {
            log.warn("Got exception when compiling {} ", source.toString(), e);
            throw new StreamSqlException(ErrCode.COMPILE_ERROR, e.toString());
        }
    }

    /**
     * when the expression behaves like a column expression, this is the column name
     * 
     * @return
     */
    public String getColumnName() {
        return colName;
    }

    public void setColumnName(String name) {
        this.colName = name;
    }

    public Object getConstantValue() {
        return constantValue;
    }

    static String sanitizeName(String s) {
        return s.replace("/", "_").replace("-", "_");
    }
}
