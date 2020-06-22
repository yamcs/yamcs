package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.ClearanceInfo;

import com.google.protobuf.Empty;

/**
 * Subscription for receiving clearance updates.
 */
public class ClearanceSubscription extends AbstractSubscription<Empty, ClearanceInfo> {

    private volatile ClearanceInfo latest;

    protected ClearanceSubscription(MethodHandler methodHandler) {
        super(methodHandler, "clearance", ClearanceInfo.class);
        addMessageListener(this::processMessage);
    }

    protected void processMessage(ClearanceInfo clearance) {
        latest = clearance;
    }

    public ClearanceInfo getCurrent() {
        return latest;
    }
}
