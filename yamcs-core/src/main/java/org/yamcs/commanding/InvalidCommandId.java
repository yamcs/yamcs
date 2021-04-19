package org.yamcs.commanding;

import org.yamcs.protobuf.Commanding.CommandId;

@SuppressWarnings("serial")
public class InvalidCommandId extends RuntimeException {

    CommandId cmdId;

    public InvalidCommandId(String msg, CommandId cmdId) {
        super(msg);
        this.cmdId = cmdId;
    }

    public InvalidCommandId(String msg) {
        super(msg);
    }
}
