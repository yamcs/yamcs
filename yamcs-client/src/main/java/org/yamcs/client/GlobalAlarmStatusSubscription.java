package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.alarms.GlobalAlarmStatus;
import org.yamcs.protobuf.alarms.SubscribeGlobalStatusRequest;

/**
 * Subscription for receiving alarm detail.
 */
public class GlobalAlarmStatusSubscription
        extends AbstractSubscription<SubscribeGlobalStatusRequest, GlobalAlarmStatus> {

    protected GlobalAlarmStatusSubscription(MethodHandler methodHandler) {
        super(methodHandler, "global-alarm-status", GlobalAlarmStatus.class);
    }
}
