package org.yamcs.client.storage;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies an object in a bucket
 */
public class ObjectId {

    private static Pattern URL_PATTERN = Pattern.compile("ys://([^\\/]+)/(.+)");

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
        if (!(obj instanceof ObjectId)) {
            return false;
        }
        ObjectId other = (ObjectId) obj;
        return Objects.equals(instance, other.instance)
                && Objects.equals(bucket, other.bucket)
                && Objects.equals(objectName, other.objectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, bucket, objectName);
    }

    @Override
    public String toString() {
        return String.format("ys://%s/%s", bucket, objectName);
    }

    public static ObjectId of(String bucket, String objectName) {
        return new ObjectId("_global", bucket, objectName);
    }

    public static ObjectId of(String instance, String bucket, String objectName) {
        return new ObjectId(instance, bucket, objectName);
    }

    /**
     * Parses a URL of the form {@code ys://my-bucket/some/file.txt}
     */
    public static ObjectId parseURL(String url) {
        return parseURL("_global", url);
    }

    public static ObjectId parseURL(String instance, String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            return of(instance, matcher.group(1), matcher.group(2));
        } else {
            throw new IllegalArgumentException("Invalid object URL '" + url + "'");
        }
    }
}
