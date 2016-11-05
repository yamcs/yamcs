package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.decode;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.archive.IndexServer;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TagRequest;
import org.yamcs.protobuf.Yamcs.TagResult;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public class HornetQIndexServer extends AbstractExecutionThreadService {
    static Logger log=LoggerFactory.getLogger(HornetQIndexServer.class);

    final String instance;

    final YamcsClient msgClient;
    final YamcsSession yamcsSession;
    final IndexServer indexServer;
    final TagDb tagDb;
    
    volatile boolean quitting=false;

    public HornetQIndexServer(IndexServer indexServer, TagDb tagDb) throws ActiveMQException, YamcsApiException {
        this.indexServer = indexServer;
        this.tagDb = tagDb;
        this.instance = indexServer.getInstance();
        yamcsSession = YamcsSession.newBuilder().build();
        msgClient = yamcsSession.newClientBuilder().setRpcAddress(Protocol.getYarchIndexControlAddress(instance)).setDataProducer(true).build();
    }

    @Override
    protected void startUp() {
        Thread.currentThread().setName(this.getClass().getSimpleName()+"["+instance+"]");
    }

    @Override
    public void run() {
        try {
            while(!quitting) {
                ClientMessage msg = msgClient.rpcConsumer.receive();
                if(msg==null) {
                    if(quitting) break;
                    log.warn("null message received from the control queue");
                    continue;
                }
                SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
                SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
                if(replyto==null) {
                    log.warn("did not receive a replyto header. Ignoring the request");
                    continue;
                }
                try {
                    String request=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
                    if("getIndex".equalsIgnoreCase(request)){
                        if(dataAddress==null) {
                            log.warn("received a getIndex without a dataAddress. Ignoring the request");
                            continue;
                        }
                        submitIndexRequest(msg,dataAddress);
                    } else if("rebuildIndex".equalsIgnoreCase(request)){
                        //   rebuildIndex(msg,replyto);
                    } else if("getTag".equalsIgnoreCase(request)){
                        if(dataAddress==null) {
                            log.warn("received a getTag without a dataAddress. Ignoring the request");
                            continue;
                        }
                        TagRequest req=(TagRequest)decode(msg, TagRequest.newBuilder());
                        TimeInterval intv = new TimeInterval();
                        if (req.hasStart()) intv.setStart(req.getStart());
                        if (req.hasStop()) intv.setStop(req.getStop());
                        tagDb.getTags(intv, new TagReceiver() {
                            private TagResult.Builder trb=TagResult.newBuilder().setInstance(instance);
                            
                            @Override
                            public void onTag(ArchiveTag tag) {
                                trb.addTag(tag);
                                if(trb.getTagCount()>500) {
                                    try {
                                        msgClient.sendData(dataAddress, ProtoDataType.ARCHIVE_TAG, trb.build());
                                    } catch (ActiveMQException e) {
                                        throw new RuntimeException(e);
                                    }
                                    trb=TagResult.newBuilder().setInstance(instance);
                                }
                            }

                            @Override
                            public void finished() {
                                try {
                                    if(trb.getTagCount()>0)
                                        msgClient.sendData(dataAddress, ProtoDataType.ARCHIVE_TAG, trb.build());
                                    msgClient.sendDataEnd(dataAddress);
                                } catch (ActiveMQException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    } else if("upsertTag".equalsIgnoreCase(request)){
                        UpsertTagRequest utr=(UpsertTagRequest)decode(msg, UpsertTagRequest.newBuilder());
                        ArchiveTag oldTag=utr.getOldTag();
                        ArchiveTag newTag=utr.getNewTag();
                        
                        if(utr.hasOldTag()) { //update
                            if(!oldTag.hasId() || oldTag.getId()<1) throw new YamcsException("Invalid or unexisting id");
                            long tagTime = (oldTag.hasStart() ? oldTag.getStart() : 0); 
                            newTag=tagDb.updateTag(tagTime, oldTag.getId(), newTag);
                        } else {
                            newTag=tagDb.insertTag(newTag);
                        }
                        msgClient.sendReply(replyto, "OK", newTag);
                    } else if("deleteTag".equalsIgnoreCase(request)){
                        DeleteTagRequest dtr=(DeleteTagRequest)decode(msg, DeleteTagRequest.newBuilder());
                        ArchiveTag tag=dtr.getTag();
                        if(!tag.hasId() || tag.getId()<1) throw new YamcsException("Invalid or unexisting id");
                        long tagTime = (tag.hasStart() ? tag.getStart() : 0);
                        ArchiveTag dtag = tagDb.deleteTag(tagTime, tag.getId());
                        msgClient.sendReply(replyto, "OK", dtag);
                    } else {
                        throw new YamcsException("Unknown request '"+request+"'");
                    }
                } catch(YamcsException e) {
                    msgClient.sendErrorReply(replyto, e.getMessage());
                } 
            }
        } catch (Exception e) {
            log.error("got exception while processing the requests ", e);
        }
    }
    
    private void submitIndexRequest(ClientMessage msg, SimpleString dataAddress) throws Exception {
        IndexRequest req;
        try {
            req=(IndexRequest)decode(msg, IndexRequest.newBuilder());
        } catch (YamcsApiException e) {
            log.warn("failed to decode the message", e);
            return;
        }
        HornetQIndexRequestListener l = new HornetQIndexRequestListener(dataAddress);
        indexServer.submitIndexRequest(req, l);
    }


    @Override
    protected void triggerShutdown() {
        try {
            quit();
        } catch (ActiveMQException e) {
            log.error("Failed to shutdown", e);
        }
    }
    
    public void quit() throws ActiveMQException {
        quitting=true;
        msgClient.close();
        yamcsSession.close();
    }

}
