package org.yamcs.cmdhistory;

import org.yamcs.cmdhistory.CommandHistoryRequestManager.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;

public class CommandHistoryFilter {
    private String commandsOrigin;
    private long commandsSince;
    public int subscriptionId;

    public CommandHistoryFilter(int subscriptionId, String commandsOrigin, long commandsSince) {
        this.subscriptionId = subscriptionId;
        this.commandsOrigin = commandsOrigin;
        this.commandsSince = commandsSince;
    }

    public boolean matches(CommandHistoryEntry che) {
        CommandId cmdId = che.getCommandId();
        if (cmdId.getGenerationTime() < commandsSince) {
            return false;
        }
        if ((commandsOrigin != null) && (!commandsOrigin.equals("*")) && (!commandsOrigin.equals(cmdId.getOrigin()))) {
            return false;
        }

        return true;
    }
}
