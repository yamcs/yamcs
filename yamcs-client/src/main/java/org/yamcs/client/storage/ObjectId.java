package org.yamcs.client.storage;

import java.util.Objects;

/**
 * Identifies an object in a bucket
 */
public class ObjectId {

    private final String instance;
    private final String bucket;
    private final String objectName;

    private ObjectId(String instance, String bucket, String objectName) {
        this.instance = Objects.requireNonNull(instance);
        this.bucket = Objects.requireNonNull(bucket);
        this.objectName = Objects.requireNonNull(objectName);
    }

    public String getInstance() {
        return instance;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectName() {
        return objectName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ObjectId)) {
            return false;
        }
        ObjectId other = (ObjectId) obj;
        return Objects.equals(bucket, other.bucket)
                && Objects.equals(objectName, other.objectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucket, objectName);
    }

    public static ObjectId of(String bucket, String objectName) {
        return new ObjectId("_global", bucket, objectName);
    }

    public static ObjectId of(String instance, String bucket, String objectName) {
        return new ObjectId(instance, bucket, objectName);
    }
}
