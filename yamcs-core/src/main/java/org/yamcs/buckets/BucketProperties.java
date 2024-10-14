package org.yamcs.buckets;

import org.yamcs.protobuf.BucketInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace;

public record BucketProperties(
        /**
         * Bucket name
         */
        String name,

        /**
         * Bucket creation date
         */
        long created,

        /**
         * Maximum number of objects in this bucket
         */
        int maxNumObjects,

        /**
         * Maximum size in bytes of this bucket
         */
        long maxSize,

        /**
         * Current number of objects in this bucket
         */
        int numObjects,

        /**
         * Current size in bytes of this bucket
         */
        long size) {

    public static BucketProperties fromBucketInfo(BucketInfo info) {
        return new BucketProperties(
                info.getName(),
                TimeEncoding.fromProtobufTimestamp(info.getCreated()),
                info.getMaxObjects(),
                info.getMaxSize(),
                info.getNumObjects(),
                info.getSize());
    }

    public static BucketProperties fromYarch(Tablespace.BucketProperties props) {
        return new BucketProperties(
                props.getName(),
                props.getCreated(),
                props.getMaxNumObjects(),
                props.getMaxSize(),
                props.getNumObjects(),
                props.getSize());
    }
}
