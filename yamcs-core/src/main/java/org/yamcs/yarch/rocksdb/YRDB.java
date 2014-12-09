package org.yamcs.yarch.rocksdb;

import java.util.List;
import java.util.Map;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

/**
 * wrapper around RocksDB that keeps track of column families
 * @author nm
 *
 */
public class YRDB {
	Map<String, ColumnFamilyHandle> columnFamilies;
	RocksDB db;
	
	public YRDB(String dir) throws RocksDBException {
		List<byte[]> cfl = RocksDB.listColumnFamilies(null, dir);
		for(byte[] b: cfl) {
			ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(new String(b));
		}
		
	}
}
