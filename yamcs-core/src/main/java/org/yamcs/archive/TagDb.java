package org.yamcs.archive;

import static org.yamcs.api.Protocol.decode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.tokyocabinet.YBDB;
import org.yamcs.yarch.tokyocabinet.YBDBCUR;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;


import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TagRequest;
import org.yamcs.protobuf.Yamcs.TagResult;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;

public class TagDb {

    static Logger log=LoggerFactory.getLogger(ReplayServer.class.getName());
    private YBDB db;
    final static int __key_size=12;
    final String instance;
    AtomicInteger idgenerator=new AtomicInteger(0);
    final byte[] firstkey=new byte[12];
    
    public TagDb(String instance, boolean readonly) throws IOException, ConfigurationException {
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

    public void getTag(YamcsClient msgClient, ClientMessage msg, SimpleString dataAddress) throws IOException, YamcsApiException, HornetQException {
        TagRequest req=(TagRequest)decode(msg, TagRequest.newBuilder());
        log.debug("processing request: "+req);
        YBDBCUR cursor=db.openCursor();
        boolean hasNext;
        cursor.jump(firstkey);
        hasNext=cursor.next();
        TagResult.Builder trb=TagResult.newBuilder().setInstance(instance);
        
        //first we read all the records without start, then we jump to reqStart-maxDistance
        while(hasNext) {
            ArchiveTag tag=ArchiveTag.parseFrom(cursor.val());
            if(req.hasStop() && tag.hasStart() && req.getStop()<tag.getStart()) break;
            if(req.hasStart() && tag.hasStop() && tag.getStop()<req.getStart()) {
                hasNext=cursor.next();
                continue;
            }
            trb.addTag(tag);
            if(trb.getTagCount()>500) {
                msgClient.sendData(dataAddress, ProtoDataType.ARCHIVE_TAG, trb.build());
                trb=TagResult.newBuilder().setInstance(instance);
            }
            hasNext=cursor.next();
        }
        if(trb.getTagCount()>0)
            msgClient.sendData(dataAddress, ProtoDataType.ARCHIVE_TAG, trb.build());
        msgClient.sendDataEnd(dataAddress);
    }

    public void upsertTag(YamcsClient msgClient, ClientMessage msg, SimpleString replyto) throws IOException,YamcsApiException, YamcsException, HornetQException {
       UpsertTagRequest utr=(UpsertTagRequest)decode(msg, UpsertTagRequest.newBuilder());
       ArchiveTag oldTag=utr.getOldTag();
       ArchiveTag newTag=utr.getNewTag();
       
       if(utr.hasOldTag()) { //update
           if(!oldTag.hasId() || oldTag.getId()<1) throw new YamcsException("Invalid or unexisting id");
           db.out(key(oldTag));
           newTag=ArchiveTag.newBuilder(newTag).setId(oldTag.getId()).build();
       } else {
           newTag=ArchiveTag.newBuilder(newTag).setId(getNewId()).build();
       }
       db.put(key(newTag), newTag.toByteArray());
       msgClient.sendReply(replyto, "OK",  newTag);
    }

    public void deleteTag(YamcsClient msgClient, ClientMessage msg, SimpleString replyto) throws IOException, YamcsApiException, YamcsException, HornetQException {
       DeleteTagRequest dtr=(DeleteTagRequest)decode(msg, DeleteTagRequest.newBuilder());
       ArchiveTag tag=dtr.getTag();
       if(!tag.hasId() || tag.getId()<1) throw new YamcsException("Invalid or unexisting id");
       byte[] k=key(tag);
       byte[]v=db.get(k);
       if(v==null) throw new YamcsException("No tag with the given time,id");
       db.out(k);
       ArchiveTag dtag=ArchiveTag.parseFrom(v);
       msgClient.sendReply(replyto, "OK",  dtag);
       
    }
    private int getNewId() throws IOException {
        int id=idgenerator.incrementAndGet();
        writeHeader();
        return id;
    }

}
