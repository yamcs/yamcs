package org.yamcs.management;


import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.Processor;

import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * some minimum properties visible in the jconsole
 * @author nm
 *
 */
public class ProcessorControlImpl extends StandardMBean  implements ProcessorControl {
    Processor channel;
    ProcessorInfo ci;
    Statistics stats;
    
    public ProcessorControlImpl(Processor yproc)  throws NotCompliantMBeanException {
        super(ProcessorControl.class);
        this.channel=yproc;
    }
    @Override
    public String getName() {
        return channel.getName();
    }

    @Override
    public String getType() {
        return channel.getType();
    }

    @Override
    public String getCreator() {
        return channel.getCreator();
    }
    
    @Override
    public boolean isReplay() {
        return channel.isReplay();
    }
    
    @Override 
    public String getReplayState() {
        if(channel.isReplay())
            return channel.getReplayState().toString();
        else return null;
    }
    
}