package org.yamcs.client;

import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.SubscribeCommandsRequest;

/**
 * Subscription for tracking issued commands, their attributes and acknowledgment status.
 * 
 * TODO Does not yet merge entries. Currently clients should do themselves the work by registering an EntryListener.
 */
public class CommandSubscription extends AbstractSubscription<SubscribeCommandsRequest, CommandHistoryEntry> {

    protected CommandSubscription(WebSocketClient client) {
        super(client, "commands", CommandHistoryEntry.class);
    }
}
