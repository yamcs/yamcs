package org.yamcs.web.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

/**
 * Implements the CFDP Protocol
 * 
 * @author ddw
 *
 */

public class CfdpRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(CfdpRestHandler.class);

    // TODO, factor these out (cp from BucketRestHandler)
    static String BUCKET_NAME_PARAM = "bucketName";
    static String OBJECT_NAME_PARAM = "objectName";
    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "POST")
    public void CfdpUpload(RestRequest req) throws HttpException {
        byte[] objData;

        /**
         * TODO largely copied from BucketRestHandler, probably better/easier to do a REST call to
         * BucketRestHandler.getObject
         */
        checkReadBucketPrivilege(req);
        String objName = req.getRouteParam(OBJECT_NAME_PARAM);
        Bucket b = verifyAndGetBucket(req);
        try {
            ObjectProperties props = b.findObject(objName);
            if (props == null) {
                throw new NotFoundException(req);
            }
            objData = b.getObject(objName);
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }
        /** /copied */

        // TODO, do the CFDP transfer with objData

        // TODO, return value
    }

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "GET")
    public void CfdpDownload(RestRequest req) throws HttpException {

        /**
         * TODO largely copied from BucketRestHandler, probably better/easier to do a REST call to
         * BucketRestHandler.uploadObject
         */
        checkManageBucketPrivilege(req);
        Bucket bucket = verifyAndGetBucket(req);
        String objName = req.getRouteParam(OBJECT_NAME_PARAM);
        verifyObjectName(objName);

        // TODO, get this data from the CFDP transfer
        byte[] objectData = null;

        // TODO, sane values for contentType and metadata
        String contentType = "";
        Map<String, String> metadata = new HashMap<String, String>();

        try {
            bucket.putObject(objName, contentType, metadata, objectData);
        } catch (IOException e) {
            log.error("Error when uploading object {} to bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when uploading object to bucket: " + e.getMessage());
        }

        // TODO, return value

    }

    @Route(path = "api/cfdp/list", method = "GET")
    public void CfdpList(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/info", method = "GET")
    public void CfdpInfo(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/cancel", method = "POST")
    public void CfdpCancel(RestRequest req) throws HttpException {
        // TODO
    }

    @Route(path = "api/cfdp/delete", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        // TODO
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    private void checkReadBucketPrivilege(RestRequest req) throws HttpException {
        String bucketName = req.getRouteParam(BUCKET_NAME_PARAM);
        if (bucketName.equals(getUserBucketName(req.getUser()))) {
            return; // user can do whatever to its own bucket (but not to increase quota!! currently not possible
                    // anyway)
        }

        if (!req.getUser().hasObjectPrivilege(ObjectPrivilegeType.ReadBucket, bucketName)
                && !req.getUser().hasObjectPrivilege(ObjectPrivilegeType.ManageBucket, bucketName)
                && !req.getUser().hasSystemPrivilege(SystemPrivilege.ManageAnyBucket)) {
            throw new ForbiddenException("Insufficient privileges to read bucket '" + bucketName + "'");
        }
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    private void checkManageBucketPrivilege(RestRequest req) throws HttpException {
        String bucketName = req.getRouteParam(BUCKET_NAME_PARAM);
        if (bucketName.equals(getUserBucketName(req.getUser()))) {
            return; // user can do whatever to its own bucket (but not to increase quota!! currently not possible
                    // anyway)
        }

        if (!req.getUser().hasObjectPrivilege(ObjectPrivilegeType.ManageBucket, bucketName)
                && !req.getUser().hasSystemPrivilege(SystemPrivilege.ManageAnyBucket)) {
            throw new ForbiddenException("Insufficient privileges to manage bucket '" + bucketName + "'");
        }
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    private static String getUserBucketName(User user) {
        return "user." + user.getUsername();
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    static Bucket verifyAndGetBucket(RestRequest req) throws HttpException {
        BucketDatabase bdb = getBucketDb(req);
        String bucketName = req.getRouteParam("bucketName");
        try {
            Bucket bucket = bdb.getBucket(bucketName);
            if (bucket == null) {
                if (bucketName.equals(getUserBucketName(req.getUser()))) {
                    try {
                        bucket = bdb.createBucket(bucketName);
                    } catch (IOException e) {
                        throw new InternalServerErrorException("Error creating user bucket", e);
                    }
                } else if (bucketName.equals("displays")) {
                    try {
                        bucket = bdb.createBucket(bucketName);
                    } catch (IOException e) {
                        throw new InternalServerErrorException("Error creating displays bucket", e);
                    }
                } else {
                    throw new NotFoundException(req);
                }
            }

            return bucket;
        } catch (IOException e) {
            throw new InternalServerErrorException("Error while resolving bucket", e);
        }
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    static private BucketDatabase getBucketDb(RestRequest req) throws HttpException {
        String yamcsInstance = verifyInstance(req, req.getRouteParam("instance"), true);
        YarchDatabaseInstance ydi = YarchDatabase.getInstance(yamcsInstance);
        try {
            BucketDatabase bdb = ydi.getBucketDatabase();
            if (bdb == null) {
                throw new NotFoundException(req);
            }
            return bdb;
        } catch (YarchException e) {
            throw new InternalServerErrorException("Bucket database not available", e);
        }
    }

    // TODO, refactor this out (cp from BucketRestHandler)
    static private void verifyObjectName(String objName) throws BadRequestException {
        if (objName == null) {
            throw new BadRequestException("No object name specified");
        }
        if (!OBJ_NAME_REGEXP.matcher(objName).matches()) {
            throw new BadRequestException("Invalid object name specified");
        }
    }

}
