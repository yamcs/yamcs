package org.yamcs.ui;

import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;

public interface CommandQueueListener {
    /*
     * called once after the connection to yamcs has been (re)established
     *  and then each time when a queue changes state
     * */
    void updateQueue(CommandQueueInfo cqi);

    void commandAdded(CommandQueueEntry cqe);
    void commandRejected(CommandQueueEntry cqe);
    void commandSent(CommandQueueEntry cqe);
    void log(String string);
}
