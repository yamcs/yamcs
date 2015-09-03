package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;

import com.google.protobuf.MessageLite;

public interface ReplayListener {

    void newData(ProtoDataType type, MessageLite data);

    void stateChanged(ReplayStatus rs);
}
