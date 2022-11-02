package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

public interface ReplayListener {

    void newData(ProtoDataType type, Object data);

    void stateChanged(ReplayStatus rs);
}
