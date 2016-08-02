package org.yamcs.ui;


import org.yamcs.YamcsException;
import org.yamcs.api.Constants;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;


/**
 * controls yprocessors in yamcs server via hornetq
 * @author nm
 *
 */
public class ProcessorControlClient implements ConnectionListener, WebSocketClientCallback {
    YamcsConnector yconnector;
    ProcessorListener yamcsMonitor;
    

    public ProcessorControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setYProcessorListener(ProcessorListener yamcsMonitor) {
        this.yamcsMonitor=yamcsMonitor;
    }

    public void destroyYProcessor(String name) throws YamcsApiException {
        // TODO Auto-generated method stub

    }

    public void createProcessor(String instance, String name, String type, Yamcs.ReplayRequest spec, boolean persistent, int[] clients) throws YamcsException, YamcsApiException{

        Yamcs.ReplayRequest.Builder rp = Yamcs.ReplayRequest.newBuilder();


        ProcessorManagementRequest.Builder crb = ProcessorManagementRequest.newBuilder()
        .setInstance(instance).setName(name)
        .setType(type).setReplaySpec(spec).setPersistent(persistent);

        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
        //yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_createProcessor, crb.build(), null);
    }

    public void connectToYProcessor(String instance, String name, int[] clients) throws YamcsException, YamcsApiException {
        ProcessorManagementRequest.Builder crb = ProcessorManagementRequest.newBuilder()
        .setInstance(instance).setName(name);
        for(int i=0;i<clients.length;i++) {
            crb.addClientId(clients[i]);
        }
       // yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_connectToProcessor, crb.build(), null);
    }

    public void pauseArchiveReplay(String instance, String name) throws YamcsException, YamcsApiException {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name);
     //   yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_pauseReplay, crb.build(), null);
    }

    public void resumeArchiveReplay(String instance, String name) throws YamcsApiException, YamcsException {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name);
     //   yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, Constants.YPR_resumeReplay, crb.build(), null);
    }


    public void seekArchiveReplay(String instance, String name, long newPosition) throws YamcsApiException, YamcsException  {
        ProcessorRequest.Builder crb = ProcessorRequest.newBuilder()
        .setInstance(instance).setName(name).setSeekTime(newPosition);
      //  yclient.executeRpc(YPROCESSOR_CONTROL_ADDRESS, "seekReplay", crb.build(), null);
    }


    @Override
    public void connecting(String url) { }

    public void receiveInitialConfig() {
        
        try {
            WebSocketRequest wsr = new WebSocketRequest("blalbla", "bubu");
            yconnector.performSubscription(wsr, this);
        } catch (Exception e) {
            e.printStackTrace();
            yamcsMonitor.log("error when retrieving link info: "+e.getMessage());
        }
    }

    @Override
    public void connected(String url) {
        receiveInitialConfig();
    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
    /*    try {
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
        }*/
    }
/*
    private void sendStatistics(ClientMessage msg) {
        try {
            Statistics s = (Statistics)Protocol.decode(msg, Statistics.newBuilder());
            yamcsMonitor.updateStatistics(s);
        } catch (YamcsApiException e) {
            yamcsMonitor.log("Error when decoding message "+e.getMessage());
        }
    }*/
    
    @Override
    public void connectionFailed(String url, YamcsException exception) {    }

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {}

}