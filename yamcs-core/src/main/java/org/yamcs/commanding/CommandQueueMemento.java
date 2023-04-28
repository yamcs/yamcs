package org.yamcs.commanding;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * Object that is used to persist link state information across Yamcs restarts.
 */
public class CommandQueueMemento {

    @SerializedName("queues")
    private Map<String, CommandQueueState> queues = new HashMap<>();

    public void addCommandQueueState(String queue, CommandQueueState state) {
        queues.put(queue, state);
    }

    public CommandQueueState getCommandQueueState(String queue) {
        return queues.get(queue);
    }
}
