package org.yamcs.client.storage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.HttpBody;
import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.BucketsApiClient;
import org.yamcs.protobuf.CreateBucketRequest;
import org.yamcs.protobuf.DeleteBucketRequest;
import org.yamcs.protobuf.GetObjectRequest;
import org.yamcs.protobuf.ListBucketsRequest;
import org.yamcs.protobuf.ListBucketsResponse;
import org.yamcs.protobuf.UploadObjectRequest;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class StorageClient {

    private BucketsApiClient bucketService;

    public StorageClient(MethodHandler handler) {
        bucketService = new BucketsApiClient(handler);
    }

    public CompletableFuture<Bucket> createBucket(String bucketName) {
        var request = CreateBucketRequest.newBuilder()
                .setName(bucketName)
                .build();
        var f = new CompletableFuture<BucketInfo>();
        bucketService.createBucket(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> getBucket(response.getName()));
    }

    public CompletableFuture<Void> deleteBucket(String bucketName) {
        var request = DeleteBucketRequest.newBuilder()
                .setBucketName(bucketName)
                .build();
        var f = new CompletableFuture<Empty>();
        bucketService.deleteBucket(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<Void> uploadObject(ObjectId target, byte[] bytes) {
        var request = UploadObjectRequest.newBuilder()
                .setBucketName(target.getBucket())
                .setObjectName(target.getObjectName())
                .setData(HttpBody.newBuilder().setData(ByteString.copyFrom(bytes)))
                .build();
        var f = new CompletableFuture<Empty>();
        bucketService.uploadObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<byte[]> downloadObject(ObjectId source) {
        var request = GetObjectRequest.newBuilder()
                .setBucketName(source.getBucket())
                .setObjectName(source.getObjectName())
                .build();
        var f = new CompletableFuture<HttpBody>();
        bucketService.getObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getData().toByteArray());
    }

    public CompletableFuture<List<BucketInfo>> listBuckets() {
        var request = ListBucketsRequest.newBuilder().build();
        var f = new CompletableFuture<ListBucketsResponse>();
        bucketService.listBuckets(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getBucketsList());
    }

    public Bucket getBucket(String bucket) {
        return new Bucket(bucketService, bucket);
    }
}
