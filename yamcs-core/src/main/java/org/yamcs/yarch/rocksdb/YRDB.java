package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
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
import org.yamcs.yarch.DataType;

/**
 * wrapper around RocksDB that keeps track of column families
 * @author nm
 *
 */
public class YRDB {
	Map<Object, ColumnFamilyHandle> columnFamilies=new HashMap<Object, ColumnFamilyHandle>();
	RocksDB db;
	private boolean isClosed = false;
	DataType valuePartitionDataType;
	private final String path;
	
	public static byte[] NULL_COLUMN_FAMILY = {}; 
	/**
	 * Create or open a new RocksDb.
	 * 
	 * @param dir - if it exists, it has to be a directory
	 * @param valuePartitionDataType - the data type of the value partition if partitioned by value, otherwise null. It is used to convert between byte array (which is how column families are handled) and that value 
	 * @throws RocksDBException
	 * @throws IOException 
	 */
	public YRDB(String dir, DataType valuePartitionDataType) throws RocksDBException, IOException {
		this.valuePartitionDataType = valuePartitionDataType;
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
						Object value = byteArrayToValue(b);	
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
	
	List<RocksIterator> newIterators(List<ColumnFamilyHandle> cfhList) throws RocksDBException {
		ReadOptions ro = new ReadOptions();
		ro.setTailing(true);
		return db.newIterators(cfhList, ro);
	}
	
	
	public synchronized ColumnFamilyHandle getColumnFamilyHandle(Object value) {
		return columnFamilies.get(value);
	}

	public byte[] get(ColumnFamilyHandle cfh, byte[] key) throws RocksDBException {
		return db.get(cfh, key);
	}

	public synchronized ColumnFamilyHandle createColumnFamily(Object value) throws RocksDBException {
		byte[] b =valueToByteArray(value);
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
	
	

	/**
	 * In RocksDB value based partitioning is based on RocksDB Column Families. Each ColumnFamily is identified by a byte[]
	 * 
	 * This method makes the conversion between the value and the byte[]	
	 * @param value
	 * @return
	 */
	public byte[] valueToByteArray(Object value) {
		if(value==null) return NULL_COLUMN_FAMILY;
		
		if(value.getClass()==Integer.class) {
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.putInt((Integer)value);
			return bb.array();
		} else if(value.getClass()==Short.class) {
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.putShort((Short)value);
			return bb.array();			
		} else if(value.getClass()==Byte.class) {
			ByteBuffer bb = ByteBuffer.allocate(1);
			bb.put((Byte)value);
			return bb.array();
		} else if(value.getClass()==String.class) {
			return ((String)value).getBytes();
		} else {
			throw new IllegalArgumentException("partition on values of type "+value.getClass()+" not supported");
		}
	}
	/**
	 * this is the reverse of the {@link #valueToByteArray(Object value)}
	 * @param part
	 * @param dt
	 * @return
	 */
	public Object byteArrayToValue(byte[] b) {
		DataType dt = valuePartitionDataType;
		switch(dt.val) {
		case INT:
			if(b.length!=4) throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
			ByteBuffer bb = ByteBuffer.wrap(b);
			return bb.getInt();            
		case SHORT:
		case ENUM: //intentional fall-through
			if(b.length!=2) throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
			bb = ByteBuffer.wrap(b);
			return bb.getShort();             
		case BYTE:
			if(b.length!=1) throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
			bb = ByteBuffer.wrap(b);
			return bb.get();             
		case STRING:
			return new String(b);
		default:
			throw new IllegalArgumentException("partition on values of type "+dt+" not supported");
		}
	}

	public String getPath() { 
		return path;
	}

}
