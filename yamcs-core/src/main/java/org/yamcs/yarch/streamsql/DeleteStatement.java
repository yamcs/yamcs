package org.yamcs.yarch.streamsql;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Execute a statement:
 * 
 * <pre>
 * delete from &lt;table&gt; where &lt;cond&gt; limit n
 * </pre>
 * 
 * The query returns a tuple containing the number of inspected and deleted rows.
 * <p>
 * If the removal operation could be performed directly using rocksdb range delete operation, then the number of deleted
 * rows will be returned as -1. This is because we do not know how many rows were in the deleted range.
 * 
 * <p>
 * The delete using roksdb range delete happens when deleting without any condition or when deleting with a condition
 * involving only the primary key. The range delete operation is much faster than the other delete which has to
 * retrieve and inspect row by row.
 * <p>
 * If a limit is specified, the slow row by row deletion is always used.
 * 
 * <p>
 * Note that rocksdb does not remove the data from the disk immediately. The freeing of the space will only happen when
 * a compact operation will be executed on the files which have removed data inside. See
 * <a href="https://github.com/facebook/rocksdb/wiki/Compaction"> Rocksdb Compaction </a> for details.
 * 
 * 
 * @author nm
 *
 */
public class DeleteStatement extends SimpleStreamSqlStatement {
    final String tblName;
    Expression whereClause;
    final long limit;
    CompiledExpression cwhere = null;

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("inspected", DataType.LONG);
        TDEF.addColumn("deleted", DataType.LONG);
    }

    public DeleteStatement(String tableName, Expression whereClause, long limit) {
        this.tblName = tableName;
        this.whereClause = whereClause;
        this.limit = limit;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        TableDefinition tblDef = ydb.getTable(tblName);
        if (tblDef == null) {
            throw new GenericStreamSqlException(String.format("Object %s does not exist or is not a table", tblName));
        }

        if (whereClause != null) {
            whereClause.bind(tblDef.getTupleDefinition());
        }
        boolean bulkDelete = false;
        AtomicLong deleted = new AtomicLong();
        AtomicLong inspected = new AtomicLong();
        try {
            TableWalker tblIt = ydb.getStorageEngine(tblDef).newTableWalker(ydb, tblDef, true, false);

            if (whereClause != null) {
                whereClause = whereClause.addFilter(tblIt);
            }
            if (whereClause == null && limit < 0) {
                bulkDelete = true;
                tblIt.bulkDelete();
            } else {
                if (whereClause != null) {
                    cwhere = whereClause.compile();
                }
                tblIt.walk(new TableVisitor() {
                    @Override
                    public Action visit(byte[] key, byte[] value) {
                        if (cwhere == null) {
                            return ACTION_DELETE;
                        } else {
                            Tuple tuple = tblDef.deserialize(key, value);
                            inspected.incrementAndGet();
                            if ((Boolean) cwhere.getValue(tuple)) {
                                long c = deleted.incrementAndGet();

                                if (limit > 0 && c >= limit) {
                                    return ACTION_DELETE_STOP;
                                } else {
                                    return ACTION_DELETE;
                                }
                            } else {
                                return ACTION_CONTINUE;
                            }
                        }
                    }
                });
            }

        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        Tuple tuple;
        if (bulkDelete) {
            tuple = new Tuple(TDEF, new Object[] { 0, -1 });
            consumer.accept(tuple);
        } else {
            tuple = new Tuple(TDEF, new Object[] { inspected.get(), deleted.get() });
            consumer.accept(tuple);
        }
    }
}
