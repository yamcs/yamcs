package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;

public interface Bucket {

    /**
     * This bucket's name
     */
    String getName();

    BucketProperties getProperties() throws IOException;

    /**
     * Update the size limit for this bucket.
     * <p>
     * If the specified size is smaller than the current size, the bucket will no longer accept new files.
     */
    void setMaxSize(long maxSize) throws IOException;

    /**
     * Update the object count limit for this bucket.
     * <p>
     * If the specified count is smaller than the current count, the bucket will no longer accept new files.
     */
    void setMaxObjects(int maxObjects) throws IOException;

    default List<ObjectProperties> listObjects() throws IOException {
        return listObjects(null, x -> true);
    }

    default List<ObjectProperties> listObjects(String prefix) throws IOException {
        return listObjects(prefix, x -> true);
    }

    default List<ObjectProperties> listObjects(Predicate<ObjectPropertiesOrBuilder> p) throws IOException {
        return listObjects(null, p);
    }

    /**
     * retrieve objects whose name start with prefix and that match the condition Note that searching by prefix is
     * cheap, the condition will be evaluated for all objects that match the prefix
     * 
     * @param prefix
     * @param p
     *            predicate to be matched by the returned objects
     * @return list of objects
     * @throws IOException
     */
    List<ObjectProperties> listObjects(String prefix, Predicate<ObjectPropertiesOrBuilder> p) throws IOException;

    void putObject(String objectName, String contentType, Map<String, String> metadata, byte[] objectData)
            throws IOException;

    /**
     * Retrieve object from the bucket. Returns null if object does not exist.
     * 
     * @param objectName
     * @return
     * @throws IOException
     */
    byte[] getObject(String objectName) throws IOException;

    void deleteObject(String objectName) throws IOException;

    /**
     * retrieve the object properties or null if not such an object exist
     * 
     * @param objectName
     * @return
     * @throws IOException
     */
    ObjectProperties findObject(String objectName) throws IOException;

}
