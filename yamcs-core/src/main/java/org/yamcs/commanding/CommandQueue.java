package org.yamcs.commanding;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


import org.yamcs.YProcessor;
import org.yamcs.security.Privilege;

import org.yamcs.protobuf.Commanding.QueueState;

public class CommandQueue {
    String name;
    private ConcurrentLinkedQueue<PreparedCommand> commands=new ConcurrentLinkedQueue<PreparedCommand>();
    QueueState defaultState=QueueState.BLOCKED;
    QueueState state=QueueState.BLOCKED;
    YProcessor channel;

    int nbSentCommands = 0;
    int nbRejectedCommands = 0;
    int stateExpirationTimeS = 0;


    int stateExpirationRemainingS = 0;
    Runnable stateExpirationJob = null;
    
    List<String> roles;
    List<String> significances;
    
    CommandQueue(YProcessor channel, String name) {
        this.channel=channel;
        this.name=name;
        if(!Privilege.getInstance().isEnabled()) state=QueueState.ENABLED;
    }

    public ConcurrentLinkedQueue<PreparedCommand> getCommands()
    {
        return commands;
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



    public void add(PreparedCommand pc)
    {
        commands.add(pc);
    }

    /**
     * remove the command from the queue and return true if it has been removed
     * @param pc
     * @param isSent: true if the command has been sent, false if the commmand has been rejected
     * @return
     */
    public boolean remove(PreparedCommand pc, boolean isSent) {
        boolean removed = commands.remove(pc);
        if(removed)
        {
            if(isSent)
                nbSentCommands++;
            else
                nbRejectedCommands++;
        }
        return removed;
    }

    public void clear(boolean areSent)
    {
        int nbCommands = commands.size();
        commands.clear();
        if(areSent)
        {
            nbSentCommands += nbCommands;
        }
        else
        {
            nbRejectedCommands += nbCommands;
        }
    }

    public int getNbRejectedCommands() {
        return nbRejectedCommands;
    }


    public int getStateExpirationRemainingS() {
        return stateExpirationRemainingS;
    }

    public int getNbSentCommands() {
        return nbSentCommands;
    }
}