package org.yamcs.buckets;

import java.util.Map;

import org.yamcs.protobuf.ObjectInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace;

public record ObjectProperties(
        /**
         * Object name
         */
        String name,

        /**
         * Content type
         */
        String contentType,

        /**
         * Creation date
         */
        long created,

        /**
         * Size in bytes
         */
        long size,

        /**
         * Object metadata
         */
        Map<String, String> metadata) {

    public static ObjectProperties fromObjectInfo(ObjectInfo info) {
        return new ObjectProperties(
                info.getName(),
                info.hasContentType() ? info.getContentType() : null,
                TimeEncoding.fromProtobufTimestamp(info.getCreated()),
                info.getSize(),
                info.getMetadataMap());
    }

    public static ObjectProperties fromYarch(Tablespace.ObjectPropertiesOrBuilder props) {
        return new ObjectProperties(
                props.getName(),
                props.getContentType(),
                props.getCreated(),
                props.getSize(),
                props.getMetadataMap());
    }
}
