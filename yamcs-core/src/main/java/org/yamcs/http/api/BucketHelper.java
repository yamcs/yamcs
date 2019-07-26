package org.yamcs.http.api;

import java.io.IOException;
import java.util.regex.Pattern;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class BucketHelper {

    static String BUCKET_NAME_PARAM = "bucketName";
    static String OBJECT_NAME_PARAM = "objectName";
    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    static void checkReadBucketPrivilege(RestRequest req) throws HttpException {
        String bucketName = req.getRouteParam(BUCKET_NAME_PARAM);
        checkReadBucketPrivilege(bucketName, req.getUser());
    }

    static void checkReadBucketPrivilege(String bucketName, User user) throws HttpException {
        if (bucketName.equals(getUserBucketName(user))) {
            return; // user can do whatever to its own bucket (but not to increase quota!! currently not possible
                    // anyway)
        }

        if (!user.hasObjectPrivilege(ObjectPrivilegeType.ReadBucket, bucketName)
                && !user.hasObjectPrivilege(ObjectPrivilegeType.ManageBucket, bucketName)
                && !user.hasSystemPrivilege(SystemPrivilege.ManageAnyBucket)) {
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
        YarchDatabaseInstance yarch = getYarch(req);
        String bucketName = req.getRouteParam("bucketName");
        try {
            Bucket bucket = yarch.getBucket(bucketName);
            if (bucket == null) {
                if (bucketName.equals(getUserBucketName(req.getUser()))) {
                    try {
                        bucket = yarch.createBucket(bucketName);
                    } catch (IOException e) {
                        throw new InternalServerErrorException("Error creating user bucket", e);
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

    static YarchDatabaseInstance getYarch(RestRequest req) throws HttpException {
        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);
        return YarchDatabase.getInstance(yamcsInstance);
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
