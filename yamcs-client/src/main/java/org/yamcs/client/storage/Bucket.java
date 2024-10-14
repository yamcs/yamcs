package org.yamcs.client.storage;

import java.util.concurrent.CompletableFuture;

import org.yamcs.api.HttpBody;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.storage.Bucket.ListObjectsOptions.DelimiterOption;
import org.yamcs.client.storage.Bucket.ListObjectsOptions.ListObjectsOption;
import org.yamcs.client.storage.Bucket.ListObjectsOptions.PrefixOption;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.BucketsApiClient;
import org.yamcs.protobuf.DeleteObjectRequest;
import org.yamcs.protobuf.GetBucketRequest;
import org.yamcs.protobuf.GetObjectInfoRequest;
import org.yamcs.protobuf.GetObjectRequest;
import org.yamcs.protobuf.ListObjectsRequest;
import org.yamcs.protobuf.ListObjectsResponse;
import org.yamcs.protobuf.ObjectInfo;
import org.yamcs.protobuf.UploadObjectRequest;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class Bucket {

    private BucketsApiClient bucketService;
    private String bucket;

    Bucket(BucketsApiClient bucketService, String bucket) {
        this.bucketService = bucketService;
        this.bucket = bucket;
    }

    public String getName() {
        return bucket;
    }

    public CompletableFuture<BucketInfo> getInfo() {
        var requestb = GetBucketRequest.newBuilder()
                .setBucketName(bucket);
        var f = new CompletableFuture<BucketInfo>();
        bucketService.getBucket(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    @Deprecated
    public CompletableFuture<ListObjectsResponse> listObjects(String prefix) {
        return listObjects(
                ListObjectsOptions.delimiter("/"),
                ListObjectsOptions.prefix(prefix));
    }

    public CompletableFuture<ListObjectsResponse> listObjects(ListObjectsOption... options) {
        var requestb = ListObjectsRequest.newBuilder()
                .setBucketName(bucket);
        for (var option : options) {
            if (option instanceof DelimiterOption o) {
                if (o.delimiter != null) {
                    requestb.setDelimiter(o.delimiter);
                }
            } else if (option instanceof PrefixOption o) {
                if (o.prefix != null) {
                    requestb.setPrefix(o.prefix);
                }
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        var f = new CompletableFuture<ListObjectsResponse>();
        bucketService.listObjects(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ObjectInfo> getObject(String objectName) {
        var requestb = GetObjectInfoRequest.newBuilder()
                .setBucketName(bucket)
                .setObjectName(objectName);
        var f = new CompletableFuture<ObjectInfo>();
        bucketService.getObjectInfo(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> uploadObject(String objectName, byte[] bytes) {
        var request = UploadObjectRequest.newBuilder()
                .setBucketName(bucket)
                .setObjectName(objectName)
                .setData(HttpBody.newBuilder().setData(ByteString.copyFrom(bytes)))
                .build();
        var f = new CompletableFuture<Empty>();
        bucketService.uploadObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<byte[]> downloadObject(String objectName) {
        var request = GetObjectRequest.newBuilder()
                .setBucketName(bucket)
                .setObjectName(objectName)
                .build();
        var f = new CompletableFuture<HttpBody>();
        bucketService.getObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getData().toByteArray());
    }

    public CompletableFuture<Void> deleteObject(String objectName) {
        var request = DeleteObjectRequest.newBuilder()
                .setBucketName(bucket)
                .setObjectName(objectName)
                .build();
        var f = new CompletableFuture<Empty>();
        bucketService.deleteObject(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public static final class ListObjectsOptions {

        public static interface ListObjectsOption {
        }

        public static ListObjectsOption prefix(String prefix) {
            return new PrefixOption(prefix);
        }

        public static ListObjectsOption delimiter(String delimiter) {
            return new DelimiterOption(delimiter);
        }

        static final class PrefixOption implements ListObjectsOption {
            final String prefix;

            public PrefixOption(String prefix) {
                this.prefix = prefix;
            }
        }

        static final class DelimiterOption implements ListObjectsOption {
            final String delimiter;

            public DelimiterOption(String delimiter) {
                this.delimiter = delimiter;
            }
        }
    }
}
