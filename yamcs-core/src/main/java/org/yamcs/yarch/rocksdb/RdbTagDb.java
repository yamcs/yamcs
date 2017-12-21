package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.utils.ByteArrayUtils.encodeLong;
import static org.yamcs.utils.ByteArrayUtils.decodeInt;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;


public class RdbTagDb implements TagDb {

    static Logger log = LoggerFactory.getLogger(RdbTagDb.class);

    private final Tablespace tablespace;
    static final int __key_size = 16;
    AtomicInteger idgenerator = new AtomicInteger(0);
    final String yamcsInstance;
    final int tbsIndex;

    RdbTagDb(String yamcsInstance, Tablespace tablespace) throws RocksDBException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;

        List<TablespaceRecord> trl = tablespace.filter(Type.TAGDB, yamcsInstance, trb -> true);
        if (trl.isEmpty()) {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.TAGDB);
            TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
            tbsIndex = tr.getTbsIndex();
            log.debug("Created new tag db tbsIndex: {}", tbsIndex);
            writeHeader();
        } else {
            tbsIndex = trl.get(0).getTbsIndex();
            YRDB db = tablespace.getRdb();
            byte[] val = db.get(headerKey());
            if(val==null) {
                throw new DatabaseCorruptionException("Cannot find the header of the tag db: "+StringConverter.arrayToHexString(headerKey()));
            }
            idgenerator.set(decodeInt(val, 0));
        }
    }

    private void writeHeader() throws RocksDBException {
        // put a record at the beginning containing the current id
        int id = idgenerator.get();
        byte[] val = new byte[4];
        encodeInt(id, val, 0);

        YRDB db = tablespace.getRdb();
        db.put(headerKey(), val);
    }

    private  byte[] headerKey() {
        return key(0, 0);
    }
    private byte[] key(ArchiveTag tag) {
        long start = tag.hasStart() ? tag.getStart() : 0;
        return key(start, tag.getId());
    }

    private byte[] key(long start, int id) {
        byte[] key = new byte[__key_size];
        encodeInt(tbsIndex, key, 0);
        encodeLong(start, key, TBS_INDEX_SIZE);
        encodeInt(id, key, 12);
        return key;
    }

    /**
     * Synchonously gets tags, passing every separate one to the provided
     * {@link TagReceiver}.
     */
    @Override
    public void getTags(TimeInterval intv, TagReceiver callback) throws IOException {
        log.debug("processing request: {}", intv);
        YRDB db = tablespace.getRdb();
        byte[] rangeStart = headerKey();
        boolean strictStart = true;
        
        byte[] rangeStop;
        if(intv.hasEnd()) {
            rangeStop = new byte[TBS_INDEX_SIZE+8];
            encodeInt(tbsIndex, rangeStop, 0);
            encodeLong(intv.getEnd(), rangeStop, TBS_INDEX_SIZE);
        } else {
            rangeStop = new byte[TBS_INDEX_SIZE];
            encodeInt(tbsIndex, rangeStop, 0);
        }
        boolean strictStop = false;
        try(AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), rangeStart, strictStart, rangeStop, strictStop)) {
            while (it.isValid()) {
                ArchiveTag tag = ArchiveTag.parseFrom(it.value());
                if (intv.hasStart() && tag.hasStop() && tag.getStop() < intv.getStart()) {
                    it.next();
                    continue;
                }
                callback.onTag(tag);
                it.next();
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
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
            byte[] v = tablespace.getRdb().get(k);
            return (v != null) ? ArchiveTag.parseFrom(v) : null;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Inserts a new Tag. No id should be specified. If it is, it will silently
     * be overwritten, and the new tag will be returned.
     */
    @Override
    public ArchiveTag insertTag(ArchiveTag tag) throws IOException {
        ArchiveTag newTag = ArchiveTag.newBuilder(tag).setId(getNewId()).build();
        try {
            tablespace.putData(key(newTag), newTag.toByteArray());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return newTag;
    }

    /**
     * Updates an existing tag. The tag is fetched by the specified id throws
     * YamcsException if the tag could not be found.
     * <p>
     * Note that both tagId and oldTagStart need to be specified so that a
     * direct lookup in the internal data structure can be made.
     */
    @Override
    public ArchiveTag updateTag(long tagTime, int tagId, ArchiveTag tag) throws YamcsException, IOException {
        try {
            if (tagId < 1) {
                throw new YamcsException("Invalid or unexisting id");
            }
            tablespace.remove(key(tagTime, tagId));
            ArchiveTag newTag = ArchiveTag.newBuilder(tag).setId(tagId).build();
            tablespace.putData(key(newTag), newTag.toByteArray());
            return newTag;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    /**
     * Deletes the specified tag
     * 
     * @throws YamcsException
     *             if the id was invalid, or if the tag could not be found
     */
    @Override
    public ArchiveTag deleteTag(long tagTime, int tagId) throws IOException, YamcsException {
        if (tagId < 1) {
            throw new YamcsException("Invalid or unexisting id");
        }
        try {
            byte[] k = key(tagTime, tagId);
            byte[] v = tablespace.getData(k);
            if (v == null) {
                throw new YamcsException("No tag with the given time,id");
            }
            tablespace.remove(k);
            return ArchiveTag.parseFrom(v);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    private int getNewId() throws IOException {
        int id = idgenerator.incrementAndGet();
        try {
            writeHeader();
            return id;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

    }

    @Override
    public void close() {
    }
}
