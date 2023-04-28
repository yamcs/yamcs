package org.yamcs.commanding;

import org.yamcs.protobuf.Commanding.QueueState;

import com.google.gson.annotations.SerializedName;

public class CommandQueueState {

    @SerializedName("state")
    private QueueState state;

    /**
     * The state of the queue.
     */
    public QueueState getState() {
        return state;
    }

    /**
     * Create state object for the given queue.
     */
    public static CommandQueueState forQueue(CommandQueue queue) {
        var state = new CommandQueueState();
        state.state = queue.getState();
        return state;
    }
}
