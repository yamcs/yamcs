package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.YProcessor;
import org.yamcs.ChannelClient;
import org.yamcs.ChannelException;

import org.yamcs.protobuf.YamcsManagement.ClientInfo;

public class ClientControlImpl extends StandardMBean implements ClientControl {
    ClientInfo clientInfo;
    ChannelClient client;
    
    ClientControlImpl(String instance, int id, String username, String applicationName, String channelName, ChannelClient client) throws NotCompliantMBeanException {
        super(ClientControl.class);
        this.client=client;
        clientInfo=ClientInfo.newBuilder().setInstance(instance)
            .setApplicationName(applicationName)
            .setChannelName(channelName).setUsername(username)
            .setId(id).build();
    }
    
    @Override
    public String getApplicationName() {
        return clientInfo.getApplicationName();
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    
    public ChannelClient getClient(){
        return client;
    }

    public void switchChannel(YProcessor chan) throws ChannelException {
        client.switchChannel(chan);
        
        clientInfo=ClientInfo.newBuilder().mergeFrom(clientInfo)
            .setInstance(chan.getInstance()).setChannelName(chan.getName())
            .build();
    }
}
