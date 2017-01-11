package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.yarch.YarchDatabase;

public class RdbTagDb implements TagDb {

    static Logger log=LoggerFactory.getLogger(RdbTagDb.class);

    private RocksDB db;
    final static int __key_size=12;
    AtomicInteger idgenerator=new AtomicInteger(0);
    final byte[] firstkey=new byte[12];

    RdbTagDb(YarchDatabase ydb) throws RocksDBException {
        this(ydb.getRoot()+"/tags");
    }
    
    RdbTagDb(String path) throws RocksDBException {
        db = RocksDB.open(path);

        log.debug("opened {}: {} ", path, db.getProperty("rocksdb.stats"));
        String num = db.getProperty("rocksdb.estimate-num-keys");

        if("0".equals(num)) {
            writeHeader();
        } else {
            ByteBuffer bb=ByteBuffer.wrap(db.get(firstkey));
            idgenerator.set(bb.getInt());
        }
    }
    private void writeHeader() throws RocksDBException {
        //put a record at the beginning containing the current id and the max distance
        int id=idgenerator.get();
        ByteBuffer bb=ByteBuffer.allocate(12);
        bb.putInt(id);
        db.put(firstkey, bb.array());
    }

    private byte[] key(ArchiveTag tag) {
        ByteBuffer bbk=ByteBuffer.allocate(__key_size);
        bbk.putLong(tag.hasStart()?tag.getStart():0);
        bbk.putInt(tag.getId());
        return bbk.array();
    }

    private byte[] key(long start, int id) {
        ByteBuffer bbk=ByteBuffer.allocate(__key_size);
        bbk.putLong(start);
        bbk.putInt(id);
        return bbk.array();
    }

    /**
     * Synchonously gets tags, passing every separate one to the provided
     * {@link TagReceiver}.
     */
    @Override
    public void getTags(TimeInterval intv, TagReceiver callback) throws IOException {
        log.debug("processing request: {}", intv);
        RocksIterator it = db.newIterator();
        boolean hasNext;
        it.seek(firstkey);
        it.next();
        hasNext = it.isValid();

        //first we read all the records without start, then we jump to reqStart-maxDistance
        while(hasNext) {
            ArchiveTag tag = ArchiveTag.parseFrom(it.value());
            if(intv.hasStop() && tag.hasStart() && intv.getStop()<tag.getStart()) break;
            if(intv.hasStart() && tag.hasStop() && tag.getStop()<intv.getStart()) {
                it.next();
                hasNext = it.isValid();
                continue;
            }
            callback.onTag(tag);
            it.next();
            hasNext = it.isValid();
        }
        callback.finished();
    }
    
    /**
     * Returns a specific tag, or null if the requested tag does not exist
     */
    @Override
    public ArchiveTag getTag(long tagTime, int tagId) throws IOException {
        try {
            byte[] k = key(tagTime, tagId);
            byte[] v = db.get(k);
            return (v != null) ? ArchiveTag.parseFrom(v) : null;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Inserts a new Tag. No id should be specified. If it is, it will
     * silently be overwritten, and the new tag will be returned.
     */
    @Override
    public ArchiveTag insertTag(ArchiveTag tag) throws IOException {
        ArchiveTag newTag=ArchiveTag.newBuilder(tag).setId(getNewId()).build();
        try {
            db.put(key(newTag), newTag.toByteArray());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return newTag;
    }

    /**
     * Updates an existing tag. The tag is fetched by the specified id
     * throws YamcsException if the tag could not be found.
     * <p>
     * Note that both tagId and oldTagStart need to be specified so that
     * a direct lookup in the internal data structure can be made.
     */
    @Override
    public ArchiveTag updateTag(long tagTime, int tagId, ArchiveTag tag) throws YamcsException, IOException {
        try {
            if(tagId<1) throw new YamcsException("Invalid or unexisting id");
            db.remove(key(tagTime, tagId));
            ArchiveTag newTag=ArchiveTag.newBuilder(tag).setId(tagId).build();
            db.put(key(newTag), newTag.toByteArray());
            return newTag;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Deletes the specified tag
     * @throws YamcsException if the id was invalid, or if the tag could not be found
     */
    @Override
    public ArchiveTag deleteTag(long tagTime, int tagId) throws IOException, YamcsException {
        if(tagId<1) throw new YamcsException("Invalid or unexisting id");
        try {
            byte[] k=key(tagTime, tagId);
            byte[]v=db.get(k);
            if(v==null) throw new YamcsException("No tag with the given time,id");
            db.remove(k);
            return ArchiveTag.parseFrom(v);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    private int getNewId() throws IOException {
        int id=idgenerator.incrementAndGet();
        try {
            writeHeader();
            return id;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
      
    }
    
    @Override
    public void close() {
        db.close();
    }
}
