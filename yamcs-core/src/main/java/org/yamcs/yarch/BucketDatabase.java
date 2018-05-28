package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;

public interface BucketDatabase {
    Bucket createBucket(String bucketName) throws IOException;
    Bucket getBucket(String bucketName);
    List<BucketProperties> listBuckets();
    void deleteBucket(String bucketName) throws IOException;
}
