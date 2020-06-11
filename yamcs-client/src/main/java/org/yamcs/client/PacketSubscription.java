package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.SubscribePacketsRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;

/**
 * Subscription for receiving packet updates.
 */
public class PacketSubscription extends AbstractSubscription<SubscribePacketsRequest, TmPacketData> {

    protected PacketSubscription(WebSocketClient client) {
        super(client, "packets", TmPacketData.class);
    }
}
