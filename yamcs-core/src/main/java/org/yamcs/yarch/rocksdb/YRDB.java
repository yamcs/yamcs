package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
/**
 * wrapper around RocksDB that keeps track of column families
 * @author nm
 *
 */
public class YRDB {
	Map<Object, ColumnFamilyHandle> columnFamilies=new HashMap<Object, ColumnFamilyHandle>();
	RocksDB db;
	private boolean isClosed = false;
	private final String path;
	private final ColumnFamilySerializer cfSerializer;

	/**
	 * Create or open a new RocksDb.
	 * 
	 * @param dir - if it exists, it has to be a directory
	 * @param valuePartitionDataType - the data type of the value partition if partitioned by value, otherwise null. It is used to convert between byte array (which is how column families are handled) and that value 
	 * @throws RocksDBException
	 * @throws IOException 
	 */
	YRDB(String dir, ColumnFamilySerializer cfSerializer) throws RocksDBException, IOException {
		this.cfSerializer = cfSerializer;
		File f = new File(dir);
		if(f.exists() && !f.isDirectory()) {
			throw new IOException("'"+dir+"' exists and it is not a directory");
		}
		this.path = dir;
		if(f.exists()) {
			List<byte[]> cfl = RocksDB.listColumnFamilies(new Options(), dir);
			if(cfl!=null) {
				List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());
				
				for(byte[] b: cfl) {
					cfdList.add(new ColumnFamilyDescriptor(b));					
				}
				List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
				db = RocksDB.open(dir, cfdList, cfhList);
				for(int i=0;i<cfl.size();i++) {
					byte[] b = cfl.get(i);
					if(!Arrays.equals(b, RocksDB.DEFAULT_COLUMN_FAMILY)) {
						Object value = cfSerializer.byteArrayToObject(b);	
						columnFamilies.put(value, cfhList.get(i));
					}
				}
			} else { //no existing column families
				db=RocksDB.open(dir);
			}
		} else {
			//new DB
			db=RocksDB.open(dir);
		}		
	}

	/**
	 * Close the database. Shall only be done from the RDBFactory
	 */
	void close() {		
		db.close();
		isClosed = true;
	}

	public boolean isOpen() {
		return !isClosed;
	}
	
	public void flush(FlushOptions flushOptions) throws RocksDBException {
		db.flush(flushOptions);		
	}
	
	public List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList) throws RocksDBException {
		ReadOptions ro = new ReadOptions();
		ro.setTailing(true);
		return db.newIterators(cfhList, ro);
	}
	
	
	public RocksIterator newIterator(ColumnFamilyHandle cfh) throws RocksDBException {
		return db.newIterator(cfh);
	}
	
	public synchronized ColumnFamilyHandle getColumnFamilyHandle(Object value) {
		return columnFamilies.get(value);
	}

	public byte[] get(ColumnFamilyHandle cfh, byte[] key) throws RocksDBException {
		return db.get(cfh, key);
	}

	public synchronized ColumnFamilyHandle createColumnFamily(Object value) throws RocksDBException {
		byte[] b = cfSerializer.objectToByteArray(value);
		ColumnFamilyDescriptor cfd= new ColumnFamilyDescriptor(b);
		ColumnFamilyHandle cfh = db.createColumnFamily(cfd);			
		columnFamilies.put(value, cfh);
		return cfh;
	}

	public void put(ColumnFamilyHandle cfh, byte[] k, byte[] v) throws RocksDBException {		
		db.put(cfh, k, v);
	}

	public Collection<Object> getColumnFamilies() {
		return columnFamilies.keySet();
	}
	
	public String getPath() { 
		return path;
	}

}
