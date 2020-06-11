package org.yamcs.client.base;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.yamcs.api.AnnotationsProto;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.Observer;
import org.yamcs.client.ClientException;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringEncoder;

/**
 * A message observer that implements a streaming request over HTTP using chunked transfer encoding.
 */
public class ClientStreamingObserver implements Observer<Message> {

    private MethodDescriptor method;
    private RestClient baseClient;
    private Message responsePrototype;
    private Observer<Message> responseObserver;
    private FieldDescriptor bodyField;

    private BulkRestDataSender sender;

    public ClientStreamingObserver(MethodDescriptor method, RestClient baseClient, Message responsePrototype,
            Observer<Message> responseObserver) {
        this.method = method;
        this.baseClient = baseClient;
        this.responsePrototype = responsePrototype;
        this.responseObserver = responseObserver;

        HttpRoute route = method.getOptions().getExtension(AnnotationsProto.route);

        if (!route.hasBody()) {
            throw new IllegalArgumentException("Route does not accept request bodies");
        }
        if (!"*".equals(route.getBody())) {
            bodyField = method.getInputType().findFieldByName(route.getBody());
        }
    }

    @Override
    public synchronized void next(Message message) { // Synchronize so that sender is available after the first request
        if (sender == null) {
            HttpRoute route = method.getOptions().getExtension(AnnotationsProto.route);

            // Holder for extracting route and query params
            Message.Builder partial = message.toBuilder();

            HttpMethod httpMethod = HttpMethodHandler.getMethod(route);
            String uriTemplate = HttpMethodHandler.getPattern(route);
            QueryStringEncoder uri = HttpMethodHandler.resolveUri(uriTemplate, message, method.getInputType(), partial);
            message = partial.buildPartial();
            try {
                sender = baseClient.doBulkSendRequest(uri.toString(), httpMethod).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                cancel(e.getCause());
            }
        } else { // First message is always the initial request setup (no payload)
            if (bodyField != null) {
                message = (Message) message.getField(bodyField);
            }
            try {
                sender.sendData(delimit(message));
            } catch (ClientException e) {
                cancel(e);
            }
        }
    }

    private static byte[] delimit(Message message) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            message.writeDelimitedTo(bout);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return bout.toByteArray();
    }

    private void cancel(Throwable reason) {
        // TODO
        throw new RuntimeException(reason);
    }

    @Override
    public void completeExceptionally(Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void complete() {
        sender.completeRequest().whenComplete((data, err) -> {
            if (err == null) {
                Message response;
                try {
                    response = responsePrototype.newBuilderForType().mergeFrom(data).build();
                    responseObserver.complete(response);
                } catch (InvalidProtocolBufferException e) {
                    throw new IllegalArgumentException(e);
                }
            } else {
                responseObserver.completeExceptionally(err);
            }
        });
    }
}
