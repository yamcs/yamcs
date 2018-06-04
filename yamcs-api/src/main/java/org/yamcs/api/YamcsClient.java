package org.yamcs.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListBucketsResponse;
import org.yamcs.protobuf.Rest.ListObjectsResponse;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.handler.codec.http.HttpMethod;

/**
 * WIP
 * <p>
 * Intended to provide a typed java-based client for use in CLI or other clients.
 */
public class YamcsClient {

    private RestClient restClient;

    public YamcsClient(YamcsConnectionProperties yprops) {
        restClient = new RestClient(yprops);
    }

    public InstanceClient selectInstance(String instance) {
        return new InstanceClient(instance, this);
    }

    public CompletableFuture<ListServiceInfoResponse> getServices() {
        return restClient.doRequest("/services/_global", HttpMethod.GET).thenApply(response -> {
            try {
                return ListServiceInfoResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> editService(String service, EditServiceRequest options) {
        String url = "/services/_global/" + service;
        byte[] body = options.toByteArray();
        return restClient.doRequest(url, HttpMethod.PATCH, body).thenApply(response -> null);
    }

    public CompletableFuture<ListBucketsResponse> getBuckets() {
        String url = "/buckets/_global";
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListBucketsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<ListObjectsResponse> getObjects(String bucket) {
        String url = "/buckets/_global/" + bucket;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListObjectsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<byte[]> getObject(String bucket, String object) {
        String url = "/buckets/_global/" + bucket + "/" + object;
        return restClient.doRequest(url, HttpMethod.GET);
    }

    RestClient getRestClient() {
        return restClient;
    }
}
