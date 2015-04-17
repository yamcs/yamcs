package org.yamcs.yarch.tokyocabinet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import tokyocabinet.BDB;;

/**
 * Wrapper around the Tokyo Cabinet BTree+ database that keeps track of the cursors and 
 * resets them in case a database write takes place
 *  
 * @author nm
 *
 */
public class YBDB {
	BDB bdb;
	ReadWriteLock rwlock=new ReentrantReadWriteLock();
	ArrayList<YBDBCUR> cursors=new ArrayList<YBDBCUR>();
	
	public YBDB() {
		bdb=new BDB();
	}
	
	public YBDBCUR openCursor() {
		rwlock.writeLock().lock();
		try {
			YBDBCUR cur=new YBDBCUR(this);
			cursors.add(cur);
			return cur;
		} finally {
			rwlock.writeLock().unlock();
		}
	}

	public void closeCursor(YBDBCUR cur) {
		rwlock.writeLock().lock();
		try {
			cursors.remove(cur);
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	public void tune(int lmemb, int nmemb, long bnum, int apow, int fpow, int opts) throws IOException {
		if(!bdb.tune(lmemb, nmemb, bnum, apow, fpow, opts)) throw new IOException(bdb.errmsg());
	}
	public void setcache(int lcnum, int ncnum) throws IOException {
		if(!bdb.setcache(lcnum, ncnum)) throw new IOException(bdb.errmsg());
	}
	
	public void close() throws IOException {
		if(!bdb.close()) {
			throw new IOException(bdb.errmsg());
		}
	}

	public void open(String name, int omode) throws IOException {
		if(!bdb.open(name, omode)) {
			if(bdb.ecode()==BDB.ENOFILE) {
				throw new FileNotFoundException(bdb.errmsg()+": "+name);
			} else {
				throw new IOException(bdb.errmsg());
			}
		}
	}
	/**
	 * Store a record. If a record with the same key exists in the database, it is overwritten.
	 * @param key
	 * @param val
	 * @throws IOException
	 */
	public void put(byte[] key, byte[] val) throws IOException{
		rwlock.writeLock().lock();
		try {
			if(!bdb.put(key, val)) 	throw new IOException(bdb.errmsg());
			markCursorsDirty();
		} finally {
			rwlock.writeLock().unlock();
		}
		
	}
	/**
	 * Remove a record. If the key of duplicated records is specified, the first one is selected.
	 * @param key
	 * @throws IOException
	 */
	public void out(byte[] key) throws IOException{
		rwlock.writeLock().lock();
		try {
			if(!bdb.out(key)) 	throw new IOException(bdb.errmsg());
			markCursorsDirty();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	/**
	 * Store a record with allowing duplication of keys. If a record with the same key exists in the database, the new record is placed after the existing one.
	 * @param key
	 * @param val
	 * @throws IOException
	 */
	public void putdup(byte[] key, byte[] val) throws IOException{
		rwlock.writeLock().lock();
		try {
			if(!bdb.putdup(key,val)) throw new IOException(bdb.errmsg());
			markCursorsDirty();
		} finally {
			rwlock.writeLock().unlock();
		}
	}
	
	/**
	 * 
	 * @param key
	 * @param val
	 * @return true if the record has been inserted, false otherwise
	 * @throws IOException
	 */
	public boolean putkeep(byte[] key, byte[] val) throws IOException {
		rwlock.writeLock().lock();
		boolean status=bdb.putkeep(key,val);
		markCursorsDirty();
		rwlock.writeLock().unlock();
		if(!status) { 
			if(bdb.ecode()!=BDB.EKEEP) throw new IOException(bdb.errmsg()); 
		}
		return status;
	}
	
	void markCursorsDirty() {
		for(YBDBCUR cur:cursors) {
			cur.dirty=true;
		}	
	}
	public byte[] get(byte[] key) {
		return bdb.get(key);
	}
	public long rnum() {
		return bdb.rnum();
	}
	public void sync() throws IOException {
		if(!bdb.sync()) throw new IOException(bdb.errmsg());
	}

	public String path() {
		return	bdb.path();
	}

}
