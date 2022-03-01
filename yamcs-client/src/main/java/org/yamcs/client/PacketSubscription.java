package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.SubscribePacketsRequest;
import org.yamcs.protobuf.TmPacketData;

/**
 * Subscription for receiving packet updates.
 */
public class PacketSubscription extends AbstractSubscription<SubscribePacketsRequest, TmPacketData> {

    protected PacketSubscription(MethodHandler methodHandler) {
        super(methodHandler, "packets", TmPacketData.class);
    }
}
