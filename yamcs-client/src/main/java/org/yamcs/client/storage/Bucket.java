package org.yamcs.client.storage;

import java.util.concurrent.CompletableFuture;

import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.BucketsApiClient;
import org.yamcs.protobuf.ListObjectsRequest;
import org.yamcs.protobuf.ListObjectsResponse;

public class Bucket {

    private BucketsApiClient bucketService;
    private String bucket;

    Bucket(BucketsApiClient bucketService, String bucket) {
        this.bucketService = bucketService;
        this.bucket = bucket;
    }

    public CompletableFuture<ListObjectsResponse> listObjects(String prefix) {
        ListObjectsRequest.Builder requestb = ListObjectsRequest.newBuilder()
                .setInstance("_global")
                .setBucketName(bucket)
                .setDelimiter("/");
        if (prefix != null) {
            requestb.setPrefix(prefix);
        }
        CompletableFuture<ListObjectsResponse> f = new CompletableFuture<>();
        bucketService.listObjects(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }
}
