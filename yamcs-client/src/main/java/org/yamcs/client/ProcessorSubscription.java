package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.SubscribeProcessorsRequest;

public class ProcessorSubscription extends AbstractSubscription<SubscribeProcessorsRequest, ProcessorInfo> {

    protected ProcessorSubscription(MethodHandler methodHandler) {
        super(methodHandler, "processors", ProcessorInfo.class);
    }
}
