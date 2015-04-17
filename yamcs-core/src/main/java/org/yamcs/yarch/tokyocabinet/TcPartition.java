package org.yamcs.yarch.tokyocabinet;

import org.yamcs.yarch.Partition;

public class TcPartition extends Partition {
	String filename;
	
	public TcPartition(long start, long end, Object v, String filename) {
		super(start, end, v);
		this.filename = filename;
	}

}
