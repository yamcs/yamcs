package org.yamcs.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.EditClientRequest;
import org.yamcs.protobuf.EditProcessorRequest;
import org.yamcs.protobuf.ListProcessorsResponse;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.SubscribeProcessorsRequest;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.handler.codec.http.HttpMethod;

/**
 * controls processors in yamcs server via websocket
 *
 * @author nm
 *
 */
public class ProcessorControlClient implements ConnectionListener, WebSocketResponseHandler {

    private static final Logger log = Logger.getLogger(ProcessorControlClient.class.getName());
    private YamcsClient client;

    private ProcessorListener processorListener;

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
                log.log(Level.SEVERE, "Exception creating processor", exception);
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
                    log.log(Level.SEVERE, "Exception connecting client to processor", exception);
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
                log.log(Level.SEVERE, "Exception pausing the processor", exception);
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
                log.log(Level.SEVERE, "Exception resuming processor", exception);
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
                log.log(Level.SEVERE, "Exception while seeking processor", exception);
            }
        });
    }

    @Override
    public void connecting(String url) {
    }

    @Override
    public void connected(String url) {
        ProcessorSubscription subscription = client.createProcessorSubscription();
        subscription.addMessageListener(processor -> {
            ServiceState servState = processor.getState();
            if (servState == ServiceState.TERMINATED || servState == ServiceState.FAILED) {
                processorListener.processorClosed(processor);
            } else {
                processorListener.processorUpdated(processor);
            }
        });
        subscription.sendMessage(SubscribeProcessorsRequest.getDefaultInstance());

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
        log.log(Level.SEVERE, "Exception when performing subscription", e);
    }
}
