package org.yamcs.web.rest.archive;

import java.io.IOException;

import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Archive.GetTagsRequest;
import org.yamcs.protobuf.Archive.GetTagsResponse;
import org.yamcs.protobuf.Archive.InsertTagRequest;
import org.yamcs.protobuf.Archive.InsertTagResponse;
import org.yamcs.protobuf.Archive.UpdateTagRequest;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.MethodNotAllowedException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class ArchiveTagRequestHandler extends RestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return listTags(req);
            } else if (req.isPOST()) {
                return insertTag(req);
            } else {
                throw new MethodNotAllowedException(req);
            }
        } else {
            if (!req.hasPathSegment(pathOffset + 1)) {
                throw new NotFoundException(req);
            }
            
            long tagTime;
            try {
                tagTime = Long.parseLong(req.getPathSegment(pathOffset));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid tag time: " + req.getPathSegment(pathOffset));
            }
            
            int tagId;
            try {
                tagId = Integer.parseInt(req.getPathSegment(pathOffset + 1));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid tag id: " + req.getPathSegment(pathOffset + 1));
            }
            
            if (req.isPUT()) {
                return updateTag(req, tagTime, tagId);
            } else if (req.isDELETE()) {
                return deleteTag(req, tagTime, tagId);
            } else {
                throw new MethodNotAllowedException(req);
            }
        }
    }
    
    /**
     * Lists all tags (optionally filtered by request-body)
     */
    private RestResponse listTags(RestRequest req) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        
        // Start with default open-ended
        TimeInterval interval = new TimeInterval();
        
        // Check any additional options
        if (req.hasBody()) {
            GetTagsRequest request = req.bodyAsMessage(SchemaArchive.GetTagsRequest.MERGE).build();
            if (request.hasStart()) interval.setStart(request.getStart());
            if (request.hasStop()) interval.setStop(request.getStop());
        }
        
        // Build response with a callback from the TagDb, this is all happening on
        // the same thread.
        GetTagsResponse.Builder responseb = GetTagsResponse.newBuilder();
        try {
            tagDb.getTags(new TimeInterval(), new TagReceiver() {
                @Override
                public void onTag(ArchiveTag tag) {
                   responseb.addTags(tag);
                }

                @Override public void finished() {}
            });
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not load tags", e);
        }
        return new RestResponse(req, responseb.build(), SchemaArchive.GetTagsResponse.WRITE);
    }
    
    /**
     * Adds a new tag. The newly added tag is returned as a response so the user
     * knows the assigned id.
     */
    private RestResponse insertTag(RestRequest req) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        InsertTagRequest request = req.bodyAsMessage(SchemaArchive.InsertTagRequest.MERGE).build();
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(request.getStart());
        if (request.hasStop()) tagb.setStop(request.getStop());
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Do the insert
        ArchiveTag newTag;
        try {
            newTag = tagDb.insertTag(tagb.build());
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        // Echo back the tag, with its new ID
        InsertTagResponse.Builder responseb = InsertTagResponse.newBuilder();
        responseb.setTag(newTag);
        return new RestResponse(req, responseb.build(), SchemaArchive.InsertTagResponse.WRITE);
    }
    
    /**
     * Updates an existing tag. Returns nothing
     */
    private RestResponse updateTag(RestRequest req, long tagTime, int tagId) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        UpdateTagRequest request = req.bodyAsMessage(SchemaArchive.UpdateTagRequest.MERGE).build();
        if (tagId < 1)
            throw new BadRequestException("Invalid tag ID");
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(request.getStart());
        if (request.hasStop()) tagb.setStop(request.getStop());
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Do the update
        try {
            tagDb.updateTag(tagTime, tagId, tagb.build());
        } catch (YamcsException e) {
            throw new InternalServerErrorException(e);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return new RestResponse(req);
    }
    
    /**
     * Deletes the identified tag. Returns nothing, but if the tag
     * didn't exist, it returns a 404 Not Found
     */
    private RestResponse deleteTag(RestRequest req, long tagTime, int tagId) throws RestException {
        if (tagId < 1)
            throw new BadRequestException("Invalid tag ID");
        
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        try {
            tagDb.deleteTag(tagTime, tagId);
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException(req);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return new RestResponse(req);
    }
    
    private static TagDb getTagDb(String yamcsInstance) throws RestException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getDefaultStorageEngine().getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load tag-db", e);
        }
    }
}
