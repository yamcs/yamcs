package org.yamcs.yarch.streamsql;

import java.util.ArrayList;

import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class CreateTableStatement extends StreamSqlStatement {
	
	String tableName;
	TupleDefinition tupleDefinition;
    ArrayList<String> primaryKey;
    ArrayList<String> histoColumns;
    PartitioningSpec partitioningSpec;;
    String dataDir;
    private boolean compressed=false;
    
	public CreateTableStatement(String tableName, TupleDefinition tupleDefinition, ArrayList<String> primaryKey) {
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
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        synchronized(dict) {
            TableDefinition tableDefinition=new TableDefinition(tableName, tupleDefinition, primaryKey);
            tableDefinition.validate();
            
            if(dataDir!=null) {
                tableDefinition.setDataDir(dataDir);
                tableDefinition.setCustomDataDir(true);
            } else {
                tableDefinition.setDataDir(dict.getRoot());
                tableDefinition.setCustomDataDir(false);
            }
            tableDefinition.setCompressed(compressed);
            if(partitioningSpec!=null) {
                tableDefinition.setPartitioningSpec(partitioningSpec);
            }
            if(histoColumns!=null) {
                tableDefinition.setHistogramColumns(histoColumns);
            }
            try {
                dict.addTable(tableDefinition);
                return new StreamSqlResult();
            } catch(YarchException e) {
                throw new GenericStreamSqlException("Cannot create table: "+e.getMessage());
            }
        }
    }
}
