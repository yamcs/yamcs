package org.yamcs.http.api;

import java.io.IOException;

import org.yamcs.YamcsException;
import org.yamcs.api.Observer;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractTagApi;
import org.yamcs.protobuf.CreateTagRequest;
import org.yamcs.protobuf.DeleteTagRequest;
import org.yamcs.protobuf.EditTagRequest;
import org.yamcs.protobuf.GetTagRequest;
import org.yamcs.protobuf.ListTagsRequest;
import org.yamcs.protobuf.ListTagsResponse;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class TagApi extends AbstractTagApi<Context> {

    @Override
    public void listTags(Context ctx, ListTagsRequest request, Observer<ListTagsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        TagDb tagDb = getTagDb(instance);

        TimeInterval interval = new TimeInterval();
        if (request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        // Build response with a callback from the TagDb, this is all happening on
        // the same thread.
        ListTagsResponse.Builder responseb = ListTagsResponse.newBuilder();
        try {
            tagDb.getTags(interval, new TagReceiver() {
                @Override
                public void onTag(ArchiveTag tag) {
                    responseb.addTag(enrichTag(tag));
                }

                @Override
                public void finished() {
                }
            });
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not load tags", e);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTag(Context ctx, GetTagRequest request, Observer<ArchiveTag> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        TagDb tagDb = getTagDb(instance);

        long tagTime = TimeEncoding.fromProtobufTimestamp(request.getTagTime());
        int tagId = request.getTagId();

        ArchiveTag tag = verifyTag(tagDb, tagTime, tagId);
        observer.complete(enrichTag(tag));
    }

    @Override
    public void createTag(Context ctx, CreateTagRequest request, Observer<ArchiveTag> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        TagDb tagDb = getTagDb(instance);

        if (!request.hasName()) {
            throw new BadRequestException("Name is required");
        }

        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) {
            tagb.setStart(TimeEncoding.parse(request.getStart()));
        }
        if (request.hasStop()) {
            tagb.setStop(TimeEncoding.parse(request.getStop()));
        }
        if (request.hasDescription()) {
            tagb.setDescription(request.getDescription());
        }
        if (request.hasColor()) {
            tagb.setColor(request.getColor());
        }

        // Do the insert
        ArchiveTag newTag;
        try {
            newTag = tagDb.insertTag(tagb.build());
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        // Echo back the tag, with its assigned ID
        observer.complete(newTag);
    }

    @Override
    public void updateTag(Context ctx, EditTagRequest request, Observer<ArchiveTag> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        TagDb tagDb = getTagDb(instance);
        long tagTime = TimeEncoding.fromProtobufTimestamp(request.getTagTime());
        int tagId = request.getTagId();
        ArchiveTag tag = verifyTag(tagDb, tagTime, tagId);

        ArchiveTag.Builder tagb = ArchiveTag.newBuilder(tag);
        if (request.hasName()) {
            tagb.setName(request.getName());
        }
        if (request.hasStart()) {
            tagb.setStart(parseTime(request.getStart()));
        }
        if (request.hasStop()) {
            tagb.setStop(parseTime(request.getStop()));
        }
        if (request.hasDescription()) {
            tagb.setDescription(request.getDescription());
        }
        if (request.hasColor()) {
            tagb.setColor(request.getColor());
        }

        try {
            ArchiveTag updatedTag = tagDb.updateTag(tagTime, tag.getId(), tagb.build());
            observer.complete(updatedTag);
        } catch (YamcsException e) {
            throw new InternalServerErrorException(e);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Interprets the provided string as either an instant, or an ISO 8601 string and returns it as an instant of type
     * long
     */
    private static long parseTime(String datetime) {
        try {
            return Long.parseLong(datetime);
        } catch (NumberFormatException e) {
            return TimeEncoding.parse(datetime);
        }
    }

    @Override
    public void deleteTag(Context ctx, DeleteTagRequest request, Observer<ArchiveTag> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        TagDb tagDb = getTagDb(instance);

        long tagTime = TimeEncoding.fromProtobufTimestamp(request.getTagTime());
        int tagId = request.getTagId();
        ArchiveTag tag = verifyTag(tagDb, tagTime, tagId);
        ArchiveTag deletedTag;
        try {
            deletedTag = tagDb.deleteTag(tag.getStart(), tag.getId());
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException();
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        observer.complete(deletedTag);
    }

    private ArchiveTag enrichTag(ArchiveTag tag) {
        ArchiveTag.Builder enrichedTag = ArchiveTag.newBuilder(tag);
        if (tag.hasStart()) {
            enrichedTag.setStartUTC(TimeEncoding.toProtobufTimestamp(tag.getStart()));
        }
        if (tag.hasStop()) {
            enrichedTag.setStopUTC(TimeEncoding.toProtobufTimestamp(tag.getStop()));
        }
        return enrichedTag.build();
    }

    private static TagDb getTagDb(String yamcsInstance) throws HttpException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load Tag DB", e);
        }
    }

    private ArchiveTag verifyTag(TagDb tagDb, long tagTime, int tagId) throws HttpException {
        if (tagId < 1) {
            throw new BadRequestException("Invalid tag ID");
        }

        ArchiveTag tag;
        try {
            tag = tagDb.getTag(tagTime, tagId);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        if (tag == null) {
            throw new NotFoundException("No tag for ID (" + tagTime + ", " + tagId + ")");
        } else {
            return tag;
        }
    }
}
