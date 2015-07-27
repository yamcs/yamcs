package org.yamcs.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.TagRequest;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.tokyocabinet.YBDB;
import org.yamcs.yarch.tokyocabinet.YBDBCUR;

public class TagDb {

    static Logger log=LoggerFactory.getLogger(ReplayServer.class);
    static Map<String, TagDb> dbsByInstance = new HashMap<String, TagDb>();
    
    private YBDB db;
    final static int __key_size=12;
    final String instance;
    AtomicInteger idgenerator=new AtomicInteger(0);
    final byte[] firstkey=new byte[12];
    
    private TagDb(String instance, boolean readonly) throws IOException, ConfigurationException {
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
    
    public static synchronized TagDb getInstance(String yamcsInstance, boolean readonly) throws ConfigurationException, IOException {
        if (!dbsByInstance.containsKey(yamcsInstance)) {
            TagDb tagDb = new TagDb(yamcsInstance, readonly);
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

    /**
     * Synchonously gets tags, passing every separate one to the provided
     * {@link TagReceiver}.
     */
    public void getTags(TagRequest req, TagReceiver callback) throws IOException {
        log.debug("processing request: {}", req);
        YBDBCUR cursor=db.openCursor();
        boolean hasNext;
        cursor.jump(firstkey);
        hasNext=cursor.next();
        
        
        //first we read all the records without start, then we jump to reqStart-maxDistance
        while(hasNext) {
            ArchiveTag tag=ArchiveTag.parseFrom(cursor.val());
            if(req.hasStop() && tag.hasStart() && req.getStop()<tag.getStart()) break;
            if(req.hasStart() && tag.hasStop() && tag.getStop()<req.getStart()) {
                hasNext=cursor.next();
                continue;
            }
            callback.onTag(tag);
            hasNext=cursor.next();
        }
        callback.finished();
    }

    public ArchiveTag upsertTag(UpsertTagRequest request) throws IOException, YamcsException {
        ArchiveTag oldTag=request.getOldTag();
        ArchiveTag newTag=request.getNewTag();
       
        if(request.hasOldTag()) { //update
            if(!oldTag.hasId() || oldTag.getId()<1) throw new YamcsException("Invalid or unexisting id");
            db.out(key(oldTag));
            newTag=ArchiveTag.newBuilder(newTag).setId(oldTag.getId()).build();
        } else {
            newTag=ArchiveTag.newBuilder(newTag).setId(getNewId()).build();
        }
        db.put(key(newTag), newTag.toByteArray());
        return newTag;
    }

    public ArchiveTag deleteTag(DeleteTagRequest request) throws IOException, YamcsException {
        ArchiveTag tag=request.getTag();
        if(!tag.hasId() || tag.getId()<1) throw new YamcsException("Invalid or unexisting id");
        byte[] k=key(tag);
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
