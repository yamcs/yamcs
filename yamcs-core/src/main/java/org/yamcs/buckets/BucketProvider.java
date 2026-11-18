package org.yamcs.buckets;

import java.io.IOException;
import java.util.List;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

/**
 * Provides buckets stored outside of Yamcs.
 * <p>
 * A new instance is created for each {@code bucketProviders} entry in {@code yamcs.yaml}, so implementations need a
 * public no-argument constructor, and may keep state related to their entry (for example a client) in fields.
 */
public interface BucketProvider {
    /**
     * Description of provider location
     */
    BucketLocation getLocation();

    /**
     * Returns the valid configuration options for this plugin.
     */
    Spec getSpec();

    /**
     * Called on Yamcs startup, once for this provider entry. Provider should create and return all buckets that match
     * the provided configuration.
     * <p>
     * Implementations do not need to verify bucket uniqueness.
     */
    List<Bucket> loadBuckets(YConfiguration config) throws IOException;

    /**
     * Called when a new bucket is to be created, if this provider entry has {@code createBuckets: true}. This happens
     * for example on bucket creation through the HTTP API, or when a component needs a bucket that does not yet exist.
     * <p>
     * If the bucket should survive a server restart, the provider must return it from
     * {@link #loadBuckets(YConfiguration)} on the next start.
     * <p>
     * Implementations do not need to verify bucket uniqueness.
     *
     * @param bucketName
     *            the name of the bucket to create
     * @throws UnsupportedOperationException
     *             if this provider does not support creating buckets (default)
     */
    default Bucket createBucket(String bucketName) throws IOException {
        throw new UnsupportedOperationException(
                "Bucket provider '" + getLocation().name() + "' does not support creating buckets");
    }

    /**
     * Called when a bucket of this provider is to be deleted, for example through the HTTP API. The provider should
     * delete the bucket, including its content, such that it is no longer returned from
     * {@link #loadBuckets(YConfiguration)} on the next start.
     *
     * @param bucket
     *            the bucket to delete
     * @throws UnsupportedOperationException
     *             if this provider does not support deleting buckets (default)
     */
    default void deleteBucket(Bucket bucket) throws IOException {
        throw new UnsupportedOperationException(
                "Bucket provider '" + getLocation().name() + "' does not support deleting buckets");
    }
}
