package org.yamcs.commanding;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


import org.yamcs.YProcessor;
import org.yamcs.security.Privilege;

import org.yamcs.protobuf.Commanding.QueueState;

public class CommandQueue {
    String name;
    ConcurrentLinkedQueue<PreparedCommand> commands=new ConcurrentLinkedQueue<PreparedCommand>();
    QueueState state=QueueState.BLOCKED;
    YProcessor channel;
    
    List<String> roles;
    
    CommandQueue(YProcessor channel, String name) {
        this.channel=channel;
        this.name=name;
        if(!Privilege.getInstance().isEnabled()) state=QueueState.ENABLED;
    }

    public String getName() {
        return name;
    };
    public QueueState getState() {
        return state;
    }
    
    public YProcessor getChannel() {
        return channel;
    }
    
    public PreparedCommand[] getCommandArray() {
        return commands.toArray(new PreparedCommand[0]);
    }

    public int getCommandCount() {
        return commands.size();
    }

    public boolean contains(PreparedCommand pc) {
	return commands.contains(pc);
    }

    /**
     * remove the command from the queue and return true if it has been removed
     * @param pc
     * @return
     */
    public boolean remove(PreparedCommand pc) {
	return commands.remove(pc);
    }
}