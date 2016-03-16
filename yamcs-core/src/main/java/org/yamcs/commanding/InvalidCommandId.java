package org.yamcs.commanding;

import org.yamcs.protobuf.Commanding.CommandId;

public class InvalidCommandId extends Exception {
    CommandId cmdId;
    public InvalidCommandId(String msg, CommandId cmdId) {
        super(msg);
        this.cmdId=cmdId;
    }
    public InvalidCommandId(String msg) {
        super(msg);
    }
}
