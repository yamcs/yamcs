package org.yamcs.http.api.archive;

import java.io.IOException;

import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.Route;
import org.yamcs.protobuf.Archive.CreateTagRequest;
import org.yamcs.protobuf.Archive.EditTagRequest;
import org.yamcs.protobuf.Archive.ListTagsResponse;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class ArchiveTagRestHandler extends RestHandler {

    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.ListTags")
    public void listTags(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);

        IntervalResult ir = req.scanForInterval();
        TimeInterval interval = ir.asTimeInterval();

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
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.GetTag")
    public void getTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);

        long tagTime = req.getDateRouteParam("tagTime");
        int tagId = req.getIntegerRouteParam("tagId");

        ArchiveTag tag = verifyTag(req, tagDb, tagTime, tagId);
        completeOK(req, enrichTag(tag));
    }

    private ArchiveTag enrichTag(ArchiveTag tag) {
        ArchiveTag.Builder enrichedTag = ArchiveTag.newBuilder(tag);
        if (tag.hasStart()) {
            enrichedTag.setStartUTC(TimeEncoding.toString(tag.getStart()));
        }
        if (tag.hasStop()) {
            enrichedTag.setStopUTC(TimeEncoding.toString(tag.getStop()));
        }
        return enrichedTag.build();
    }

    /**
     * Adds a new tag. The newly added tag is returned as a response so the user knows the assigned id.
     */
    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.CreateTag")
    public void createTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);

        CreateTagRequest request = req.bodyAsMessage(CreateTagRequest.newBuilder()).build();
        if (!request.hasName()) {
            throw new BadRequestException("Name is required");
        }

        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) {
            tagb.setStart(RestRequest.parseTime(request.getStart()));
        }
        if (request.hasStop()) {
            tagb.setStop(RestRequest.parseTime(request.getStop()));
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
        completeOK(req, newTag);
    }

    /**
     * Updates an existing tag. Returns the updated tag
     */
    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.UpdateTag")
    public void updateTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);
        ArchiveTag tag = verifyTag(req, tagDb, req.getDateRouteParam("tagTime"), req.getIntegerRouteParam("tagId"));

        EditTagRequest request = req.bodyAsMessage(EditTagRequest.newBuilder()).build();

        // Patch the existing tag
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder(tag);
        if (request.hasName()) {
            tagb.setName(request.getName());
        }
        if (request.hasStart()) {
            tagb.setStart(RestRequest.parseTime(request.getStart()));
        }
        if (request.hasStop()) {
            tagb.setStop(RestRequest.parseTime(request.getStop()));
        }
        if (request.hasDescription()) {
            tagb.setDescription(request.getDescription());
        }
        if (request.hasColor()) {
            tagb.setColor(request.getColor());
        }

        // Override with query params
        if (req.hasQueryParameter("name")) {
            tagb.setName(req.getQueryParameter("name"));
        }
        if (req.hasQueryParameter("start")) {
            tagb.setStart(RestRequest.parseTime(req.getQueryParameter("start")));
        }
        if (req.hasQueryParameter("stop")) {
            tagb.setStop(RestRequest.parseTime(req.getQueryParameter("stop")));
        }
        if (req.hasQueryParameter("description")) {
            tagb.setDescription(req.getQueryParameter("description"));
        }
        if (req.hasQueryParameter("color")) {
            tagb.setColor(req.getQueryParameter("color"));
        }

        // Persist the update
        ArchiveTag updatedTag;
        try {
            long tagTime = tag.hasStart() ? tag.getStart() : 0;
            updatedTag = tagDb.updateTag(tagTime, tag.getId(), tagb.build());
        } catch (YamcsException e) {
            throw new InternalServerErrorException(e);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        completeOK(req, updatedTag);
    }

    /**
     * Deletes the identified tag. Returns the deleted tag
     */
    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.DeleteTag")
    public void deleteTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);
        ArchiveTag tag = verifyTag(req, tagDb, req.getDateRouteParam("tagTime"), req.getIntegerRouteParam("tagId"));
        ArchiveTag deletedTag;
        try {
            deletedTag = tagDb.deleteTag(tag.getStart(), tag.getId());
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException(req);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        completeOK(req, deletedTag);
    }

    private static TagDb getTagDb(String yamcsInstance) throws HttpException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load Tag DB", e);
        }
    }

    private ArchiveTag verifyTag(RestRequest req, TagDb tagDb, long tagTime, int tagId) throws HttpException {
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
            throw new NotFoundException(req, "No tag for ID (" + tagTime + ", " + tagId + ")");
        } else {
            return tag;
        }
    }
}
