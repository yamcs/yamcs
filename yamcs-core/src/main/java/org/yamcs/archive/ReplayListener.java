package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;

public interface ReplayListener {

    void newData(ProtoDataType type, Object data);

    void stateChanged(ReplayStatus rs);
}
