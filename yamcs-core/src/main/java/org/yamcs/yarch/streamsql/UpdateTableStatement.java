package org.yamcs.yarch.streamsql;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.yamcs.logging.Log;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Row;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class UpdateTableStatement extends SimpleStreamSqlStatement {
    final Log log = new Log(UpdateTableStatement.class);
    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("inspected", DataType.LONG);
        TDEF.addColumn("updated", DataType.LONG);
    }

    final String tableName;
    final List<UpdateItem> updateList;
    Expression whereClause;
    long limit;

    CompiledExpression cwhere = null;
    TableDefinition tableDefinition;
    boolean updateKey = false;

    public UpdateTableStatement(String tableName, List<UpdateItem> updateList, Expression whereClause, long limit) {
        this.tableName = tableName;
        this.updateList = updateList;
        this.whereClause = whereClause;
        this.limit = limit;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        checkAndCompile(context);

        AtomicLong updated = new AtomicLong();
        AtomicLong inspected = new AtomicLong();
        try {
            TableWalkerBuilder twb = new TableWalkerBuilder(context, tableDefinition);
            if (whereClause != null) {
                whereClause.addFilter(twb);
            }
            TableWalker tblWalker = twb.build();
            tblWalker.setBatchUpdates(true);

            if (whereClause != null) {
                cwhere = whereClause.compile();
            }

            tblWalker.walk((key, value) -> {
                inspected.getAndIncrement();
                Tuple tuple = tableDefinition.deserialize(key, value);
                if (cwhere != null && !((Boolean) cwhere.getValue(tuple))) {
                    return TableVisitor.ACTION_CONTINUE;
                }

                for (UpdateItem item : updateList) {
                    Object colv;
                    if (item.compiledExpr != null) {
                        colv = DataType.castAs(item.type, item.compiledExpr.getValue(tuple));
                    } else {
                        colv = item.constantValue;
                    }
                    if (tuple.hasColumn(item.colName)) {
                        tuple.setColumn(item.colName, colv);
                    } else {
                        tuple.addColumn(item.colName, item.type, colv);
                    }
                }
                long c = updated.incrementAndGet();
                boolean stop = (limit > 0 && c >= limit);
                byte[] updatedValue;
                Row row = null;
                try {
                    if (updateKey) {
                        row = tableDefinition.generateRow(tuple);
                        updatedValue = tableDefinition.serializeValue(tuple, row);
                    } else {
                        updatedValue = tableDefinition.serializeValue(tuple, null);
                    }
                } catch (YarchException e) {
                    log.error("Error serializing value", e);
                    return TableVisitor.ACTION_STOP;
                }
                if (row != null) {
                    return TableVisitor.Action.updateAction(row.getKey(), updatedValue, stop);
                } else {
                    return TableVisitor.Action.updateAction(updatedValue, stop);
                }
            });

        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        Tuple tuple = new Tuple(TDEF, new Object[] { inspected.get(), updated.get() });
        consumer.accept(tuple);

    }

    void checkAndCompile(ExecutionContext context) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        tableDefinition = ydb.getTable(tableName);
        if (tableDefinition == null) {
            throw new ResourceNotFoundException("Table '" + tableName + "' does not exist");
        }

        for (UpdateItem ui : updateList) {
            ui.value.bind(tableDefinition.getTupleDefinition());

            ColumnDefinition cd = tableDefinition.getColumnDefinition(ui.colName);
            if (cd == null) {
                ui.type = ui.value.getType();
                if (ui.value.isConstant()) {
                    ui.constantValue = ui.value.getConstantValue();
                } else {
                    ui.compiledExpr = ui.value.compile();
                }
            } else {
                if (tableDefinition.isPartitionedBy(ui.colName)) {
                    throw new NotImplementedException("Cannot update partition column");
                }
                if (tableDefinition.hasKey(ui.colName)) {
                    updateKey = true;
                }


                boolean isNull = ui.value instanceof NullExpression
                        || (ui.value instanceof ArgumentExpression
                                && ((ArgumentExpression) ui.value).getConstantValue() == null);

                if (!isNull && !DataType.compatible(ui.value.getType(), cd.getType())) {
                    throw new IncompatibilityException(
                            "Cannot assign values of type " + ui.value.getType() + " to column '"
                                    + cd.getName() + "' of type " + cd.getType());
                }

                ui.type = cd.getType();
                if (isNull) {
                    ui.constantValue = null;
                } else if (ui.value.isConstant()) {
                    ui.constantValue = DataType.castAs(cd.getType(), ui.value.getConstantValue());
                } else {
                    ui.compiledExpr = ui.value.compile();
                }
            }
        }

        if (whereClause != null) {
            whereClause.bind(tableDefinition.getTupleDefinition());
            if (whereClause.getType() != DataType.BOOLEAN) {
                throw new GenericStreamSqlException("Invalid where clause, should return a boolean");
            }
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }

    static public class UpdateItem {
        final String colName;
        final Expression value;
        Object constantValue = null;
        CompiledExpression compiledExpr = null;
        DataType type;

        public UpdateItem(String colName, Expression value) {
            this.colName = colName;
            this.value = value;
        }

        @Override
        public String toString() {
            return "UpdateItem [colName=" + colName + ", value=" + value + "]";
        }
    }
}
