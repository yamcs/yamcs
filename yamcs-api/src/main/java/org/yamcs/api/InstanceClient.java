package org.yamcs.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.CreateBucketRequest;
import org.yamcs.protobuf.Rest.EditLinkRequest;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListBucketsResponse;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListLinkInfoResponse;
import org.yamcs.protobuf.Rest.ListObjectsResponse;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.Rest.ListTablesResponse;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;

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

    public CompletableFuture<ListClientsResponse> getClients() {
        return restClient.doRequest("/instances/" + instance + "/clients", HttpMethod.GET).thenApply(response -> {
            try {
                return ListClientsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<ListProcessorsResponse> getProcessors() {
        return restClient.doRequest("/processors/" + instance, HttpMethod.GET).thenApply(response -> {
            try {
                return ListProcessorsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<ProcessorInfo> getProcessor(String name) {
        String url = "/processors/" + instance + "/" + name;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ProcessorInfo.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
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

    public CompletableFuture<ServiceInfo> getService(String service) {
        String url = "/services/" + instance + "/" + service;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ServiceInfo.parseFrom(response);
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

    public CompletableFuture<ListLinkInfoResponse> getLinks() {
        String url = "/links/" + instance;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListLinkInfoResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<LinkInfo> getLink(String link) {
        String url = "/links/" + instance + "/" + link;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return LinkInfo.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> editLink(String link, EditLinkRequest options) {
        String url = "/links/" + instance + "/" + link;
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

    public CompletableFuture<ListBucketsResponse> getBuckets() {
        String url = "/buckets/" + instance;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListBucketsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> createBucket(CreateBucketRequest options) {
        String url = "/buckets/" + instance;
        byte[] body = options.toByteArray();
        return restClient.doRequest(url, HttpMethod.POST, body).thenApply(response -> null);
    }

    public CompletableFuture<Void> deleteBucket(String name) {
        String url = "/buckets/" + instance + "/" + name;
        return restClient.doRequest(url, HttpMethod.DELETE).thenApply(response -> null);
    }

    public CompletableFuture<ListObjectsResponse> getObjects(String bucket) {
        String url = "/buckets/" + instance + "/" + bucket;
        return restClient.doRequest(url, HttpMethod.GET).thenApply(response -> {
            try {
                return ListObjectsResponse.parseFrom(response);
            } catch (InvalidProtocolBufferException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<byte[]> getObject(String bucket, String object) {
        String url = "/buckets/" + instance + "/" + bucket + "/" + object;
        return restClient.doRequest(url, HttpMethod.GET);
    }
}
