package org.yamcs.web.rest.archive;

import java.io.IOException;

import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Rest.CreateTagRequest;
import org.yamcs.protobuf.Rest.EditTagRequest;
import org.yamcs.protobuf.Rest.ListTagsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.Route;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import io.netty.channel.ChannelFuture;

public class ArchiveTagRestHandler extends RestHandler {
    
    @Route(path = "/api/archive/:instance/tags", method = "GET")
    public ChannelFuture listTags(RestRequest req) throws HttpException {
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
                   responseb.addTag(tag);
                }

                @Override public void finished() {}
            });
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not load tags", e);
        }
        return sendOK(req, responseb.build(), SchemaRest.ListTagsResponse.WRITE);
    }
    
    @Route(path = "/api/archive/:instance/tags/:tagTime/:tagId", method = "GET")
    public ChannelFuture getTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);
        
        long tagTime = req.getDateRouteParam("tagTime");
        int tagId = req.getIntegerRouteParam("tagId");

        ArchiveTag tag = verifyTag(req, tagDb, tagTime, tagId); 
        return sendOK(req, tag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Adds a new tag. The newly added tag is returned as a response so the user
     * knows the assigned id.
     */
    @Route(path = "/api/archive/:instance/tags", method = "POST")
    public ChannelFuture createTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);
        
        CreateTagRequest request = req.bodyAsMessage(SchemaRest.CreateTagRequest.MERGE).build();
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(RestRequest.parseTime(request.getStart()));
        if (request.hasStop()) tagb.setStop(RestRequest.parseTime(request.getStop()));
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Do the insert
        ArchiveTag newTag;
        try {
            newTag = tagDb.insertTag(tagb.build());
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        // Echo back the tag, with its assigned ID
        return sendOK(req, newTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Updates an existing tag. Returns the updated tag
     */
    @Route(path = "/api/archive/:instance/tags/:tagTime/:tagId", method = { "PATCH", "PUT", "POST" })
    public ChannelFuture updateTag(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        TagDb tagDb = getTagDb(instance);
        ArchiveTag tag = verifyTag(req, tagDb, req.getDateRouteParam("tagTime"), req.getIntegerRouteParam("tagId"));
        
        EditTagRequest request = req.bodyAsMessage(SchemaRest.EditTagRequest.MERGE).build();        
        
        // Patch the existing tag
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder(tag);
        if (request.hasName()) tagb.setName(request.getName());
        if (request.hasStart()) tagb.setStart(RestRequest.parseTime(request.getStart()));
        if (request.hasStop()) tagb.setStop(RestRequest.parseTime(request.getStop()));
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Override with query params
        if (req.hasQueryParameter("name")) tagb.setName(req.getQueryParameter("name"));
        if (req.hasQueryParameter("start")) tagb.setStart(RestRequest.parseTime(req.getQueryParameter("start")));
        if (req.hasQueryParameter("stop")) tagb.setStop(RestRequest.parseTime(req.getQueryParameter("stop")));
        if (req.hasQueryParameter("description")) tagb.setDescription(req.getQueryParameter("description"));
        if (req.hasQueryParameter("color")) tagb.setColor(req.getQueryParameter("color"));
        
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
        
        return sendOK(req, updatedTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Deletes the identified tag. Returns the deleted tag
     */
    @Route(path = "/api/archive/:instance/tags/:tagTime/:tagId", method = "DELETE")
    public ChannelFuture deleteTag(RestRequest req, TagDb tagDb, long tagTime, int tagId) throws HttpException {
        ArchiveTag deletedTag;
        try {
            deletedTag = tagDb.deleteTag(tagTime, tagId);
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException(req);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return sendOK(req, deletedTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    private static TagDb getTagDb(String yamcsInstance) throws HttpException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getDefaultStorageEngine().getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load Tag DB", e);
        }
    }
    
    private ArchiveTag verifyTag(RestRequest req, TagDb tagDb, long tagTime, int tagId) throws HttpException {
        if (tagId < 1)
            throw new BadRequestException("Invalid tag ID");
        
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
