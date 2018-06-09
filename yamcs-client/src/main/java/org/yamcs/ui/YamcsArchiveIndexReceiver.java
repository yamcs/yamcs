package org.yamcs.ui;

import java.util.concurrent.Future;

import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsConnector;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.CreateTagRequest;
import org.yamcs.protobuf.Rest.EditTagRequest;
import org.yamcs.protobuf.Rest.ListTagsResponse;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.ui.archivebrowser.ArchiveIndexListener;
import org.yamcs.ui.archivebrowser.ArchiveIndexReceiver;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.handler.codec.http.HttpMethod;

public class YamcsArchiveIndexReceiver implements ArchiveIndexReceiver {
    ArchiveIndexListener indexListener;

    volatile private boolean receiving = false;

    YamcsConnector yconnector;

    public YamcsArchiveIndexReceiver(YamcsConnector yconnector) {
        this.yconnector = yconnector;
    }

    @Override
    public void setIndexListener(ArchiveIndexListener ail) {
        this.indexListener = ail;
    }

    @Override
    public void getIndex(final String instance, final TimeInterval interval) {
        if (receiving) {
            indexListener.log("already receiving data");
            return;
        }
        if (instance == null) {
            indexListener.receiveArchiveRecordsError("No yamcs instance to get data from");
            return;
        }

        yconnector.getExecutor().submit(() -> {
            try {
                RestClient restClient = yconnector.getRestClient();
                StringBuilder resource = new StringBuilder().append("/archive/" + instance + "/indexes?");

                if (interval.hasStart()) {
                    resource.append("start=" + TimeEncoding.toString(interval.getStart()));
                }
                if (interval.hasEnd()) {
                    resource.append("&stop=" + TimeEncoding.toString(interval.getEnd()));
                }

                Future<Void> f = restClient.doBulkGetRequest(resource.toString(), new BulkRestDataReceiver() {
                    @Override
                    public void receiveData(byte[] data) throws YamcsApiException {
                        try {
                            indexListener.receiveArchiveRecords(IndexResult.parseFrom(data));
                        } catch (InvalidProtocolBufferException e) {
                            throw new YamcsApiException("Error parsing index result: " + e.getMessage());
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
            } finally {
                receiving = false;
            }
        });
    }

    @Override
    public void getTag(final String instance, final TimeInterval interval) {
        if (receiving) {
            indexListener.log("already receiving data");
            return;
        }
        yconnector.getExecutor().submit(() -> {
            try {
                RestClient restClient = yconnector.getRestClient();
                StringBuilder resource = new StringBuilder().append("/archive/" + instance + "/tags?");

                if (interval.hasStart()) {
                    resource.append("start=" + TimeEncoding.toString(interval.getStart()));
                }
                if (interval.hasEnd()) {
                    resource.append("&stop=" + TimeEncoding.toString(interval.getEnd()));
                }

                Future<byte[]> f = restClient.doRequest(resource.toString(), HttpMethod.GET);
                byte[] data = f.get();
                ListTagsResponse ltr = ListTagsResponse.parseFrom(data);
                indexListener.receiveTags(ltr.getTagList());
                indexListener.receiveTagsFinished();

            } catch (Exception e) {
                e.printStackTrace();
                indexListener.receiveArchiveRecordsError(e.getMessage());
            } finally {
                receiving = false;
            }
        });
    }

    @Override
    public void insertTag(String instance, ArchiveTag tag) {
        CreateTagRequest ctr = CreateTagRequest.newBuilder().setColor(tag.getColor())
                .setDescription(tag.getDescription()).setName(tag.getName())
                .setStart(TimeEncoding.toString(tag.getStart())).setStop(TimeEncoding.toString(tag.getStop())).build();
        try {
            RestClient restClient = yconnector.getRestClient();
            StringBuilder resource = new StringBuilder().append("/archive/" + instance + "/tags");
            byte[] data = restClient.doRequest(resource.toString(), HttpMethod.POST, ctr.toByteArray()).get();
            indexListener.tagAdded(ArchiveTag.parseFrom(data));
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: " + e.getMessage());
        }
    }

    @Override
    public void updateTag(String instance, ArchiveTag oldTag, ArchiveTag newTag) {
        EditTagRequest etr = EditTagRequest.newBuilder().setColor(newTag.getColor())
                .setDescription(newTag.getDescription()).setName(newTag.getName())
                .setStart(TimeEncoding.toString(newTag.getStart())).setStop(TimeEncoding.toString(newTag.getStop()))
                .build();

        try {
            RestClient restClient = yconnector.getRestClient();
            StringBuilder resource = new StringBuilder().append("/archive/").append(instance).append("/tags/")
                    .append(TimeEncoding.toString(oldTag.getStart())).append("/").append(oldTag.getId());
            byte[] data = restClient.doRequest(resource.toString(), HttpMethod.PATCH, etr.toByteArray()).get();
            indexListener.tagChanged(oldTag, ArchiveTag.parseFrom(data));
        } catch (Exception e) {
            indexListener.log("Failed to insert tag: " + e.getMessage());
        }

    }

    @Override
    public void deleteTag(String instance, ArchiveTag tag) {
        RestClient restClient = yconnector.getRestClient();
        StringBuilder resource = new StringBuilder().append("/archive/").append(instance).append("/tags/")
                .append(TimeEncoding.toString(tag.getStart())).append("/").append(tag.getId());
        try {
            byte[] data = restClient.doRequest(resource.toString(), HttpMethod.DELETE).get();
            indexListener.tagRemoved(ArchiveTag.parseFrom(data));
        } catch (Exception e) {
            indexListener.log("Failed to remove tag: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsTags() {
        return true;
    }

}
