package org.yamcs.yarch.streamsql;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.janino.SimpleCompiler;
import org.yamcs.yarch.CompiledAggregateExpression;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import org.yamcs.yarch.streamsql.AggregateExpression;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public abstract class CompilableAggregateExpression extends AggregateExpression {
    
    public CompilableAggregateExpression(Expression[] args, boolean star)throws ParseException {
        super(args, star);
    }

    private static AtomicInteger counter=new AtomicInteger();
    
    @Override
    public CompiledAggregateExpression getCompiledAggregate() throws StreamSqlException {
        String className = "AggregateExpression"+counter.incrementAndGet();
        StringBuilder code=new StringBuilder();
        code.append("package org.yamcs.yarch;\n")
        .append("public class "+className+" implements CompiledAggregateExpression {\n");
        aggregateFillCode_Declarations(code);

        code.append("\tpublic void newData(Tuple tuple) {\n");
        aggregateFillCode_newData(code);
        code.append("\t}\n");

        code.append("\tpublic Object getValue() {\n");
        aggregateFillCode_getValue(code);
        code.append("\t}\n");

        code.append("\tpublic void clear() {\n");
        aggregateFillCode_clear(code);
        code.append("\t}\n")
        .append("}");

        try {
            SimpleCompiler compiler=new SimpleCompiler();
            compiler.cook(new StringReader(code.toString()));
            Class cexprClass = compiler.getClassLoader().loadClass("org.yamcs.yarch."+className);
            return (CompiledAggregateExpression) cexprClass.newInstance();
        } catch (Exception e) {
            throw new StreamSqlException(ErrCode.COMPILE_ERROR, e.toString()); 
        }
    }

    protected abstract void aggregateFillCode_clear(StringBuilder code);

    protected abstract void aggregateFillCode_getValue(StringBuilder code);
    protected abstract void aggregateFillCode_newData(StringBuilder code) throws StreamSqlException;

    protected abstract void aggregateFillCode_Declarations(StringBuilder code);  
}
