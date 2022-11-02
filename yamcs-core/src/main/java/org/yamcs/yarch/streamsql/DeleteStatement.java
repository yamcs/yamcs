package org.yamcs.yarch.streamsql;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
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
            TableWalkerBuilder twb = new TableWalkerBuilder(context, tblDef);
            if (whereClause != null) {
                whereClause.addFilter(twb);
            }
            TableWalker tblIt = twb.build();
           /* TODO: add  back bulk delete
            if (whereClause == null && limit < 0) {
                bulkDelete = true;
                tblIt.bulkDelete();
            } else {*/
            
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

        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        Tuple tuple;
        if (bulkDelete) {
            tuple = new Tuple(TDEF, new Object[] { 0l, -1l });
            consumer.accept(tuple);
        } else {
            tuple = new Tuple(TDEF, new Object[] { inspected.get(), deleted.get() });
            consumer.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}

/*
 * 
 *     public void bulkDelete() {
        running = true;
        Iterator<PartitionManager.Interval> partitionIterator = getIntervalIterator();
        try {
            while (isRunning() && partitionIterator.hasNext()) {
                PartitionManager.Interval interval = partitionIterator.next();
                boolean endReached = bulkDeleteFromInterval(interval, range);
                if (endReached) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("got exception ", e);
        } finally {
            close();
        }
    }
    
     @Override
    protected boolean bulkDeleteFromInterval(PartitionManager.Interval partitions,  DbRange tableRange ) {

        // all partitions will have the same database, just use the same one
        RdbPartition p1 = (RdbPartition) partitions.iterator().next();

        YRDB rdb;
        rdb = tablespace.getRdb(p1.dir, false);

        try (FlushOptions flushOptions = new FlushOptions()) {
            for (Partition p : partitions) {
                RdbPartition rp = (RdbPartition) p;
                DbRange dbRange = getDeleteDbRange(rp.tbsIndex, tableRange);
                rdb.getDb().deleteRange(dbRange.rangeStart, dbRange.rangeEnd);
            }

            rdb.getDb().flush(flushOptions);

        } catch (RocksDBException e) {
            throw new YarchException(e);
        } finally {
            tablespace.dispose(rdb);
        }
        return false;
    }
*/
