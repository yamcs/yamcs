package org.yamcs.buckets;

import com.google.common.base.CaseFormat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public abstract class Bucket {

    /**
     * Bucket specific access rules: each AccessRuleType, maps user roles to a list of path patterns
     * If a certain AccessRuleType is absent, it means no rule was set, and the old bucket-wide permissions get used
     */
    private final Map<AccessRuleType, Map<String, List<String>>> accessRules = new HashMap<>();

    public enum AccessRuleType {
        READ,
        WRITE;

        private final String configName;

        AccessRuleType() {
            configName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
        }

        public String getConfigName() {
            return configName;
        }
    }

    /**
     * Bucket location
     */
    public abstract BucketLocation getLocation();

    /**
     * This bucket's name
     */
    public abstract String getName();

    public abstract CompletableFuture<BucketProperties> getPropertiesAsync();

    /**
     * Update the size limit for this bucket.
     * <p>
     * If the specified size is smaller than the current size, the bucket will no longer accept new files.
     */
    public abstract void setMaxSize(long maxSize) throws IOException;

    /**
     * Update the object count limit for this bucket.
     * <p>
     * If the specified count is smaller than the current count, the bucket will no longer accept new files.
     */
    public abstract void setMaxObjects(int maxObjects) throws IOException;

    public CompletableFuture<List<ObjectProperties>> listObjectsAsync() {
        return listObjectsAsync(null, x -> true);
    }

    public CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix) {
        return listObjectsAsync(prefix, x -> true);
    }

    public CompletableFuture<List<ObjectProperties>> listObjectsAsync(Predicate<ObjectProperties> p) {
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
    public abstract CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix,
            Predicate<ObjectProperties> p);

    public abstract CompletableFuture<Void> putObjectAsync(String objectName, String contentType,
            Map<String, String> metadata,
            byte[] objectData);

    /**
     * Retrieve object from the bucket. Returns null if object does not exist.
     */
    public abstract CompletableFuture<byte[]> getObjectAsync(String objectName);

    public abstract CompletableFuture<Void> deleteObjectAsync(String objectName);

    /**
     * Retrieve the object properties or null if not such an object exist
     */
    public abstract CompletableFuture<ObjectProperties> findObjectAsync(String objectName);

    /**
     * Sets bucket object access rules
     * @param accessRules map of roles to a list of allowed path regexes
     */
    public void setAccessRules(Map<AccessRuleType, Map<String, List<String>>> accessRules) {
        this.accessRules.putAll(accessRules);
    }

    public Map<String, List<String>> getAccessRules(AccessRuleType type) {
        return accessRules.get(type);
    }

    public boolean hasAccessRules(AccessRuleType type) {
        return accessRules.containsKey(type);
    }
}
