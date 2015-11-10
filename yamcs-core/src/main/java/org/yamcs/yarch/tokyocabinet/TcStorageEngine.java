package org.yamcs.yarch.tokyocabinet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.archive.TagDb;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.HistogramDb;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
/**
 * Storage engine based on TokyoCabinets
 * Each table partition is mapped to a tcb file. 
 * 
 * 
 * @author nm
 *
 */
public class TcStorageEngine implements StorageEngine {
    Map<TableDefinition, TcPartitionManager> partitionManagers = Collections.synchronizedMap(new HashMap<TableDefinition, TcPartitionManager>());
    final YarchDatabase ydb;

    public TcStorageEngine(YarchDatabase ydb) {
        this.ydb = ydb;
    }

    @Override
    public void loadTable(TableDefinition tbl) throws YarchException {
	if(tbl.hasPartitioning()) {
	    TcPartitionManager pm = new TcPartitionManager(tbl);
	    pm.readPartitions();
	    partitionManagers.put(tbl, pm);
	}
    }

    @Override
    public void dropTable(TableDefinition tbl) throws YarchException {
	TCBFactory tcbFactory = TCBFactory.getInstance();
	TcPartitionManager pm = partitionManagers.get(tbl);

	for(String p:pm.getPartitionFilenames()) {
	    String file=tbl.getDataDir()+"/"+p;
	    File f=new File(file);
	    if(f.exists() && (!f.delete())) throw new YarchException("Cannot remove "+f);
	    tcbFactory.delete(file);
	}
    }

    @Override
    public TableWriter newTableWriter(TableDefinition tbl, InsertMode insertMode) throws YarchException {
	PartitionManager pm = partitionManagers.get(tbl);
	if(pm==null) {
	    throw new RuntimeException("Do not have a PartitionManager for table "+tbl.getName());
	}
	try {
	    return new TcTableWriter(ydb, tbl, insertMode, partitionManagers.get(tbl));
	} catch (FileNotFoundException e) {
	    throw new YarchException("Failed to create writer", e);
	} 
    }

    public TcPartitionManager getPartitionManager(TableDefinition tdef) {      
	return partitionManagers.get(tdef);
    }


    @Override
    public AbstractStream newTableReaderStream(TableDefinition tbl, boolean ascending, boolean follow) {
        TcPartitionManager pm = partitionManagers.get(tbl);
        if(pm==null) {
            throw new RuntimeException("Do not have a PartitionManager for table '"+tbl.getName()+"'");
        }
        if(!ascending) {
            throw new UnsupportedOperationException("Descending sort not support for TokyoCabinets tables. Consider migrating to RocksDB");
        }
        if(!follow) {
            throw new UnsupportedOperationException("NOFOLLOW not support for TokyoCabinets tables. Consider migrating to RocksDB");
        }
        return new TcTableReaderStream(ydb, tbl, pm, ascending, follow);
    }


    @Override
    public void createTable(TableDefinition tbl) {
	TcPartitionManager pm = new TcPartitionManager(tbl);
	partitionManagers.put(tbl, pm);
    }


    @Override
    public HistogramDb getHistogramDb(TableDefinition tbl) {		
	return TcHistogramDb.getInstance(ydb, tbl);
    }

    @Override
    public TagDb getTagDb() throws YarchException {
        try {
            return TcTagDb.getInstance(ydb.getName(), false);
        } catch (IOException e) {
            throw new YarchException(e);
        }
    }
}
