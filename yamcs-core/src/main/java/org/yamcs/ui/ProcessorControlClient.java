package org.yamcs.ui;


import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.web.websocket.ManagementResource;


/**
 * controls processors in yamcs server via websocket
 * 
 * @author nm
 *
 */
public class ProcessorControlClient implements ConnectionListener, WebSocketClientCallback {
    YamcsConnector yconnector;
    ProcessorListener yamcsMonitor;
    

    public ProcessorControlClient(YamcsConnector yconnector) {
        this.yconnector = yconnector;
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
        WebSocketRequest wsr = new WebSocketRequest(ManagementResource.RESOURCE_NAME, ManagementResource.OP_subscribe);
        yconnector.performSubscription(wsr, this);
    }

    @Override
    public void connected(String url) {
        receiveInitialConfig();
    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if(data.hasProcessorInfo()) {
            ProcessorInfo procInfo = data.getProcessorInfo();
            ServiceState servState = procInfo.getState();
            if(servState==ServiceState.TERMINATED || servState ==ServiceState.FAILED) {
                yamcsMonitor.processorClosed(procInfo);
            } else {
                yamcsMonitor.processorUpdated(procInfo);
            }
        }
        if(data.hasClientInfo()) {
            ClientInfo cinfo = data.getClientInfo();
            ClientState cstate = cinfo.getState();
            if(cstate==ClientState.DISCONNECTED) {
                yamcsMonitor.clientUpdated(cinfo);
            } else {
                yamcsMonitor.clientUpdated(cinfo);
            }
        }
        if(data.hasStatistics()) {
            Statistics s = data.getStatistics();
            yamcsMonitor.updateStatistics(s);
        }
    }
    
    @Override
    public void connectionFailed(String url, YamcsException exception) {    }

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {}

}