package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchException;

/**
 * Produces tuples as result of:
 * <p>
 * <code>
 * INSERT INTO table(...) VALUES(...)
 *</code>
 */
public class InsertValuesExpression implements StreamExpression {
    TupleDefinition tdef;
    List<String> columns;
    List<SelectItem> selectList;
    static final TupleDefinition EMPTY_DEF = new TupleDefinition();
    static final Tuple EMPTY_TPL = new Tuple(EMPTY_DEF);

    public InsertValuesExpression(List<String> columns, List<SelectItem> selectList) throws StreamSqlException {
        if (columns.size() != selectList.size()) {
            throw new GenericStreamSqlException("Values list size does not match the declared columns");
        }
        boolean hasStars = selectList.stream().filter(item -> item.isStar()).findAny().isPresent();
        if (hasStars) {
            throw new GenericStreamSqlException("Cannot use star (*) in INSERT list");
        }

        this.columns = columns;
        this.selectList = selectList;
    }

    @Override
    public void bind(ExecutionContext c) throws StreamSqlException {

        tdef = new TupleDefinition();

        for (int i = 0; i < selectList.size(); i++) {
            SelectItem item = selectList.get(i);
            item.expr.bind(EMPTY_DEF);
            tdef.addColumn(columns.get(i), item.expr.getType());
        }
    }

    @Override
    public Stream execute(ExecutionContext c) throws StreamSqlException, YarchException {
        
        List<Object> values = new ArrayList<>();
        for(SelectItem item: selectList) {
            Object o =  item.expr.compile().getValue(EMPTY_TPL);
            values.add(o);
        }

        return new Stream(c.getDb(), "InsertValuesExpression" + this.hashCode(), tdef) {
            @Override
            public void doStart() {
                Tuple t = new Tuple(tdef, values);
                emitTuple(t);
                close();
            }
            @Override
            protected void doClose() {
            }
        };
    }

    @Override
    public TupleDefinition getOutputDefinition() {
        return tdef;
    }

    @Override
    public boolean isFinite() {
        return true;
    }

}
