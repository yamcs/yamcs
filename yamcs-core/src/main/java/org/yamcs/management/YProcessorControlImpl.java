package org.yamcs.management;


import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.YProcessor;

import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * some minimum properties visible in the jconsole
 * @author nm
 *
 */
public class YProcessorControlImpl extends StandardMBean  implements YProcessorControl {
    YProcessor channel;
    ProcessorInfo ci;
    Statistics stats;
    
    public YProcessorControlImpl(YProcessor yproc)  throws NotCompliantMBeanException {
        super(YProcessorControl.class);
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