package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

/**
 * Stores users objects in rocksdb
 * <p>
 * Each bucket has associated a TablespaceRecord with the corresponding tbsIndex.
 * <p>
 * Each object in the bucket has an 4 bytes objectId
 * <p>
 * The rocksdb key is formed by either one of:
 * 
 * <pre>
 * 4 bytes    1 byte             variable size
 * tbsIndex   0 = bucket info
 * tbsIndex   1 = metadata       objectName (up to 1000 bytes)
 * tbsIndex   2 = data           objectId (4 bytes)
 * </pre>
 * 
 * The rocksdb value is formed by:
 * <ul>
 * <li>in case of metadata: protobuf representation of ObjectProperties (contains the objectId and key,value metadata)
 * <li>in case of user object: binary user object
 * </ul>
 * 
 * To retrieve an object based on the bucket name and object name,
 * <ol>
 * <li>retrieve the tbsIndex based on the bucket name
 * <li>retrieve the ObjectProperties based on the tbsIndex and object name
 * <li>retrieve the object data based on the tbsIndex and objectId
 * </ol>
 * 
 * @author nm
 *
 */
public class RdbBucketDatabase implements BucketDatabase {
    private final Tablespace tablespace;
    private final String yamcsInstance;
    Map<String, RdbBucket> buckets = new HashMap<>();

    final static byte TYPE_BUCKET_INFO = 0;
    final static byte TYPE_OBJ_METADATA = 1;
    final static byte TYPE_OBJ_DATA = 2;

    final static long DEFAULT_MAX_BUCKET_SIZE = 100L * 1024 * 1024; // 100MB
    final static int DEFAULT_MAX_OBJECTS_PER_BUCKET = 1000;
    private static final Logger log = LoggerFactory.getLogger(RdbBucketDatabase.class);

    public RdbBucketDatabase(String yamcsInstance, Tablespace tablespace) throws RocksDBException, IOException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        loadBuckets();
    }

    private void loadBuckets() throws RocksDBException, IOException {
        List<TablespaceRecord> l = tablespace.filter(Type.BUCKET, yamcsInstance, x -> true);
        for (TablespaceRecord tr : l) {
            RdbBucket b = new RdbBucket(yamcsInstance, tablespace, tr.getTbsIndex(), tr.getBucketProperties());
            buckets.put(b.getName(), b);
        }
    }

    @Override
    public RdbBucket createBucket(String bucketName) throws IOException {
        log.debug("Creating new bucket {}", bucketName);
        try {
            synchronized (buckets) {
                if (buckets.containsKey(bucketName)) {
                    throw new IllegalArgumentException("Bucket already exists");
                }
                BucketProperties bucketProps = BucketProperties.newBuilder()
                        .setName(bucketName)
                        .setCreated(TimeEncoding.getWallclockTime())
                        .setMaxNumObjects(DEFAULT_MAX_OBJECTS_PER_BUCKET)
                        .setMaxSize(DEFAULT_MAX_BUCKET_SIZE)
                        .build();
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                        .setType(Type.BUCKET)
                        .setBucketProperties(bucketProps);
                TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
                RdbBucket bucket = new RdbBucket(yamcsInstance, tablespace, tr.getTbsIndex(), bucketProps);
                buckets.put(bucketName, bucket);
                return bucket;
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to create bucket: " + e.toString(), e);
        }
    }

    @Override
    public RdbBucket getBucket(String bucketName) {
        synchronized (buckets) {
            return buckets.get(bucketName);
        }
    }

    @Override
    public List<Bucket> listBuckets() {
        synchronized (buckets) {
            return new ArrayList<>(buckets.values());
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws IOException {
        log.debug("Deleting bucket {}", bucketName);
        try {
            synchronized (buckets) {
                RdbBucket b = buckets.get(bucketName);
                if (b == null) {
                    throw new IllegalArgumentException("No bucket by this name");
                }
                tablespace.removeTbsIndex(Type.BUCKET, b.getTbsIndex());
                buckets.remove(b.getName());
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to delete bucket: " + e.toString(), e);
        }
    }

    public Tablespace getTablespace() {
        return tablespace;
    }
}
