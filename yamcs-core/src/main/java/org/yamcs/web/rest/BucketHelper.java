package org.yamcs.web.rest;

import java.io.IOException;
import java.util.regex.Pattern;

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

public class BucketHelper {

    static String BUCKET_NAME_PARAM = "bucketName";
    static String OBJECT_NAME_PARAM = "objectName";
    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    static void checkReadBucketPrivilege(RestRequest req) throws HttpException {
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

    static void checkManageBucketPrivilege(RestRequest req) throws HttpException {
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

    static String getUserBucketName(User user) {
        return "user." + user.getUsername();
    }

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

    static BucketDatabase getBucketDb(RestRequest req) throws HttpException {
        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);
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

    static void verifyObjectName(String objName) throws BadRequestException {
        if (objName == null) {
            throw new BadRequestException("No object name specified");
        }
        if (!OBJ_NAME_REGEXP.matcher(objName).matches()) {
            throw new BadRequestException("Invalid object name specified");
        }
    }

    static void verifyBucketName(String bucketName) throws BadRequestException {
        if (bucketName == null) {
            throw new BadRequestException("No bucketName specified");
        }
        if (!BUCKET_NAME_REGEXP.matcher(bucketName).matches()) {
            throw new BadRequestException("Invalid bucket name specified");
        }
    }

}
