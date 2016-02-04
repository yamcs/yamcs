package org.yamcs.ui;



import org.hornetq.api.core.HornetQException;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.ui.archivebrowser.ArchiveIndexListener;
import org.yamcs.ui.archivebrowser.ArchiveIndexReceiver;
import org.yamcs.xtce.MdbMappings;

import org.yamcs.api.YamcsConnector;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.TagResult;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;

public class YamcsArchiveIndexReceiver implements ConnectionListener, ArchiveIndexReceiver {
    ArchiveIndexListener indexListener;

    volatile private boolean  receiving=false;

    YamcsConnector yconnector;
    YamcsClient yamcsClient;


    public YamcsArchiveIndexReceiver(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    @Override
    public void setIndexListener(ArchiveIndexListener ail) {
        this.indexListener=ail;
    }

    @Override
    public void getIndex(final String instance, final TimeInterval interval) {
        if(receiving){
            indexListener.log("already receiving data");
            return;
        }
        if( instance == null ) {
            indexListener.receiveArchiveRecordsError( "No yamcs instance to get data from" );
            return;
        }
        yconnector.getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    int seq=0;
                    IndexRequest.Builder request=IndexRequest.newBuilder().setInstance(instance);
                    if(interval.hasStart())request.setStart(interval.getStart());
                    if(interval.hasStop()) request.setStop(interval.getStop());
                    request.setDefaultNamespace(MdbMappings.MDB_OPSNAME).setSendAllPp(true).setSendAllTm(true).setSendAllCmd(true);
                    request.setSendCompletenessIndex(true);
                    //       yamcsClient.executeRpc(Protocol.getYarchIndexControlAddress(instance), "getIndex", request.build(), null);
                    yamcsClient.sendRequest(Protocol.getYarchIndexControlAddress(instance), "getIndex", request.build());
                    while(true) {
                        IndexResult ir=(IndexResult) yamcsClient.receiveData(IndexResult.newBuilder());
                        //    System.out.println("Received ")
                        if(ir==null) {
                            indexListener.receiveArchiveRecordsFinished();
                            break;
                        }
                        indexListener.receiveArchiveRecords(ir);
                        seq++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    indexListener.receiveArchiveRecordsError(e.toString());
                }  finally {
                    receiving=false;
                }
            };
        });
    }


    @Override
    public void getTag(final String instance, final TimeInterval interval) {
        //System.out.println("receiving tags for "+instance);
        if(receiving){
            indexListener.log("already receiving data");
            return;
        }
        yconnector.getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    int seq=0;
                    IndexRequest.Builder request=IndexRequest.newBuilder().setInstance(instance);
                    if(interval.hasStart())request.setStart(interval.getStart());
                    if(interval.hasStop()) request.setStop(interval.getStop());
                    yamcsClient.sendRequest(Protocol.getYarchIndexControlAddress(instance), "getTag", request.build());
                    while(true) {
                        TagResult tr=(TagResult) yamcsClient.receiveData(TagResult.newBuilder());
                        if(tr==null) {
                            indexListener.receiveTagsFinished();
                            break;
                        }
                        indexListener.receiveTags(tr.getTagList());
                        seq++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    indexListener.receiveArchiveRecordsError(e.getMessage());
                }  finally {
                    receiving=false;
                }
            };
        });
    }


    @Override
    public void insertTag(String instance, ArchiveTag tag) {
        UpsertTagRequest utr=UpsertTagRequest.newBuilder().setNewTag(tag).build();
        try {
            ArchiveTag ntag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "upsertTag", utr, ArchiveTag.newBuilder());
            indexListener.tagAdded(ntag);
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: "+e.getMessage());
        }
    }

    @Override
    public void updateTag(String instance, ArchiveTag oldTag, ArchiveTag newTag) {
        UpsertTagRequest utr=UpsertTagRequest.newBuilder().setOldTag(oldTag).setNewTag(newTag).build();
        try {
            ArchiveTag ntag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "upsertTag", utr, ArchiveTag.newBuilder());
            indexListener.tagChanged(oldTag, ntag);
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: "+e.getMessage());
        }

    }

    @Override
    public void deleteTag(String instance, ArchiveTag tag) {
        DeleteTagRequest dtr=DeleteTagRequest.newBuilder().setTag(tag).build();
        try {
            ArchiveTag rtag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "deleteTag", dtr, ArchiveTag.newBuilder());
            indexListener.tagRemoved(rtag);
        } catch (Exception e) {
            indexListener.log("Failed to remove tag: "+e.getMessage());
        }
    }

    @Override
    public boolean supportsTags() {
        return true;
    }


    @Override
    public void connecting(String url) {
        indexListener.connecting(url);
    }

    @Override
    public void connected(String url) {
        try {
            yamcsClient=yconnector.getSession().newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            indexListener.log("connected to "+yconnector.getUrl());
        } catch (HornetQException e) {
            e.printStackTrace();
            indexListener.log("Failed to build yamcs client: "+e.getMessage());
        }

    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        indexListener.connectionFailed(url, exception);
    }

    @Override
    public void disconnected() {
        indexListener.disconnected();
    }

    @Override
    public void log(String message) {
        indexListener.log(message);
    }
}
