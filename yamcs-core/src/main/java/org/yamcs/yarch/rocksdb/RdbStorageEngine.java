package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.rocksdb.RdbPartitionManager;
import org.yamcs.yarch.YarchException;

import com.google.common.io.Files;

/**
 * Storage Engine based on RocksDB.
 * 
 * Tables are mapped to multiple RocksDB databases - one for each time based partition.
 * Value based partitions are mapped to column families.
 * 
 * 
 */
public class RdbStorageEngine implements StorageEngine {
	Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<TableDefinition, RdbPartitionManager>();
	final YarchDatabase ydb;
	static Map<YarchDatabase, RdbStorageEngine> instances = new HashMap<YarchDatabase, RdbStorageEngine>();
	static {
		 RocksDB.loadLibrary();
	}
	
    public RdbStorageEngine(YarchDatabase ydb) {
        this.ydb = ydb;
        instances.put(ydb, this);
    }
    
    
	@Override
	public void loadTable(TableDefinition tbl) throws YarchException {
		if(tbl.hasPartitioning()) {
			RdbPartitionManager pm = new RdbPartitionManager(tbl);
			pm.readPartitions();
			partitionManagers.put(tbl, pm);
		}
	}

	@Override
	public void dropTable(TableDefinition tbl) throws YarchException {
		RdbPartitionManager pm = partitionManagers.get(tbl);

		for(String p:pm.getPartitionDirectories()) {
			File f=new File(tbl.getDataDir()+"/"+p);
			try {
				Files.deleteRecursively(f);
			} catch (IOException e) {
				throw new YarchException("Cannot remove "+f, e);
			}
		}
	}

	@Override
	public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException {
		try {
			return new RdbTableWriter(ydb, tbl, insertMode, partitionManagers.get(tbl));
		} catch (FileNotFoundException e) {
			throw new YarchException("Failed to create writer", e);
		} 
	}



	@Override
	public AbstractStream newTableReaderStream(TableDefinition tbl) {
		return new RdbTableReaderStream(ydb, tbl, partitionManagers.get(tbl));
	}

	@Override
	public void createTable(TableDefinition def) {

	}


	public static RdbStorageEngine getInstance(YarchDatabase ydb) {
		return instances.get(ydb);
	}

}
