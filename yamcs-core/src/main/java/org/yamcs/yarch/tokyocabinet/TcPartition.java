package org.yamcs.yarch.tokyocabinet;

import org.yamcs.yarch.Partition;

public class TcPartition extends Partition {
	String dir;
	
	public TcPartition(long start, long end, Object v, String dir) {
		super(start, end, v);
		this.dir = dir;
	}

}
