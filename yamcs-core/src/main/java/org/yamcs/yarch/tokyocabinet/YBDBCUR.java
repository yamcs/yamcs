package org.yamcs.yarch.tokyocabinet;


import tokyocabinet.BDBCUR;

/** 
 * Wrapper around the Tokyo Cabinet BTree+ database that keeps track of the cursors and 
 * resets them in case a database write takes place
 * Also throws exceptions in case of errors
 */
public class YBDBCUR {
	BDBCUR cur;
	YBDB db;
	volatile boolean dirty;
	private byte[] currentkey;

	
	YBDBCUR(YBDB ybdb) {
		this.db=ybdb;
		cur=new BDBCUR(ybdb.bdb);
	}
	/**
	 * Move the cursor to the front of records corresponding a key. The cursor is set to the first record corresponding the key or the next substitute if completely matching record does not exist.
	 * @param key
	 * @return If successful, it is true, else, it is false. False is returned if there is no record corresponding the condition.
	 */
	public boolean jump(byte[] key) {
		db.rwlock.readLock().lock();
		boolean status=cur.jump(key);
		dirty=false;
		currentkey=cur.key();
		db.rwlock.readLock().unlock();
		return status;
	}
	
	public byte[] key() {
		return currentkey;
	}
	
	public byte[] val() {
		db.rwlock.readLock().lock();
		byte[] val=null;
	//	System.err.println("val dirty: "+dirty);
		if(dirty) {
			if(currentkey!=null) {
				cur.jump(currentkey);
				val=cur.val();
				dirty=false;
			}
		} else {
			val= cur.val();
		}
		db.rwlock.readLock().unlock();
		return val;
	}
	/**
	 * Move the cursor to the next record. 
	 * @return  If successful, it is true, else, it is false. False is returned if there is no next record.
	 */
	public boolean next() {
		db.rwlock.readLock().lock();
		boolean status=false;
		//System.err.println("next dirty: "+dirty+"currentkey: "+Arrays.toString(currentkey));
		if(dirty) {
			if(currentkey!=null) {
				cur.jump(currentkey);
				status=cur.next();
				currentkey=cur.key();
				dirty=false;
			}
		} else {
			status=cur.next();
			currentkey=cur.key();
		}
		db.rwlock.readLock().unlock();
		return status;

	}
	
	public boolean prev() {
		db.rwlock.readLock().lock();
		boolean status=false;
		//System.err.println("next dirty: "+dirty+"currentkey: "+Arrays.toString(currentkey));
		if(dirty) {
			if(currentkey!=null) {
				cur.jump(currentkey);
				status=cur.prev();
				currentkey=cur.key();
				dirty=false;
			}
		} else {
			status=cur.prev();
			currentkey=cur.key();
		}
		db.rwlock.readLock().unlock();
		return status;
	}
	//what happens when there is an error???
	public boolean put(byte[] val, int cpmode) {
		return cur.put(val,cpmode);
	}

	public boolean out() {
		db.rwlock.writeLock().lock();
		boolean status=false;
		//System.err.println("next dirty: "+dirty+"currentkey: "+Arrays.toString(currentkey));
		if(dirty) {
			if(currentkey!=null) {
				cur.jump(currentkey);
				status=cur.out();
				currentkey=cur.key();
				db.markCursorsDirty();
				dirty=false;//all cursors are dirty except this one
			}
		} else {
			status=cur.out();
			currentkey=cur.key();
		}
		db.rwlock.writeLock().unlock();
		return status;
	}

	public boolean first() {
		db.rwlock.readLock().lock();
		boolean status=cur.first();
		dirty=false;
		currentkey=cur.key();
		db.rwlock.readLock().unlock();
		return status;

	}
	public void close() {
		db.closeCursor(this);
	}
	public void finalize() {
		close();
	}
}
