package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.alarms.SubscribeAlarmsRequest;

/**
 * Subscription for receiving alarm detail.
 */
public class AlarmSubscription extends AbstractSubscription<SubscribeAlarmsRequest, AlarmData> {

    protected AlarmSubscription(WebSocketClient client) {
        super(client, "alarms", AlarmData.class);
    }
}
