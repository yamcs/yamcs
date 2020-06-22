package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.SubscribeQueueStatisticsRequest;

public class QueueStatisticsSubscription
        extends AbstractSubscription<SubscribeQueueStatisticsRequest, CommandQueueInfo> {

    protected QueueStatisticsSubscription(MethodHandler methodHandler) {
        super(methodHandler, "queue-stats", CommandQueueInfo.class);
    }
}
