package org.yamcs.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.CreateBucketRequest;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListBucketsResponse;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.Rest.ListObjectsResponse;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

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

    public CompletableFuture<ListInstancesResponse> getInstances() {
        return restClient.doRequest("/instances", HttpMethod.GET).thenApply(response -> {
            try {
                return ListInstancesResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Message> T decode(byte[] data, T defaultInstance) throws CompletionException {
        try {
            return (T) defaultInstance.newBuilderForType().mergeFrom(data).build();
        } catch (InvalidProtocolBufferException e) {
            throw new CompletionException(e);
        }
    }

    public CompletableFuture<YamcsInstance> getInstance(String instance) {
        return restClient.doRequest("/instances/" + instance, HttpMethod.GET)
                .thenApply(response -> decode(response, YamcsInstance.getDefaultInstance()));
    }

    public CompletableFuture<ListClientsResponse> getClients() {
        return restClient.doRequest("/clients", HttpMethod.GET).thenApply(response -> {
            try {
                return ListClientsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<ClientInfo> getClients(int clientId) {
        return restClient.doRequest("/clients/" + clientId, HttpMethod.GET).thenApply(response -> {
            try {
                return ClientInfo.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<ListProcessorsResponse> getProcessors() {
        return restClient.doRequest("/processors", HttpMethod.GET).thenApply(response -> {
            try {
                return ListProcessorsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
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

    public CompletableFuture<ServiceInfo> getService(String service) {
        String url = "/services/_global/" + service;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ServiceInfo.parseFrom(response);
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

    public CompletableFuture<Void> createBucket(CreateBucketRequest options) {
        String url = "/buckets/_global";
        byte[] body = options.toByteArray();
        return restClient.doRequest(url, HttpMethod.POST, body).thenApply(response -> null);
    }

    public CompletableFuture<Void> deleteBucket(String name) {
        String url = "/buckets/_global/" + name;
        return restClient.doRequest(url, HttpMethod.DELETE).thenApply(response -> null);
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
