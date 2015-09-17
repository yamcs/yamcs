package org.yamcs.yarch.streamsql;

import java.util.ArrayList;

import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class CreateTableStatement extends StreamSqlStatement {

    boolean ifNotExists;
    String tableName;
    TupleDefinition tupleDefinition;
    ArrayList<String> primaryKey;
    ArrayList<String> histoColumns;
    PartitioningSpec partitioningSpec;
    String dataDir;
    String engine;

    private boolean compressed=false;

    public CreateTableStatement(boolean ifNotExists, String tableName, TupleDefinition tupleDefinition, ArrayList<String> primaryKey) {
        this.ifNotExists=ifNotExists;
        this.tableName=tableName;
        this.tupleDefinition=tupleDefinition;
        this.primaryKey=primaryKey;
    }



    public void setDataDir(String dataDir) {
        this.dataDir=dataDir;

    }

    public void setPartitioning(PartitioningSpec pspec) {
        this.partitioningSpec=pspec;
    }
    public void setCompressed(boolean c) {
        this.compressed=c;
    }

    public void addHistogramColumn(String columnName) {
        if(histoColumns==null) {
            histoColumns=new ArrayList<String>();
        }
        histoColumns.add(columnName);
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase ydb=YarchDatabase.getInstance(c.getDbName());
        synchronized(ydb) {
            TableDefinition tableDefinition=new TableDefinition(tableName, tupleDefinition, primaryKey);
            tableDefinition.validate();

            if(dataDir!=null) {
                tableDefinition.setDataDir(dataDir);
                tableDefinition.setCustomDataDir(true);
            } else {
                tableDefinition.setDataDir(ydb.getRoot());
                tableDefinition.setCustomDataDir(false);
            }
            if(engine!=null) {
                tableDefinition.setStorageEngineName(engine);
            } else {
                tableDefinition.setStorageEngineName(ydb.getDefaultStorageEngineName());
            }

            tableDefinition.setCompressed(compressed);
            if(partitioningSpec!=null) {
                tableDefinition.setPartitioningSpec(partitioningSpec);
            } else {
                tableDefinition.setPartitioningSpec(PartitioningSpec.noneSpec());
            }
            if(histoColumns!=null) {
                tableDefinition.setHistogramColumns(histoColumns);
            }

            try {
                if(!ifNotExists || ydb.getTable(tableName)==null) {
                    ydb.createTable(tableDefinition);
                }
                return new StreamSqlResult();
            } catch(YarchException e) {
                throw new GenericStreamSqlException("Cannot create table: "+e.getMessage());
            }
        }
    }

    public void setEngine(String engine) {
        this.engine=engine;		
    }
}
