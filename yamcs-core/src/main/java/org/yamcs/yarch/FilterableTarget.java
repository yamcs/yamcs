package org.yamcs.yarch;

import java.util.Set;

import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;


public interface FilterableTarget {
    /**
     * Tries to add a restriction for the rows to be selected/updated/deleted. This will implement optimisations to avoid scanning the table row by row.
     * <p>
     * Typically it works if the condition refers to the primary key.
     * 
     * @param cexpr
     * @param relOp
     * @param value
     * @throws StreamSqlException
     */
    public void addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException;
    /**
     * same as {@link #addRelOpFilter(ColumnExpression, RelOp, Object)} but adds a restrictions for a set of values resulted from a where x in (a,b,c) condition
     * 
     * @param cexpr
     * @param negation
     * @param values
     * @throws StreamSqlException
     */
    public void addInFilter(ColumnExpression cexpr, boolean negation, Set<Object> values) throws StreamSqlException;
}
