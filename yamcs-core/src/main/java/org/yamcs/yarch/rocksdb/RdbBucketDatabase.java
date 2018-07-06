package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

/**
 * Stores users objects in rocksdb
 * 
 * Each bucket has associated a TablespaceRecord with the corresponding tbsIndex.
 * 
 * Each object in the bucket has an 4 bytes objectId
 * 
 * The rocksdb key is formed by either one of:
 * 
 * 4 bytes    1 byte             variable size
 * tbsIndex   0 = bucket info
 * tbsIndex   1 = metadata       objectName (up to 1000 bytes)
 * tbsIndex   2 = data           objectId (4 bytes)
 * 
 * 
 * The rocksdb value is formed by:
 * in case of metadata:
 * protobuf representation of ObjectProperties (contains the objectId and key,value metadata)
 * in case of user object:
 * binary user object
 * 
 * To retrieve an object based on the bucket name and object name,
 * 1. retrieve the tbsIndex based on the bucket name
 * 2. retrieve the ObjectProperties based on the tbsIndex and object name
 * 3. retrieve the object data based on the tbsIndex and objectId
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
    
    final static long MAX_BUCKET_SIZE = 100l * 1024 * 1024; //100MB
    final static int MAX_NUM_OBJECTS_PER_BUCKET = 1000; //
    private static final Logger log = LoggerFactory.getLogger(RdbBucketDatabase.class);
    
    RdbBucketDatabase(String yamcsInstance, Tablespace tablespace) throws RocksDBException, IOException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        loadBuckets();
    }

    private void loadBuckets() throws RocksDBException, IOException {
        List<TablespaceRecord> l = tablespace.filter(Type.BUCKET, yamcsInstance, x->true);
        for(TablespaceRecord tr: l) {
            RdbBucket b = new RdbBucket(yamcsInstance, tablespace, tr.getTbsIndex(), tr.getBucketProperties());
            buckets.put(b.getName(), b);
        }
    }

    @Override
    public RdbBucket createBucket(String bucketName) throws IOException {
        log.debug("creating new bucket {}", bucketName);
        try {
            synchronized (buckets) {
                if (buckets.containsKey(bucketName)) {
                    throw new IllegalArgumentException("Bucket already exists");
                }
                BucketProperties bucketProps = BucketProperties.newBuilder().setCreated(TimeEncoding.getWallclockTime())
                        .setMaxNumObjects(MAX_NUM_OBJECTS_PER_BUCKET).setMaxSize(MAX_BUCKET_SIZE).setName(bucketName).build();
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.BUCKET)
                        .setBucketProperties(bucketProps);
                TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
                RdbBucket bucket = new RdbBucket(yamcsInstance, tablespace, tr.getTbsIndex(), bucketProps);
                buckets.put(bucketName, bucket);
                return bucket;
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to create bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public RdbBucket getBucket(String bucketName) {
        synchronized (buckets) {
            return buckets.get(bucketName);
        }
    }

    @Override
    public List<BucketProperties> listBuckets() {
        synchronized (buckets) {
            return buckets.values().stream().map(b -> b.bucketProps).collect(Collectors.toList());
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws IOException {
        log.debug("deleting bucket {}", bucketName);
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
            throw new IOException("Failed to delete bucket: " + e.getMessage(), e);
        }
    }

}
