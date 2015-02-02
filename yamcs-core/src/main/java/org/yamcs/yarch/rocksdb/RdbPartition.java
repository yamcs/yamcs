package org.yamcs.yarch.rocksdb;

import org.yamcs.yarch.Partition;

public class RdbPartition extends Partition {
	String dir;
	
	public RdbPartition(long start, long end, Object v, String dir) {
		super(start, end, v);
		this.dir = dir;
	}

}
