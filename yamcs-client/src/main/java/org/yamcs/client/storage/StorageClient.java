package org.yamcs.client.storage;

import java.util.concurrent.CompletableFuture;

import org.yamcs.api.HttpBody;
import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.BucketsApiClient;
import org.yamcs.protobuf.GetObjectRequest;
import org.yamcs.protobuf.UploadObjectRequest;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class StorageClient {

    private BucketsApiClient bucketService;

    public StorageClient(MethodHandler handler) {
        bucketService = new BucketsApiClient(handler);
    }

    public CompletableFuture<Void> uploadObject(ObjectId target, byte[] bytes) {
        UploadObjectRequest request = UploadObjectRequest.newBuilder()
                .setInstance(target.getInstance())
                .setBucketName(target.getBucket())
                .setObjectName(target.getObjectName())
                .setData(HttpBody.newBuilder().setData(ByteString.copyFrom(bytes)))
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        bucketService.uploadObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<byte[]> downloadObject(ObjectId source) {
        GetObjectRequest request = GetObjectRequest.newBuilder()
                .setInstance(source.getInstance())
                .setBucketName(source.getBucket())
                .setObjectName(source.getObjectName())
                .build();
        CompletableFuture<HttpBody> f = new CompletableFuture<>();
        bucketService.getObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getData().toByteArray());
    }

    public Bucket getBucket(String bucket) {
        return new Bucket(bucketService, bucket);
    }
}
