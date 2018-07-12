package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;

public interface BucketDatabase {
    Bucket createBucket(String bucketName) throws IOException;

    Bucket getBucket(String bucketName) throws IOException;

    List<BucketProperties> listBuckets() throws IOException;

    void deleteBucket(String bucketName) throws IOException;
}
