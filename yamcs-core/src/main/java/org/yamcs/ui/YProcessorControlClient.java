package org.yamcs.ui;


import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Constants;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;

import static org.yamcs.api.Protocol.YPROCESSOR_INFO_ADDRESS;
import static org.yamcs.api.Protocol.YPROCESSOR_CONTROL_ADDRESS;
import static org.yamcs.api.Protocol.YPROCESSOR_STATISTICS_ADDRESS;

/**
 * controls yprocessors in yamcs server via hornetq
 * @author nm
 *
 */
public class YProcessorControlClient implements ConnectionListener {
    YamcsConnector yconnector;
    YProcessorListener yamcsMonitor;
    YamcsClient yclient;

    public YProcessorControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setYProcessorListener(YProcessorListener yamcsMonitor) {
        this.yamcsMonitor=yamcsMonitor;
    }

    public void destroyYProcessor(String name) throws YamcsApiException {
        // TODO Auto-generated method stub

    }

    public void createProcessor(String instance, String name, String type, Yamcs.ReplayRequest spec, boolean persistent, int[] clients) throws YamcsException, YamcsApiException, HornetQException {

        Yamcs.ReplayRequest.Builder rp = Yamcs.ReplayRequest.newBuilder();


        ProcessorManagementRequest.Builder crb = ProcessorManagementRequest.newBuilder()
        .setInstance(instance).setName(name)
        .setType(type).setReplaySpec(spec).setPersistent(persistent);

        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
        yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_createProcessor, crb.build(), null);
    }

    public void connectToYProcessor(String instance, String name, int[] clients) throws YamcsException, YamcsApiException {
        ProcessorManagementRequest.Builder crb = ProcessorManagementRequest.newBuilder()
        .setInstance(instance).setName(name);
        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
        yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_connectToProcessor, crb.build(), null);
    }

    public void pauseArchiveReplay(String instance, String name) throws YamcsException, YamcsApiException {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name);
        yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_pauseReplay, crb.build(), null);
    }

    public void resumeArchiveReplay(String instance, String name) throws YamcsApiException, YamcsException {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name);
        yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_resumeReplay, crb.build(), null);
    }


    public void seekArchiveReplay(String instance, String name, long newPosition) throws YamcsApiException, YamcsException  {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name).setSeekTime(newPosition);
        yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, "seekReplay", crb.build(), null);
    }


    @Override
    public void connecting(String url) { }

    public void receiveInitialConfig() {
        try {
            if(yclient==null) {
                yclient=yconnector.getSession().newClientBuilder()
                .setRpc(true).setDataConsumer(YPROCESSOR_INFO_ADDRESS, null).build();
            } else {
                yclient.dataConsumer.setMessageHandler(null);
            }

            YamcsClient browser=yconnector.getSession().newClientBuilder().setDataConsumer(YPROCESSOR_INFO_ADDRESS, YPROCESSOR_INFO_ADDRESS).setBrowseOnly(true).build();
            yclient=yconnector.getSession().newClientBuilder()
            .setRpc(true).setDataConsumer(YPROCESSOR_INFO_ADDRESS, null).build();

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
            YamcsClient yclientStats=yconnector.getSession().newClientBuilder().setDataConsumer(YPROCESSOR_STATISTICS_ADDRESS, null).build();
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
            if("yprocUpdated".equals(eventName)) {
                ProcessorInfo ci = (ProcessorInfo)Protocol.decode(msg, ProcessorInfo.newBuilder());
                yamcsMonitor.processorUpdated(ci);
            } else if("yprocClosed".equals(eventName)) {
                ProcessorInfo ci = (ProcessorInfo)Protocol.decode(msg, ProcessorInfo.newBuilder());
                yamcsMonitor.yProcessorClosed(ci);
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