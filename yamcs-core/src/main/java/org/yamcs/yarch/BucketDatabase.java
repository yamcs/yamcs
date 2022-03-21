package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;

public interface BucketDatabase {
    Bucket createBucket(String bucketName) throws IOException;

    /**
     * Retrieve a bucket handler from the database.
     * 
     * @param bucketName
     * @return the bucket with the given name or null if it does not exist
     * @throws IOException
     */
    Bucket getBucket(String bucketName) throws IOException;

    List<Bucket> listBuckets() throws IOException;

    void deleteBucket(String bucketName) throws IOException;
}
