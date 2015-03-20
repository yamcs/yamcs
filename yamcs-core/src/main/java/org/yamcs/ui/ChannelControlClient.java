package org.yamcs.ui;


import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.ChannelRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import static org.yamcs.api.Protocol.CHANNEL_INFO_ADDRESS;
import static org.yamcs.api.Protocol.CHANNEL_CONTROL_ADDRESS;
import static org.yamcs.api.Protocol.CHANNEL_STATISTICS_ADDRESS;

/**
 * controls channels in yamcs server via hornetq
 * @author nm
 *
 */
public class ChannelControlClient implements ConnectionListener {
    YamcsConnector yconnector;
    ChannelListener yamcsMonitor;
    YamcsClient yclient;

    public ChannelControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setChannelListener(ChannelListener yamcsMonitor) {
        this.yamcsMonitor=yamcsMonitor;
    }

    public void destroyChannel(String name) throws YamcsApiException {
        // TODO Auto-generated method stub

    }

    public void createChannel(String instance, String name, String type, String spec, boolean persistent, int[] clients) throws YamcsException, YamcsApiException, HornetQException {
        ChannelRequest.Builder crb=ChannelRequest.newBuilder()
        .setInstance(instance).setName(name)
        .setType(type).setSpec(spec).setPersistent(persistent);
        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
        yclient.executeRpc(CHANNEL_CONTROL_ADDRESS, "createChannel", crb.build(), null);
    }

    public void connectToChannel(String instance, String name, int[] clients) throws YamcsException, YamcsApiException {
        ChannelRequest.Builder crb=ChannelRequest.newBuilder()
        .setInstance(instance).setName(name);
        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
        yclient.executeRpc(CHANNEL_CONTROL_ADDRESS, "connectToChannel", crb.build(), null);
    }

    public void pauseArchiveReplay(String instance, String name) throws YamcsException, YamcsApiException {
        ChannelRequest.Builder crb=ChannelRequest.newBuilder()
        .setInstance(instance).setName(name);
        yclient.executeRpc(CHANNEL_CONTROL_ADDRESS, "pauseReplay", crb.build(), null);
    }

    public void resumeArchiveReplay(String instance, String name) throws YamcsApiException, YamcsException {
        ChannelRequest.Builder crb=ChannelRequest.newBuilder()
        .setInstance(instance).setName(name);
        yclient.executeRpc(CHANNEL_CONTROL_ADDRESS, "resumeReplay", crb.build(), null);
    }


    public void seekArchiveReplay(String instance, String name, long newPosition) throws YamcsApiException, YamcsException  {
        ChannelRequest.Builder crb=ChannelRequest.newBuilder()
        .setInstance(instance).setName(name).setSeekTime(newPosition);
        yclient.executeRpc(CHANNEL_CONTROL_ADDRESS, "seekReplay", crb.build(), null);
    }


    @Override
    public void connecting(String url) { }

    public void receiveInitialConfig() {
        try {
            if(yclient==null) {
                yclient=yconnector.getSession().newClientBuilder()
                .setRpc(true).setDataConsumer(CHANNEL_INFO_ADDRESS, null).build();
            } else {
                yclient.dataConsumer.setMessageHandler(null);
            }

            YamcsClient browser=yconnector.getSession().newClientBuilder().setDataConsumer(CHANNEL_INFO_ADDRESS, CHANNEL_INFO_ADDRESS).setBrowseOnly(true).build();
            yclient=yconnector.getSession().newClientBuilder()
            .setRpc(true).setDataConsumer(CHANNEL_INFO_ADDRESS, null).build();

            ClientMessage m1;
            while((m1=browser.dataConsumer.receiveImmediate())!=null) {//send all the messages from the queue first
                sendUpdate(m1);
            }
            browser.close();


            yclient.dataConsumer.setMessageHandler(new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg) {
                    sendUpdate(msg);
                }
            });
            YamcsClient yclientStats=yconnector.getSession().newClientBuilder().setDataConsumer(CHANNEL_STATISTICS_ADDRESS, null).build();
            yclientStats.dataConsumer.setMessageHandler(new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg) {
                    sendStatistics(msg);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            yamcsMonitor.log("error when retrieving link info: "+e.getMessage());
        }
    }

    @Override
    public void connected(String url) {
        yclient=null;
        receiveInitialConfig();
    }

    private void sendUpdate(ClientMessage msg) {
        try {
            String eventName=msg.getStringProperty(Protocol.HDR_EVENT_NAME);
            if("channelUpdated".equals(eventName)) {
                ChannelInfo ci = (ChannelInfo)Protocol.decode(msg, ChannelInfo.newBuilder());
                yamcsMonitor.channelUpdated(ci);
            } else if("channelClosed".equals(eventName)) {
                ChannelInfo ci = (ChannelInfo)Protocol.decode(msg, ChannelInfo.newBuilder());
                yamcsMonitor.channelClosed(ci);
            } else if("clientUpdated".equals(eventName)) {
                ClientInfo ci=(ClientInfo)Protocol.decode(msg, ClientInfo.newBuilder());
                yamcsMonitor.clientUpdated(ci);
            } else if("clientDisconnected".equals(eventName)) {
                ClientInfo ci=(ClientInfo)Protocol.decode(msg, ClientInfo.newBuilder());
                yamcsMonitor.clientDisconnected(ci);
            } else {
                yamcsMonitor.log("Received unknwon message '"+eventName+"'");
            }
        } catch (YamcsApiException e) {
            yamcsMonitor.log("Error when decoding message "+e.getMessage());
        }
    }

    private void sendStatistics(ClientMessage msg) {
        try {
            Statistics s = (Statistics)Protocol.decode(msg, Statistics.newBuilder());
            yamcsMonitor.updateStatistics(s);
        } catch (YamcsApiException e) {
            yamcsMonitor.log("Error when decoding message "+e.getMessage());
        }
    }
    @Override
    public void connectionFailed(String url, YamcsException exception) {    }

    @Override
    public void disconnected() {
        yclient=null;
    }

    @Override
    public void log(String message) {}

    public void close() throws HornetQException {
        yclient.close();
    }  
}