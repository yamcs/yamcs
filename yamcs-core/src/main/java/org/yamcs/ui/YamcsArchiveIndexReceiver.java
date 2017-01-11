package org.yamcs.ui;

import java.util.concurrent.Future;

import org.yamcs.TimeInterval;
import org.yamcs.ui.archivebrowser.ArchiveIndexListener;
import org.yamcs.ui.archivebrowser.ArchiveIndexReceiver;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.TagResult;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;

import com.google.protobuf.InvalidProtocolBufferException;

public class YamcsArchiveIndexReceiver implements ArchiveIndexReceiver {
    ArchiveIndexListener indexListener;

    volatile private boolean receiving = false;

    YamcsConnector yconnector;


    public YamcsArchiveIndexReceiver(YamcsConnector yconnector) {
        this.yconnector = yconnector;
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
                    RestClient restClient = yconnector.getRestClient();
                    StringBuilder resource = new StringBuilder().append("/archive/"+instance+"/indexes?");

                    if(interval.hasStart()) resource.append("start="+TimeEncoding.toString(interval.getStart()));
                    if(interval.hasStop()) resource.append("&stop="+TimeEncoding.toString(interval.getStop()));

                    Future<Void> f = restClient.doBulkGetRequest(resource.toString(), new BulkRestDataReceiver() {
                        @Override
                        public void receiveData(byte[] data) throws YamcsApiException {
                            try {
                                indexListener.receiveArchiveRecords(IndexResult.parseFrom(data));
                            } catch (InvalidProtocolBufferException e) {
                                throw new YamcsApiException("Error parsing index result: "+e.getMessage());
                            }
                        }
                        @Override
                        public void receiveException(Throwable t) {
                            indexListener.receiveArchiveRecordsError(t.getMessage());
                        }
                    });

                    f.get();
                    indexListener.receiveArchiveRecordsFinished();
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
                    RestClient restClient = yconnector.getRestClient();
                    StringBuilder resource = new StringBuilder().append("/archive/"+instance+"/tags?");

                    if(interval.hasStart()) resource.append("start="+TimeEncoding.toString(interval.getStart()));
                    if(interval.hasStop()) resource.append("&stop="+TimeEncoding.toString(interval.getStop()));

                    Future<Void> f = restClient.doBulkGetRequest(resource.toString(), new BulkRestDataReceiver() {
                        @Override
                        public void receiveData(byte[] data) throws YamcsApiException {
                            try {
                                indexListener.receiveTags(TagResult.parseFrom(data).getTagList());
                            } catch (InvalidProtocolBufferException e) {
                                throw new YamcsApiException("Error parsing tag result: "+e.getMessage());
                            }
                        }
                        @Override
                        public void receiveException(Throwable t) {
                            indexListener.receiveArchiveRecordsError(t.getMessage());
                        }
                    });

                    f.get();
                    indexListener.receiveTagsFinished();
                    
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
        //    ArchiveTag ntag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "upsertTag", utr, ArchiveTag.newBuilder());
         //   indexListener.tagAdded(ntag);
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: "+e.getMessage());
        }
    }

    @Override
    public void updateTag(String instance, ArchiveTag oldTag, ArchiveTag newTag) {
        UpsertTagRequest utr=UpsertTagRequest.newBuilder().setOldTag(oldTag).setNewTag(newTag).build();
        try {
       //     ArchiveTag ntag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "upsertTag", utr, ArchiveTag.newBuilder());
        //    indexListener.tagChanged(oldTag, ntag);
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: "+e.getMessage());
        }

    }

    @Override
    public void deleteTag(String instance, ArchiveTag tag) {
        DeleteTagRequest dtr=DeleteTagRequest.newBuilder().setTag(tag).build();
        try {
        //    ArchiveTag rtag=(ArchiveTag)yamcsClient.executeRpc((Protocol.getYarchIndexControlAddress(instance)), "deleteTag", dtr, ArchiveTag.newBuilder());
        //    indexListener.tagRemoved(rtag);
        } catch (Exception e) {
            indexListener.log("Failed to remove tag: "+e.getMessage());
        }
    }

    @Override
    public boolean supportsTags() {
        return true;
    }

}
