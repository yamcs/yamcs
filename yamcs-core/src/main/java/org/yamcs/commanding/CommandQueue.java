package org.yamcs.commanding;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;

import org.yamcs.YProcessor;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;

public class CommandQueue {
    String name;
    private ConcurrentLinkedQueue<PreparedCommand> commands=new ConcurrentLinkedQueue<PreparedCommand>();
    QueueState defaultState=QueueState.BLOCKED;
    QueueState state=QueueState.BLOCKED;
    YProcessor processor;

    int nbSentCommands = 0;
    int nbRejectedCommands = 0;
    int stateExpirationTimeS = 0;


    int stateExpirationRemainingS = -1;
    ScheduledFuture<?> stateExpirationJob = null;
    
    List<String> roles;
    List<String> significances;
    NamedObjectId sp_queueState_id;
    NamedObjectId sp_numSentCommands_id;
    NamedObjectId sp_numRejectedCommands_id;
    NamedObjectId sp_numCommands_id;

    CommandQueue(YProcessor channel, String name) {
        this.processor=channel;
        this.name=name;
        if(!Privilege.getInstance().isEnabled()) state=QueueState.ENABLED;
    }

    void setupSysParameters() {
        SystemParametersCollector sysParamCollector = SystemParametersCollector.getInstance(processor.getInstance());
        sp_queueState_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/cmdQueue/"+name+"/state").build();
        sp_numCommands_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/cmdQueue/"+name+"/numCommands").build();
        sp_numSentCommands_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/cmdQueue/"+name+"/numSentCommands").build();
        sp_numRejectedCommands_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/cmdQueue/"+name+"/numRejectedCommands").build();
    }

    public ConcurrentLinkedQueue<PreparedCommand> getCommands() {
        return commands;
    }

    public String getName() {
        return name;
    };
    public QueueState getState() {
        return state;
    }

    public YProcessor getChannel() {
        return processor;
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

    public void add(PreparedCommand pc) {
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
        if(removed) {
            if(isSent)
                nbSentCommands++;
            else
                nbRejectedCommands++;
        }
        return removed;
    }

    public void clear(boolean areSent) {
        int nbCommands = commands.size();
        commands.clear();
        if(areSent) {
            nbSentCommands += nbCommands;
        } else {
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

    void fillInSystemParameters(List<Pvalue.ParameterValue> params, long time) {

        params.add(SystemParametersCollector.getPV(sp_queueState_id, time, state.name()));
        params.add(SystemParametersCollector.getPV(sp_numCommands_id, time, commands.size()));
        params.add(SystemParametersCollector.getPV(sp_numSentCommands_id, time, nbSentCommands));
        params.add(SystemParametersCollector.getPV(sp_numRejectedCommands_id, time, nbRejectedCommands));
    }
}
