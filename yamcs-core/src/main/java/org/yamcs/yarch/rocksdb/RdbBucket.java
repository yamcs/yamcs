package org.yamcs.yarch.rocksdb;

import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.yarch.rocksdb.RdbBucketDatabase.TYPE_OBJ_DATA;
import static org.yamcs.yarch.rocksdb.RdbBucketDatabase.TYPE_OBJ_METADATA;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.buckets.Bucket;
import org.yamcs.buckets.BucketLocation;
import org.yamcs.buckets.BucketProperties;
import org.yamcs.buckets.ObjectProperties;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import com.google.protobuf.InvalidProtocolBufferException;

public class RdbBucket extends Bucket {

    private static final Logger log = LoggerFactory.getLogger(RdbBucket.class);
    private static final BucketLocation LOCATION = new BucketLocation("db", "Yamcs DB");

    final int tbsIndex;
    org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties bucketProps;
    final Tablespace tablespace;
    final String yamcsInstance;

    public RdbBucket(String yamcsInstance, Tablespace tablespace, int tbsIndex,
            org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties bucketProps) {
        this.yamcsInstance = yamcsInstance;
        this.tbsIndex = tbsIndex;
        this.bucketProps = bucketProps;
        this.tablespace = tablespace;
    }

    @Override
    public BucketLocation getLocation() {
        return LOCATION;
    }

    @Override
    public CompletableFuture<BucketProperties> getPropertiesAsync() {
        var properties = getProperties();
        return CompletableFuture.completedFuture(properties);
    }

    public BucketProperties getProperties() {
        return BucketProperties.fromYarch(bucketProps);
    }

    @Override
    public void setMaxSize(long maxSize) throws IOException {
        if (maxSize != bucketProps.getMaxSize()) {
            var updatedProps = org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties.newBuilder()
                    .mergeFrom(bucketProps)
                    .setMaxSize(maxSize)
                    .build();
            try {
                saveUpdatedBucketProperties(updatedProps);
            } catch (RocksDBException e) {
                throw new IOException("Error writing bucket properties: " + e.toString(), e);
            }
        }
    }

    @Override
    public void setMaxObjects(int maxObjects) throws IOException {
        if (maxObjects != bucketProps.getMaxNumObjects()) {
            var updatedProps = org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties.newBuilder()
                    .mergeFrom(bucketProps)
                    .setMaxNumObjects(maxObjects)
                    .build();
            try {
                saveUpdatedBucketProperties(updatedProps);
            } catch (RocksDBException e) {
                throw new IOException("Error writing bucket properties: " + e.toString(), e);
            }
        }
    }

    @Override
    public CompletableFuture<List<ObjectProperties>> listObjectsAsync(String prefix, Predicate<ObjectProperties> p) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return listObjects(prefix, p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public List<ObjectProperties> listObjects(String prefix, Predicate<ObjectProperties> p) throws IOException {
        byte[] pb = prefix != null ? prefix.getBytes(StandardCharsets.UTF_8) : ByteArrayUtils.EMPTY;

        byte[] start = new byte[TBS_INDEX_SIZE + 1 + pb.length];
        encodeInt(tbsIndex, start, 0);
        start[TBS_INDEX_SIZE] = TYPE_OBJ_METADATA;
        System.arraycopy(pb, 0, start, TBS_INDEX_SIZE + 1, pb.length);
        List<ObjectProperties> r = new ArrayList<>();
        try (DbIterator it = tablespace.getRdb().newPrefixIterator(start)) {
            var opb = org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties.newBuilder();
            while (it.isValid()) {
                byte[] k = it.key();
                byte[] v = it.value();
                String objName = new String(k, TBS_INDEX_SIZE + 1, k.length - TBS_INDEX_SIZE - 1,
                        StandardCharsets.UTF_8);
                opb.mergeFrom(v);
                opb.setName(objName);
                var props = ObjectProperties.fromYarch(opb);
                if (p.test(props)) {
                    r.add(props);
                    opb = org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties.newBuilder();
                }
                it.next();
            }
        }
        return r;
    }

    @Override
    public CompletableFuture<Void> putObjectAsync(String objectName, String contentType,
            Map<String, String> metadata, byte[] objectData) {
        return CompletableFuture.runAsync(() -> {
            try {
                putObject(objectName, contentType, metadata, objectData);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public synchronized void putObject(String objectName, String contentType, Map<String, String> metadata,
            byte[] objectData) throws IOException {
        if (objectName.isEmpty()) {
            throw new IllegalArgumentException("object name cannot be empty");
        }
        log.debug("Uploading object {} to bucket {}; contentType: {}", objectName, bucketProps.getName(), contentType);
        var props = org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties.newBuilder();
        if (metadata != null) {
            props.putAllMetadata(metadata);
        }
        props.setCreated(TimeEncoding.getWallclockTime());
        props.setSize(objectData.length);
        if (contentType != null) {
            props.setContentType(contentType);
        }

        try (WriteBatch writeBatch = new WriteBatch();
                WriteOptions writeOpts = new WriteOptions()) {
            var oldProps = findObject(objectName);

            byte[] mk = getKey(TYPE_OBJ_METADATA, objectName);
            byte[] dk = getKey(TYPE_OBJ_DATA, objectName);
            writeBatch.put(mk, props.build().toByteArray());
            writeBatch.put(dk, objectData);
            long bsize = bucketProps.getSize() + props.getSize() - ((oldProps == null) ? 0 : oldProps.size());
            if (bsize > bucketProps.getMaxSize()) {
                throw new IOException("Maximum bucket size " + bucketProps.getMaxSize() + " exceeded");
            }
            int numobj = bucketProps.getNumObjects() + ((oldProps == null) ? 1 : 0);
            if (numobj > bucketProps.getMaxNumObjects()) {
                throw new IOException(
                        "Maximum number of objects in the bucket " + bucketProps.getNumObjects() + " exceeded");
            }
            var bucketProps1 = org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties.newBuilder()
                    .mergeFrom(bucketProps)
                    .setNumObjects(numobj)
                    .setSize(bsize)
                    .build();
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                    .setType(Type.BUCKET)
                    .setBucketProperties(bucketProps1)
                    .setTbsIndex(tbsIndex);
            tablespace.writeToBatch(yamcsInstance, writeBatch, trb);

            tablespace.getRdb().getDb().write(writeOpts, writeBatch);
            bucketProps = bucketProps1;
        } catch (RocksDBException e) {
            throw new IOException("Error writing object data: " + e.toString(), e);
        }
    }

    @Override
    public CompletableFuture<ObjectProperties> findObjectAsync(String objectName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return findObject(objectName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public ObjectProperties findObject(String objectName) throws IOException {
        byte[] k = getKey(TYPE_OBJ_METADATA, objectName);
        try {
            byte[] v = tablespace.getRdb().get(k);
            if (v == null) {
                return null;
            }
            var proto = org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties.newBuilder()
                    .mergeFrom(v).setName(objectName).build();
            return ObjectProperties.fromYarch(proto);
        } catch (InvalidProtocolBufferException e) {
            throw new DatabaseCorruptionException("Cannot decode data: " + e.toString(), e);
        } catch (RocksDBException e1) {
            throw new IOException(e1);
        }
    }

    @Override
    public CompletableFuture<byte[]> getObjectAsync(String objectName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getObject(objectName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public byte[] getObject(String objectName) throws IOException {
        try {
            byte[] k = getKey(TYPE_OBJ_DATA, objectName);
            YRDB rdb = tablespace.getRdb();

            return rdb.get(k);
        } catch (RocksDBException e) {
            throw new IOException("Failed to retrieve object: " + e.toString(), e);
        }
    }

    @Override
    public CompletableFuture<Void> deleteObjectAsync(String objectName) {
        return CompletableFuture.runAsync(() -> {
            log.debug("Deleting {} from {}", objectName, bucketProps.getName());
            try {
                deleteObject(objectName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public synchronized void deleteObject(String objectName) throws IOException {
        log.debug("Deleting {} from {}", objectName, bucketProps.getName());
        try {
            var props = findObject(objectName);
            if (props == null) {
                throw new IOException("No object by name '" + objectName + "' found");
            }
            try (WriteBatch writeBatch = new WriteBatch();
                    WriteOptions writeOpts = new WriteOptions()) {
                byte[] mk = getKey(TYPE_OBJ_METADATA, objectName);
                byte[] dk = getKey(TYPE_OBJ_DATA, objectName);
                writeBatch.delete(mk);
                writeBatch.delete(dk);
                var bucketProps1 = org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties.newBuilder()
                        .mergeFrom(bucketProps)
                        .setNumObjects(bucketProps.getNumObjects() - 1)
                        .setSize(bucketProps.getSize() - props.size())
                        .build();
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.BUCKET)
                        .setBucketProperties(bucketProps1).setTbsIndex(tbsIndex);
                tablespace.writeToBatch(yamcsInstance, writeBatch, trb);
                tablespace.getRdb().getDb().write(writeOpts, writeBatch);

                bucketProps = bucketProps1;
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to retrieve object: " + e.toString(), e);
        }
    }

    @Override
    public String getName() {
        return bucketProps.getName();
    }

    public Tablespace getTablespace() {
        return tablespace;
    }

    public int getTbsIndex() {
        return tbsIndex;
    }

    private byte[] getKey(byte type, String objectName) {
        byte[] a = objectName.getBytes(StandardCharsets.UTF_8);
        byte[] k = new byte[TBS_INDEX_SIZE + 1 + a.length];

        encodeInt(tbsIndex, k, 0);
        k[TBS_INDEX_SIZE] = type;
        System.arraycopy(a, 0, k, TBS_INDEX_SIZE + 1, a.length);

        return k;
    }

    private void saveUpdatedBucketProperties(
            org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties updatedBucketProperties)
            throws RocksDBException, IOException {
        try (WriteBatch writeBatch = new WriteBatch();
                WriteOptions writeOpts = new WriteOptions()) {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                    .setType(Type.BUCKET)
                    .setBucketProperties(updatedBucketProperties)
                    .setTbsIndex(tbsIndex);
            tablespace.writeToBatch(yamcsInstance, writeBatch, trb);

            tablespace.getRdb().getDb().write(writeOpts, writeBatch);
            this.bucketProps = updatedBucketProperties;
        }
    }
}
