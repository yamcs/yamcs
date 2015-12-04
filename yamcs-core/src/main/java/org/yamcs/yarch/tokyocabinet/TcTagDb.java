package org.yamcs.yarch.tokyocabinet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.yarch.YarchDatabase;

public class TcTagDb implements TagDb {

    static Logger log=LoggerFactory.getLogger(TcTagDb.class);
    static Map<String, TcTagDb> dbsByInstance = new HashMap<String, TcTagDb>();
    
    private YBDB db;
    final static int __key_size=12;
    final String instance;
    AtomicInteger idgenerator=new AtomicInteger(0);
    final byte[] firstkey=new byte[12];
    
    private TcTagDb(String instance, boolean readonly) throws IOException {
        this.instance=instance;
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        
        db=new YBDB();
        String filename=ydb.getRoot()+"/tags.bdb";
        
        db=ydb.getTCBFactory().getTcb(filename, false, readonly);
        log.info("opened "+filename+" with "+db.rnum()+" records");
        if(db.rnum()==0) {
            writeHeader();
        } else {
            ByteBuffer bb=ByteBuffer.wrap(db.get(firstkey));
            idgenerator.set(bb.getInt());
        }
    }
    
    public static synchronized TcTagDb getInstance(String yamcsInstance, boolean readonly) throws IOException {
        if (!dbsByInstance.containsKey(yamcsInstance)) {
            TcTagDb tagDb = new TcTagDb(yamcsInstance, readonly);
            dbsByInstance.put(yamcsInstance, tagDb);
        }
        return dbsByInstance.get(yamcsInstance);
    }

    private void writeHeader() throws IOException {
       //put a record at the beginning containing the current id and the max distance
        int id=idgenerator.get();
        ByteBuffer bb=ByteBuffer.allocate(12);
        bb.putInt(id);
        db.put(firstkey,bb.array());
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
        YBDBCUR cursor=db.openCursor();
        boolean hasNext;
        cursor.jump(firstkey);
        hasNext=cursor.next();
        
        //first we read all the records without start, then we jump to reqStart-maxDistance
        while(hasNext) {
            ArchiveTag tag=ArchiveTag.parseFrom(cursor.val());
            if(intv.hasStop() && tag.hasStart() && intv.getStop()<tag.getStart()) break;
            if(intv.hasStart() && tag.hasStop() && tag.getStop()<intv.getStart()) {
                hasNext=cursor.next();
                continue;
            }
            callback.onTag(tag);
            hasNext=cursor.next();
        }
        callback.finished();
    }
    
    /**
     * Returns a specific tag, or null if the requested tag does not exist
     */
    @Override
    public ArchiveTag getTag(long tagTime, int tagId) throws IOException {
        byte[] k = key(tagTime, tagId);
        byte[] v = db.get(k);
        return (v != null) ? ArchiveTag.parseFrom(v) : null;
    }
    
    /**
     * Inserts a new Tag. No id should be specified. If it is, it will
     * silently be overwritten, and the new tag will be returned.
     */
    @Override
    public ArchiveTag insertTag(ArchiveTag tag) throws IOException {
        ArchiveTag newTag=ArchiveTag.newBuilder(tag).setId(getNewId()).build();
        db.put(key(newTag), newTag.toByteArray());
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
        if(tagId<1) throw new YamcsException("Invalid or unexisting id");
        db.out(key(tagTime, tagId));
        ArchiveTag newTag=ArchiveTag.newBuilder(tag).setId(tagId).build();
        db.put(key(newTag), newTag.toByteArray());
        return newTag;
    }

    /**
     * Deletes the specified tag
     * @throws YamcsException if the id was invalid, or if the tag could not be found
     */
    @Override
    public ArchiveTag deleteTag(long tagTime, int tagId) throws IOException, YamcsException {
        if(tagId<1) throw new YamcsException("Invalid or unexisting id");
        byte[] k=key(tagTime, tagId);
        byte[]v=db.get(k);
        if(v==null) throw new YamcsException("No tag with the given time,id");
        db.out(k);
        return ArchiveTag.parseFrom(v);
    }
    
    private int getNewId() throws IOException {
        int id=idgenerator.incrementAndGet();
        writeHeader();
        return id;
    }
}
