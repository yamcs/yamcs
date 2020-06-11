package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.SubscribeQueueStatisticsRequest;

public class QueueStatisticsSubscription
        extends AbstractSubscription<SubscribeQueueStatisticsRequest, CommandQueueInfo> {

    protected QueueStatisticsSubscription(WebSocketClient client) {
        super(client, "queue-stats", CommandQueueInfo.class);
    }
}
