package org.yamcs.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.ClientInfo.ClientState;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.EditClientRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.ListProcessorsResponse;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ProcessorSubscriptionRequest;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.handler.codec.http.HttpMethod;

/**
 * controls processors in yamcs server via websocket
 *
 * @author nm
 *
 */
public class ProcessorControlClient implements ConnectionListener, WebSocketClientCallback, WebSocketResponseHandler {
    YamcsClient client;
    ProcessorListener processorListener;

    public ProcessorControlClient(YamcsClient client) {
        this.client = client;
        client.addConnectionListener(this);
    }

    public void setProcessorListener(ProcessorListener processorListener) {
        this.processorListener = processorListener;
    }

    public void destroyProcessor(String name) throws ClientException {
        // TODO Auto-generated method stub

    }

    public CompletableFuture<byte[]> createProcessor(String instance, String name, String type,
            String spec, boolean persistent, int[] clients) throws ClientException {
        CreateProcessorRequest.Builder cprb = CreateProcessorRequest.newBuilder()
                .setInstance(instance)
                .setName(name)
                .setType(type);
        cprb.setPersistent(persistent);
        for (int cid : clients) {
            cprb.addClientId(cid);
        }

        if (spec != null) {
            cprb.setConfig(spec);
        }

        RestClient restClient = client.getRestClient();

        CompletableFuture<byte[]> cf = restClient.doRequest("/processors", HttpMethod.POST, cprb.build().toByteArray());
        cf.whenComplete((result, exception) -> {
            if (exception != null) {
                processorListener.log("Exception creating processor: " + exception.getMessage());
            }
        });
        return cf;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> connectToProcessor(String instance, String processorName, int[] clients) {
        RestClient restClient = client.getRestClient();
        CompletableFuture<byte[]>[] cfs = new CompletableFuture[clients.length];

        for (int i = 0; i < clients.length; i++) {
            // PATCH /api/clients/:id
            String resource = "/clients/" + clients[i];
            EditClientRequest body = EditClientRequest.newBuilder().setInstance(instance).setProcessor(processorName)
                    .build();
            cfs[i] = restClient.doRequest(resource, HttpMethod.PATCH, body.toByteArray());
            cfs[i].whenComplete((result, exception) -> {
                if (exception != null) {
                    processorListener.log("Exception connecting client to processor: " + exception.getMessage());
                }
            });
        }

        return CompletableFuture.allOf(cfs);
    }

    public void pauseArchiveReplay(String instance, String name) {
        RestClient restClient = client.getRestClient();
        // PATCH /api/processors/:instance/:name
        String resource = "/processors/" + instance + "/" + name;
        EditProcessorRequest body = EditProcessorRequest.newBuilder().setState("paused").build();
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH, body.toByteArray());
        cf.whenComplete((result, exception) -> {
            if (exception != null) {
                processorListener.log("Exception pauysing the processor: " + exception.getMessage());
            }
        });
    }

    public void resumeArchiveReplay(String instance, String name) {
        RestClient restClient = client.getRestClient();
        // PATCH /api/processors/:instance/:name
        String resource = "/processors/" + instance + "/" + name;
        EditProcessorRequest body = EditProcessorRequest.newBuilder().setState("running").build();
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH, body.toByteArray());
        cf.whenComplete((result, exception) -> {
            if (exception != null) {
                processorListener.log("Exception resuming the processor: " + exception.getMessage());
            }
        });
    }

    public void seekArchiveReplay(String instance, String name, long newPosition) {
        RestClient restClient = client.getRestClient();
        // PATCH /api/processors/:instance/:name
        String resource = "/processors/" + instance + "/" + name;
        EditProcessorRequest body = EditProcessorRequest.newBuilder()
                .setSeek(TimeEncoding.toProtobufTimestamp(newPosition))
                .build();
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH, body.toByteArray());
        cf.whenComplete((result, exception) -> {
            if (exception != null) {
                processorListener.log("Exception seeking the processor: " + exception.getMessage());
            }
        });
    }

    @Override
    public void connecting(String url) {
    }

    private void receiveInitialConfig() {
        WebSocketRequest wsr = new WebSocketRequest("management", "subscribe");
        client.performSubscription(wsr, this, this);

        client.getRestClient().doRequest("/processors", HttpMethod.GET).whenComplete((response, exc) -> {
            if (exc == null) {
                try {
                    for (ProcessorInfo pi : ListProcessorsResponse.parseFrom(response).getProcessorsList()) {
                        processorListener.processorUpdated(pi);
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new CompletionException(e);
                }
            }
        });

        ProcessorSubscriptionRequest.Builder optionsb = ProcessorSubscriptionRequest.newBuilder();
        optionsb.setAllInstances(true);
        optionsb.setAllProcessors(true);
        wsr = new WebSocketRequest("processor", "subscribe", optionsb.build());
        client.performSubscription(wsr, this, this);
    }

    @Override
    public void connected(String url) {
        receiveInitialConfig();
    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if (data.hasProcessorInfo()) {
            ProcessorInfo procInfo = data.getProcessorInfo();
            ServiceState servState = procInfo.getState();
            if (servState == ServiceState.TERMINATED || servState == ServiceState.FAILED) {
                processorListener.processorClosed(procInfo);
            } else {
                processorListener.processorUpdated(procInfo);
            }
        }
        if (data.hasClientInfo()) {
            ClientInfo cinfo = data.getClientInfo();
            ClientState cstate = cinfo.getState();
            if (cstate == ClientState.DISCONNECTED) {
                processorListener.clientDisconnected(cinfo);
            } else {
                processorListener.clientUpdated(cinfo);
            }
        }
        if (data.hasStatistics()) {
            Statistics s = data.getStatistics();
            processorListener.updateStatistics(s);
        }
    }

    @Override
    public void connectionFailed(String url, ClientException exception) {
    }

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {
    }

    @Override
    public void onException(WebSocketExceptionData e) {
        processorListener.log("Exception when performing subscription:" + e.getMessage());
    }
}
