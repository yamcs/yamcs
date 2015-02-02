package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

/**
 * wrapper around RocksDB that keeps track of column families
 * @author nm
 *
 */
public class YRDB {
	Map<byte[], ColumnFamilyHandle> columnFamilies=new HashMap<byte[], ColumnFamilyHandle>();
	RocksDB db;
	
	public YRDB(String dir) throws RocksDBException {
		List<byte[]> cfl = RocksDB.listColumnFamilies(null, dir);
		List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());		
		for(byte[] b: cfl) {
			cfdList.add(new ColumnFamilyDescriptor(b));
		}
		List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
		db = RocksDB.open(dir,cfdList, cfhList);
		
		for(int i=0;i<cfl.size();i++) {
			columnFamilies.put(cfl.get(i), cfhList.get(i));
		}
	}

	public void close() {
		db.close();		
	}

	public void flush(FlushOptions flushOptions) throws RocksDBException {
		db.flush(flushOptions);		
	}
	
	List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList) throws RocksDBException {
		return db.newIterators(cfhList);
	}
	
	
	public synchronized ColumnFamilyHandle getColumnFamilyHandle(byte[] cfb) {
		return columnFamilies.get(cfb);
	}

	public byte[] get(ColumnFamilyHandle cfh, byte[] k) throws RocksDBException {
		return db.get(cfh, k);
	}

	public synchronized ColumnFamilyHandle createColumnFamily(byte[] cfb) throws RocksDBException {
		ColumnFamilyDescriptor cfd= new ColumnFamilyDescriptor(cfb);
		ColumnFamilyHandle cfh = db.createColumnFamily(cfd);
		columnFamilies.put(cfb, cfh);
		return cfh;
	}

	public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {
		db.put(cfh, k, v);
	}
}
