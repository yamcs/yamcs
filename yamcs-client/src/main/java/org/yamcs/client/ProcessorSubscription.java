package org.yamcs.client;

import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.SubscribeProcessorsRequest;

public class ProcessorSubscription extends AbstractSubscription<SubscribeProcessorsRequest, ProcessorInfo> {

    protected ProcessorSubscription(WebSocketClient client) {
        super(client, "processors", ProcessorInfo.class);
    }
}
