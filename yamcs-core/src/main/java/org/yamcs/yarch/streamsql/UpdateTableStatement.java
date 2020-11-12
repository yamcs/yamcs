package org.yamcs.yarch.streamsql;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.yamcs.logging.Log;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
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
    

    public UpdateTableStatement(String tableName, List<UpdateItem> updateList, Expression whereClause, long limit) {
        this.tableName = tableName;
        this.updateList = updateList;
        this.whereClause = whereClause;
        this.limit = limit;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        checkAndCompile(context);

        YarchDatabaseInstance ydb = context.getDb();
        AtomicLong updated = new AtomicLong();
        AtomicLong inspected = new AtomicLong();
        try {
            TableWalker tblIt = ydb.getStorageEngine(tableDefinition).newTableWalker(ydb, tableDefinition, true, false);

            if (whereClause != null) {
                whereClause = whereClause.addFilter(tblIt);
                if (whereClause != null) {
                    cwhere = whereClause.compile();
                }
            }

            tblIt.walk(new TableVisitor() {
                @Override
                public Action visit(byte[] key, byte[] value) {
                    inspected.getAndIncrement();
                    Tuple tuple = tableDefinition.deserialize(key, value);
                    if (cwhere != null && !((Boolean) cwhere.getValue(tuple))) {
                        return ACTION_CONTINUE;
                    }

                    for (UpdateItem item : updateList) {
                        Object colv;
                        if (item.constantValue != null) {
                            colv = item.constantValue;
                        } else {
                            colv = DataType.castAs(item.type, item.compiledExpr.getValue(tuple));
                        }
                        if(tuple.hasColumn(item.colName)) {
                            tuple.setColumn(item.colName, colv);
                        } else {
                            tuple.addColumn(item.colName, item.type, colv);
                        }
                    }
                    long c = updated.incrementAndGet();
                    boolean stop = (limit > 0 && c >= limit);
                    byte[] svalue;
                    try {
                        svalue = tableDefinition.serializeValue(tuple);
                    } catch (YarchException e) {
                        log.error("Error serializing value", e);
                        return ACTION_STOP;
                    }

                    return Action.updateAction(svalue, stop);
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
                if (tableDefinition.hasKey(ui.colName)) {
                    throw new NotSupportedException("Column '" + ui.colName + "' is part of the primary key");
                }

                if (!DataType.compatible(ui.value.getType(), cd.getType())) {
                    throw new IncompatibilityException(
                            "Cannot assign values of type " + ui.value.getType() + " to column '"
                                    + cd.getName() + "' of type " + cd.getType());
                }

                ui.type = cd.getType();
                if (ui.value.isConstant()) {
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
