package org.yamcs.oldparchive;

import org.yamcs.protobuf.Yamcs.Value;

public interface ValueConsumer {
    void accept(int parameterId, int parameterGroupId, long t, Value v);
}
