package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledAggregateExpression;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.ConstantValueCompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.SelectStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.WindowProcessor;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import org.yamcs.utils.parser.ParseException;

/**
 * Corresponds to a queries like "select 2*x, x+sum(y+3) from t[...] where x&gt;5 group by x" chain of data/processing
 * (D=Data, P=Processing, agg=aggregate):
 * 
 * <pre>
 * (D1)   inputDef             (x,y)
 * 
 * (P1.1) where filter         (x&gt;5)
 * (P1.2) aggInputList
 * 
 * (D2)   aggInputDef          (x,y+3)
 * 
 * (P2)   aggSelectList
 *
 * (D3)   aggOutputDef         (x,sum(y+3))
 * 
 * (P3)   selectList
 * 
 * (D4)   outputDef            (2*x,x+sum(y+3))
 * </pre>
 * 
 * 
 * P1.2 is performed by the WindowProcessor If there is no aggregate, then P3 follows directly after P1.1
 * 
 * 
 * @author nm
 *
 */
public class SelectExpression implements StreamExpression {
    List<SelectItem> selectList; // a,b+4,c
    TupleSourceExpression tupleSourceExpression; // t,u,v (but only one table/stream supported for the moment)
    Expression whereClause; // x and y
    WindowSpecification windowSpec;// [SIZE 1000 ADVANCE 1000 ON a]
    TupleDefinition inputDef, outputDef, minOutputDef, aggInputDef = null, aggOutputDef = null;
    List<AggregateExpression> aggList = null;
    List<Expression> aggInputList = null;
    boolean ascending = true; // only for table-selects
    boolean follow = false; // only for table-selects
    private boolean selectStar; // in case of select *
    BigDecimal offset;
    BigDecimal limit;

    public void setSelectList(List<SelectItem> selectList) {
        this.selectList = selectList;
    }

    public void setFirstSource(TupleSourceExpression tsrc) {
        this.tupleSourceExpression = tsrc;
    }

    public void setWhereClause(Expression whereClause) {
        this.whereClause = whereClause;
    }

    public void setWindow(WindowSpecification windowSpec) {
        this.windowSpec = windowSpec;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
        tupleSourceExpression.setAscending(ascending);
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
        tupleSourceExpression.setFollow(follow);
    }

    public void setLimit(BigDecimal offset, BigDecimal limit) {
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public void bind(ExecutionContext c) throws StreamSqlException {
        tupleSourceExpression.bind(c);

        inputDef = tupleSourceExpression.getDefinition();
        if (whereClause != null) {
            whereClause.bind(inputDef);
            if (whereClause.getType() != DataType.BOOLEAN) {
                throw new GenericStreamSqlException("Invalid where clause, should return a boolean");
            }
        }
        if (windowSpec != null) {
            windowSpec.bind(inputDef);
        }

        if (selectList.size() == 1 && selectList.get(0) == SelectItem.STAR) {
            selectStar = true;
        }

        /*
         * expand the * if together with something else
         * if(!selectStar) {
         * for(int i=0;i<selectList.size();i++) {
         * if(selectList.get(i)==SelectItem.STAR) {
         * selectList.remove(i);
         * for(ColumnDefinition cd:inputDef.getColumnDefinitions()) {
         * try {
         * selectList.add(i, new SelectItem(new ColumnExpression(cd.getName())));
         * } catch (ParseException e) {
         * e.printStackTrace();
         * }
         * i++;
         * }
         * }
         * }
         * }
         */
        // bind the other expressions
        if (selectStar) {
            outputDef = inputDef;
            minOutputDef = inputDef;
        } else {
            bindAggregates(c);
            outputDef = new TupleDefinition();
            minOutputDef = new TupleDefinition();
            for (SelectItem item : selectList) {
                if (item != SelectItem.STAR) {
                    item.expr.bind((aggOutputDef == null) ? inputDef : aggOutputDef);
                    outputDef.addColumn(item.getName(), item.expr.getType());
                }
            }
        }
    }

    private void bindAggregates(ExecutionContext c) throws StreamSqlException {
        // collect aggregates
        aggList = new ArrayList<>();

        for (SelectItem item : selectList) {
            if (item != SelectItem.STAR) {
                item.expr.collectAggregates(aggList);
            }
        }

        // bind aggregates
        if (!aggList.isEmpty()) {
            if (windowSpec == null) {
                windowSpec = WindowSpecification.INFINITE_WINDOW;
            }

            // build aggInput
            aggInputDef = new TupleDefinition();
            aggInputList = new ArrayList<>();

            boolean hasStars = false;
            for (AggregateExpression aggExpr : aggList) {
                if (aggExpr.star) {
                    hasStars = true;
                    break;
                }
            }

            if (hasStars) {// add all the columns from inputDef
                for (ColumnDefinition cd : inputDef.getColumnDefinitions()) {
                    aggInputDef.addColumn(cd);
                    try {
                        aggInputList.add(new ColumnExpression(cd.getName()));
                    } catch (ParseException e) {
                        throw new StreamSqlException(ErrCode.ERROR, e.toString());
                    }
                }
            } else if (windowSpec.type == WindowSpecification.Type.FIELD) {// add all the fields from the windowSpec
                aggInputDef.addColumn(inputDef.getColumn(windowSpec.field));
                try {
                    aggInputList.add(new ColumnExpression(windowSpec.field));
                } catch (ParseException e) {
                    throw new StreamSqlException(ErrCode.ERROR, e.toString());
                }
            }
            // add all the fields from the groupBy TODO

            boolean hasComputations = false;
            // add all children of the aggregate expressions
            for (AggregateExpression aggExpr : aggList) {
                if (aggExpr.children == null) {
                    continue;
                }
                for (Expression expr : aggExpr.children) {
                    expr.bind(inputDef);
                    if (aggInputDef.getColumn(expr.getColumnName()) == null) {
                        aggInputDef.addColumn(expr.getColumnName(), expr.getType());
                        aggInputList.add(expr);
                    }
                    if (!(expr instanceof ColumnExpression)) {
                        hasComputations = true;
                    }
                }
            }
            if (!hasComputations) {
                aggInputDef = null;
                aggInputList = null;
            }
            aggOutputDef = new TupleDefinition();
            for (AggregateExpression aggExpr : aggList) {
                aggExpr.bindAggregate((aggInputDef == null) ? inputDef : aggInputDef);
                aggOutputDef.addColumn(aggExpr.getColumnName(), aggExpr.getType());
            }
        }

    }

    @Override
    public TupleDefinition getOutputDefinition() {
        return outputDef;
    }

    @Override
    public Stream execute(ExecutionContext c) throws StreamSqlException {
        if (whereClause != null) {
            whereClause.addFilter(tupleSourceExpression);
        }

        Stream stream = tupleSourceExpression.execute(c);
        CompiledExpression cWhereClause = (whereClause == null) ? null : whereClause.compile();

        List<CompiledExpression> caggInputList = null;
        if (aggInputList != null) {
            caggInputList = new ArrayList<>();
            for (Expression expr : aggInputList) {
                caggInputList.add(expr.compile());
            }
        }

        List<CompiledAggregateExpression> caggList = null;
        if (aggOutputDef != null) {
            caggList = new ArrayList<>();
            for (AggregateExpression aexpr : aggList) {
                caggList.add(aexpr.getCompiledAggregate());
            }
        }

        List<CompiledExpression> cselectList = null;
        if (!selectStar) {
            cselectList = new ArrayList<>();
            for (SelectItem item : selectList) {
                if (item != SelectItem.STAR) {
                    Expression expr = item.expr;
                    if (expr.isConstant()) {
                        cselectList.add(new ConstantValueCompiledExpression(expr.getConstantValue(),
                                new ColumnDefinition(expr.getColumnName(), expr.getType())));
                    } else {
                        cselectList.add(item.expr.compile());
                    }
                } else {
                    cselectList.add(SelectStream.STAR);
                }
            }
        }
        WindowProcessor windowProc = null;
        if (windowSpec != null) {
            windowProc = WindowProcessor.getInstance(windowSpec, aggInputDef, caggList, aggOutputDef);
        }

        YarchDatabaseInstance ydb = c.getDb();
        if (cWhereClause != null || caggInputList != null || windowProc != null || cselectList != null) {
            stream = new SelectStream(ydb, stream, cWhereClause,
                    caggInputList, windowProc,
                    cselectList, outputDef, minOutputDef);
        }

        if (limit != null || offset != null) {
            return new LimitedStream(ydb, stream, offset, limit, stream.getDefinition());
        } else {
            return stream;
        }
    }

    @Override
    public boolean isFinite() {
        return tupleSourceExpression.isFinite();
    }
}
