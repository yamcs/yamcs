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
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.MethodNotAllowedException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class ArchiveTagRequestHandler extends RestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return listTags(req, tagDb);
            } else if (req.isPOST()) {
                return insertTag(req, tagDb);
            } else {
                throw new MethodNotAllowedException(req);
            }
        } else {
            // Need both tagTime and tagId
            if (!req.hasPathSegment(pathOffset + 1)) {
                throw new NotFoundException(req);
            }
            
            long tagTime = req.getPathSegmentAsLong(pathOffset);
            int tagId = req.getPathSegmentAsInt(pathOffset + 1);
            if (tagId < 1)
                throw new BadRequestException("Invalid tag ID");
            
            ArchiveTag existingTag;
            try {
                existingTag = tagDb.getTag(tagTime, tagId);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
            if (existingTag == null) {
                throw new NotFoundException(req, "No tag for ID (" + tagTime + ", " + tagId + ")");
            }
                    
            if (req.isGET()) {
               return getTag(req, existingTag);
            } else if (req.isPUT() || req.isPATCH() || req.isPOST()) {
                return updateTag(req, tagDb, existingTag);
            } else if (req.isDELETE()) {
                return deleteTag(req, tagDb, tagTime, tagId);
            } else {
                throw new MethodNotAllowedException(req);
            }
        }
    }
    
    /**
     * Lists tags
     */
    private RestResponse listTags(RestRequest req, TagDb tagDb) throws RestException {
        IntervalResult ir = RestUtils.scanForInterval(req);
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
        return new RestResponse(req, responseb.build(), SchemaRest.ListTagsResponse.WRITE);
    }
    
    /**
     * Outputs info on a single tag
     */
    private RestResponse getTag(RestRequest req, ArchiveTag tag) throws RestException {
        return new RestResponse(req, tag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Adds a new tag. The newly added tag is returned as a response so the user
     * knows the assigned id.
     */
    private RestResponse insertTag(RestRequest req, TagDb tagDb) throws RestException {
        CreateTagRequest request = req.bodyAsMessage(SchemaRest.CreateTagRequest.MERGE).build();
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(RestUtils.parseTime(request.getStart()));
        if (request.hasStop()) tagb.setStop(RestUtils.parseTime(request.getStop()));
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
        return new RestResponse(req, newTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Updates an existing tag. Returns the updated tag
     */
    private RestResponse updateTag(RestRequest req, TagDb tagDb, ArchiveTag tag) throws RestException {
        EditTagRequest request = req.bodyAsMessage(SchemaRest.EditTagRequest.MERGE).build();        
        
        // Patch the existing tag
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder(tag);
        if (request.hasName()) tagb.setName(request.getName());
        if (request.hasStart()) tagb.setStart(RestUtils.parseTime(request.getStart()));
        if (request.hasStop()) tagb.setStop(RestUtils.parseTime(request.getStop()));
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Override with query params
        if (req.hasQueryParameter("name")) tagb.setName(req.getQueryParameter("name"));
        if (req.hasQueryParameter("start")) tagb.setStart(RestUtils.parseTime(req.getQueryParameter("start")));
        if (req.hasQueryParameter("stop")) tagb.setStop(RestUtils.parseTime(req.getQueryParameter("stop")));
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
        
        return new RestResponse(req, updatedTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    /**
     * Deletes the identified tag. Returns the deleted tag
     */
    private RestResponse deleteTag(RestRequest req, TagDb tagDb, long tagTime, int tagId) throws RestException {
        ArchiveTag deletedTag;
        try {
            deletedTag = tagDb.deleteTag(tagTime, tagId);
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException(req);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return new RestResponse(req, deletedTag, SchemaYamcs.ArchiveTag.WRITE);
    }
    
    private static TagDb getTagDb(String yamcsInstance) throws RestException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getDefaultStorageEngine().getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load Tag DB", e);
        }
    }
}
