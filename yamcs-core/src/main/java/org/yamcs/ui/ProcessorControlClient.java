package org.yamcs.ui;


import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.SchemaYamcs.ReplayRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ManagementResource;

import io.netty.handler.codec.http.HttpMethod;
import io.protostuff.JsonIOUtil;


/**
 * controls processors in yamcs server via websocket
 *
 * @author nm
 *
 */
public class ProcessorControlClient implements ConnectionListener, WebSocketClientCallback, WebSocketResponseHandler {
    YamcsConnector yconnector;
    ProcessorListener yamcsMonitor;


    public ProcessorControlClient(YamcsConnector yconnector) {
        this.yconnector = yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setProcessorListener(ProcessorListener yamcsMonitor) {
        this.yamcsMonitor=yamcsMonitor;
    }

    public void destroyProcessor(String name) throws YamcsApiException {
        // TODO Auto-generated method stub

    }

    public CompletableFuture<byte[]> createProcessor(String instance, String name, String type, Yamcs.ReplayRequest spec, boolean persistent, int[] clients) throws YamcsException, YamcsApiException{
        CreateProcessorRequest.Builder cprb =  CreateProcessorRequest.newBuilder().setName(name).setType(type);
        cprb.setPersistent(persistent);
        for(int cid:clients) {
            cprb.addClientId(cid);
        }
        
        if(spec!=null) {
            StringWriter writer = new StringWriter();
            try {
                JsonIOUtil.writeTo(writer, spec, ReplayRequest.WRITE, false);
            } catch (IOException e) {
                throw new YamcsApiException("Error encoding the request to json", e);
            }
            cprb.setConfig(writer.toString());
        }
        
        
        RestClient restClient = yconnector.getRestClient();
        //POST "/api/processors/:instance"
        String resource = "/processors/"+instance;
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.POST, cprb.build().toByteArray());
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                yamcsMonitor.log("Exception creating processor: "+exception.getMessage());
            }
        });
        return cf;
    }

    public CompletableFuture<Void> connectToProcessor(String instance, String processorName, int[] clients) throws YamcsException, YamcsApiException {
        RestClient restClient = yconnector.getRestClient();
        CompletableFuture<byte[]>[] cfs = new CompletableFuture[clients.length];

        for(int i=0; i<clients.length;i++) {
            //PATCH /api/clients/:id
            String resource = "/clients/"+clients[i]+"?processor="+processorName+"&instance="+instance;
            cfs[i] = restClient.doRequest(resource, HttpMethod.PATCH);
            cfs[i].whenComplete((result, exception) -> {
                if(exception!=null) {
                    yamcsMonitor.log("Exception connecting client to processor: "+exception.getMessage());
                }
            });
        }

        return CompletableFuture.allOf(cfs);
    }

    public void pauseArchiveReplay(String instance, String name) throws YamcsException, YamcsApiException {
        RestClient restClient = yconnector.getRestClient();
        //  PATCH /api/processors/:instance/:name
        String resource = "/processors/"+instance+"/"+name+"?state=PAUSED";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                yamcsMonitor.log("Exception pauysing the processor: "+exception.getMessage());
            }
        });
    }

    public void resumeArchiveReplay(String instance, String name) throws YamcsApiException, YamcsException {
        RestClient restClient = yconnector.getRestClient();
        //  PATCH /api/processors/:instance/:name
        String resource = "/processors/"+instance+"/"+name+"?state=RUNNING";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                yamcsMonitor.log("Exception resuming the processor: "+exception.getMessage());
            }
        });
    }


    public void seekArchiveReplay(String instance, String name, long newPosition) throws YamcsApiException, YamcsException  {
        RestClient restClient = yconnector.getRestClient();
        //  PATCH /api/processors/:instance/:name
        String resource = "/processors/"+instance+"/"+name+"?seek="+TimeEncoding.toString(newPosition);
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                yamcsMonitor.log("Exception seeking the processor: "+exception.getMessage());
            }
        });
    }


    @Override
    public void connecting(String url) { }

    public void receiveInitialConfig() {
        WebSocketRequest wsr = new WebSocketRequest(ManagementResource.RESOURCE_NAME, ManagementResource.OP_subscribe);
        yconnector.performSubscription(wsr, this, this);
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

    @Override
    public void onException(WebSocketExceptionData e) {
        yamcsMonitor.log("Exception when performing subscription:" +e.getMessage());
    }

}