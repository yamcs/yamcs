package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.HistogramDb;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.rocksdb.RdbPartitionManager;
import org.yamcs.yarch.YarchException;

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
    static Logger log=LoggerFactory.getLogger(RdbStorageEngine.class.getName());

    public RdbStorageEngine(YarchDatabase ydb) {
	this.ydb = ydb;
	instances.put(ydb, this);
    }


    @Override
    public void loadTable(TableDefinition tbl) throws YarchException {
	if(tbl.hasPartitioning()) {
	    RdbPartitionManager pm = new RdbPartitionManager(ydb, tbl);
	    pm.readPartitionsFromDisk();
	    partitionManagers.put(tbl, pm);
	}
    }

    @Override
    public void dropTable(TableDefinition tbl) throws YarchException {
	RdbPartitionManager pm = partitionManagers.remove(tbl);

	for(Partition p:pm.getPartitions()) {
	    RdbPartition rdbp = (RdbPartition)p;
	    File f=new File(tbl.getDataDir()+"/"+rdbp.dir);
	    RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
	    rdbFactory.closeIfOpen(f.getAbsolutePath());
	    try {
		if(f.exists()) {
		    log.debug("Recursively removing {}", f);
		    FileUtils.deleteRecursively(f.toPath());
		}
	    } catch (IOException e) {
		throw new YarchException("Cannot remove "+f, e);
	    }
	}

    }

    @Override
    public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException {
	try {
	    return new RdbTableWriter(ydb, tbl, insertMode, partitionManagers.get(tbl));
	} catch (IOException e) {
	    throw new YarchException("Failed to create writer", e);
	} 
    }

    @Override
    public AbstractStream newTableReaderStream(TableDefinition tbl) {
	if(!partitionManagers.containsKey(tbl)) {
	    throw new IllegalArgumentException("Do not have a partition manager for this table");
	}
	return new RdbTableReaderStream(ydb, tbl, partitionManagers.get(tbl));
    }

    @Override
    public void createTable(TableDefinition def) {		
	RdbPartitionManager pm = new RdbPartitionManager(ydb, def);
	partitionManagers.put(def, pm);
    }

    public static RdbStorageEngine getInstance(YarchDatabase ydb) {
	return instances.get(ydb);
    }

    public RdbPartitionManager getPartitionManager(TableDefinition tdef) {      
	return partitionManagers.get(tdef);
    }


    @Override
    public HistogramDb getHistogramDb(TableDefinition tbl) throws YarchException {		
	try {
	    return RdbHistogramDb.getInstance(ydb, tbl);
	} catch (IOException e) {
	    throw new YarchException(e);
	}
    }
}
