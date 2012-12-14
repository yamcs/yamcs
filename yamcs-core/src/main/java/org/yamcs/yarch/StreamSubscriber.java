package org.yamcs.yarch;


public interface StreamSubscriber {
	void onTuple(Stream stream, Tuple tuple);

	void streamClosed(Stream stream);
}
