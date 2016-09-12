package org.yamcs.ui;


import io.netty.handler.codec.http.HttpMethod;

import java.util.concurrent.CompletableFuture;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.utils.TimeEncoding;
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
        CreateProcessorRequest.Builder cprb =  CreateProcessorRequest.newBuilder().setName(name);
        for(int cid:clients) cprb.addClientId(cid);
        if(spec.hasStart()) {
            cprb.setStart(TimeEncoding.toString(spec.getStart()));
        } else if (spec.hasUtcStart()) {
            cprb.setStart(spec.getUtcStart());
        }

        if(spec.hasStop()) {
            cprb.setStop(TimeEncoding.toString(spec.getStop()));
        } else if (spec.hasUtcStop()) {
            cprb.setStop(spec.getUtcStop());            
        }
        if(spec.hasPacketRequest()) {
            PacketReplayRequest prr = spec.getPacketRequest();
            for(NamedObjectId oid: prr.getNameFilterList()) {
                if(oid.hasNamespace()) cprb.addPacketname(oid.getNamespace()+"/"+oid.getName());
                else cprb.addPacketname(oid.getName());
            }
        }

        for(int i=0;i<clients.length;i++) {
            cprb.addClientId(clients[i]);
        }
        if(spec.hasPpRequest()) {
            PpReplayRequest ppr = spec.getPpRequest();
            cprb.addAllPpgroup(ppr.getGroupNameFilterList());
        }
        if(spec.hasParameterRequest()) {
            ParameterReplayRequest ppr = spec.getParameterRequest();
            for(NamedObjectId oid: ppr.getNameFilterList()) {
                if(oid.hasNamespace()) cprb.addParaname(oid.getNamespace()+"/"+oid.getName());
                else cprb.addParaname(oid.getName());
            }
        }
        if(spec.hasSpeed()) {
            ReplaySpeed speed = spec.getSpeed();
            if(speed.getType()==ReplaySpeedType.AFAP) {
                cprb.setSpeed("afap");    
            } else if(speed.getType()==ReplaySpeedType.FIXED_DELAY) {
                cprb.setSpeed(Integer.toString(Math.round(speed.getParam())));
            } else if(speed.getType()==ReplaySpeedType.REALTIME) {
                cprb.setSpeed(speed.getParam()+"x");
            }
        }
        if(spec.hasEndAction()) {
            EndAction endAction = spec.getEndAction();
            if(endAction==EndAction.LOOP) {
                cprb.setLoop(true);
            }
        }
        RestClient restClient = yconnector.getRestClient();
        //POST "/api/processors/:instance"
        String resource = "/processors/"+instance;
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH, cprb.build().toByteArray());
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                yamcsMonitor.log("Exception creating processor: "+exception.getMessage());
            }
        });
    }

    public void connectToProcessor(String instance, String processorName, int[] clients) throws YamcsException, YamcsApiException {
        RestClient restClient = yconnector.getRestClient();

        for(int i=0;i<clients.length;i++) {
            //PATCH /api/clients/:id
            String resource = "/clients/"+clients[i]+"?processor="+processorName+"&instance="+instance;
            CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
            cf.whenComplete((result, exception) -> {
                if(exception!=null) {
                    yamcsMonitor.log("Exception connecting client to processor: "+exception.getMessage());
                }
            });
        }       
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
            if(servState==ServiceState.TERMINATED || servState==ServiceState.FAILED) {
                yamcsMonitor.processorClosed(procInfo);
            } else {
                yamcsMonitor.processorUpdated(procInfo);
            }
        }
        if(data.hasClientInfo()) {
            ClientInfo cinfo = data.getClientInfo();
            ClientState cstate = cinfo.getState();
            if(cstate==ClientState.DISCONNECTED) {
                yamcsMonitor.clientDisconnected(cinfo);
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