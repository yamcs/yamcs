package org.yamcs.buckets;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public interface Bucket {

    /**
     * Bucket location
     */
    BucketLocation getLocation();

    /**
     * This bucket's name
     */
    String getName();

    CompletableFuture<BucketProperties> getPropertiesAsync();

    /**
     * Update the size limit for this bucket.
     * <p>
     * If the specified size is smaller than the current size, the bucket will no longer accept new files.
     */
    void setMaxSize(long maxSize) throws IOException;

    /**
     * Update the object count limit for this bucket.
     * <p>
     * If the specified count is smaller than the current count, the bucket will no longer accept new files.
     */
    void setMaxObjects(int maxObjects) throws IOException;

    default CompletableFuture<List<ObjectProperties>> listObjectsAsync() {
        return listObjectsAsync(null, x -> true);
    }

    default CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix) {
        return listObjectsAsync(prefix, x -> true);
    }

    default CompletableFuture<List<ObjectProperties>> listObjectsAsync(Predicate<ObjectProperties> p) {
        return listObjectsAsync(null, p);
    }

    /**
     * retrieve objects whose name start with prefix and that match the condition Note that searching by prefix is
     * cheap, the condition will be evaluated for all objects that match the prefix
     * 
     * @param prefix
     * @param p
     *            predicate to be matched by the returned objects
     * @return list of objects
     */
    CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix, Predicate<ObjectProperties> p);

    CompletableFuture<Void> putObjectAsync(String objectName, String contentType, Map<String, String> metadata,
            byte[] objectData);

    /**
     * Retrieve object from the bucket. Returns null if object does not exist.
     */
    CompletableFuture<byte[]> getObjectAsync(String objectName);

    CompletableFuture<Void> deleteObjectAsync(String objectName);

    /**
     * Retrieve the object properties or null if not such an object exist
     */
    CompletableFuture<ObjectProperties> findObjectAsync(String objectName);

}
