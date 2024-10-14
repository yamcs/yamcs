package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;

import org.yamcs.buckets.Bucket;

public interface BucketDatabase {
    Bucket createBucket(String bucketName) throws IOException;

    /**
     * Retrieve a bucket handler from the database.
     * 
     * @return the bucket with the given name or null if it does not exist
     */
    Bucket getBucket(String bucketName) throws IOException;

    List<? extends Bucket> listBuckets() throws IOException;

    void deleteBucket(String bucketName) throws IOException;
}
