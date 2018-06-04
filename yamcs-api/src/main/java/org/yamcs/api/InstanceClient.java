package org.yamcs.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.Rest.ListTablesResponse;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.handler.codec.http.HttpMethod;

public class InstanceClient {

    private String instance;
    private RestClient restClient;

    public InstanceClient(String instance, YamcsClient yamcsClient) {
        this.instance = instance;
        restClient = yamcsClient.getRestClient();
    }

    public String getInstance() {
        return instance;
    }

    public CompletableFuture<ListServiceInfoResponse> getServices() {
        String url = "/services/" + instance;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListServiceInfoResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> editService(String service, EditServiceRequest options) {
        String url = "/services/" + instance + "/" + service;
        byte[] body = options.toByteArray();
        return restClient.doRequest(url, HttpMethod.PATCH, body).thenApply(response -> null);
    }

    public CompletableFuture<ListTablesResponse> getTables() {
        String url = "/archive/" + instance + "/tables";
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListTablesResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }
}
