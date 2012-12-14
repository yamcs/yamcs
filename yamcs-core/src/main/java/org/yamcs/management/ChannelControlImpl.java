package org.yamcs.management;


import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.Channel;

import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * some minimum properties visible in the jconsole
 * @author nm
 *
 */
public class ChannelControlImpl extends StandardMBean  implements ChannelControl {
    Channel channel;
    ChannelInfo ci;
    Statistics stats;
    
    public ChannelControlImpl(Channel channel)  throws NotCompliantMBeanException {
        super(ChannelControl.class);
        this.channel=channel;
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