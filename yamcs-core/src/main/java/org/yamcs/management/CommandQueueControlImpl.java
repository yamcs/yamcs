package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;

import org.yamcs.protobuf.Commanding.CommandQueueInfo;

public class CommandQueueControlImpl extends StandardMBean  implements CommandQueueControl {
    CommandQueue queue;
    CommandQueueManager cqm;
    CommandQueueInfo cqi;
    
    public CommandQueueControlImpl(String instance, String yprocName, CommandQueueManager cqm, CommandQueue queue)  throws NotCompliantMBeanException {
        super(CommandQueueControl.class);
        this.queue=queue;
        this.cqm=cqm;
        cqi=CommandQueueInfo.newBuilder().setInstance(instance).setProcessorName(yprocName)
            .setName(queue.getName()).setState(queue.getState()).setNbSentCommands(queue.getNbSentCommands())
                .setNbRejectedCommands(queue.getNbRejectedCommands()).build();
    }

    @Override
    public String getName() {
        return queue.getName();
    }

    @Override
    public String getState() {
        return queue.getState().toString();
    }   
    
    @Override
    public int getCommandCount() {
        return queue.getCommandCount();
    }
}