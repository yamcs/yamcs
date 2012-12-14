package org.yamcs.api;

import com.google.protobuf.MessageLite;

public interface DataListener {

    void onData(MessageLite data);

    void replayFinished();

    void log(String message);
    
    void exception(Exception e);

	void replayStopped();

}
