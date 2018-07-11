package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;

public interface Bucket {

    /**
     * get the bucket name
     * 
     * @return
     */
    String getName();

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
