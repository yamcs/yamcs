package org.yamcs.buckets;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.yamcs.client.storage.Bucket.ListObjectsOptions;

public class RemoteYamcsBucket extends Bucket {

    public static final BucketLocation LOCATION = new BucketLocation("remote-yamcs", "Remote Yamcs");

    private String localName;
    private org.yamcs.client.storage.Bucket bucketClient;

    public RemoteYamcsBucket(String localName, org.yamcs.client.storage.Bucket bucketClient) {
        this.localName = localName;
        this.bucketClient = bucketClient;
    }

    @Override
    public BucketLocation getLocation() {
        return LOCATION;
    }

    @Override
    public String getName() {
        return localName;
    }

    @Override
    public CompletableFuture<BucketProperties> getPropertiesAsync() {
        return bucketClient.getInfo().thenApply(BucketProperties::fromBucketInfo);
    }

    @Override
    public void setMaxSize(long maxSize) throws IOException {
        // Ignore, managed by remote
    }

    @Override
    public void setMaxObjects(int maxObjects) throws IOException {
        // Ignore, managed by remote
    }

    @Override
    public CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix, Predicate<ObjectProperties> p) {
        return bucketClient.listObjects(ListObjectsOptions.prefix(prefix)).thenApply(response -> {
            return response.getObjectsList().stream()
                    .map(ObjectProperties::fromObjectInfo)
                    .filter(p::test)
                    .toList();
        });
    }

    @Override
    public CompletableFuture<Void> putObjectAsync(String objectName, String contentType, Map<String, String> metadata,
            byte[] objectData) {
        return bucketClient.uploadObject(objectName, objectData);
    }

    @Override
    public CompletableFuture<byte[]> getObjectAsync(String objectName) {
        return bucketClient.downloadObject(objectName);
    }

    @Override
    public CompletableFuture<Void> deleteObjectAsync(String objectName) {
        return bucketClient.deleteObject(objectName);
    }

    @Override
    public CompletableFuture<ObjectProperties> findObjectAsync(String objectName) {
        return bucketClient.getObject(objectName)
                .thenApply(ObjectProperties::fromObjectInfo);
    }
}
