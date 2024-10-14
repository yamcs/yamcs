package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractBucketsApi;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.CreateBucketRequest;
import org.yamcs.protobuf.DeleteBucketRequest;
import org.yamcs.protobuf.DeleteObjectRequest;
import org.yamcs.protobuf.GetBucketRequest;
import org.yamcs.protobuf.GetObjectInfoRequest;
import org.yamcs.protobuf.GetObjectRequest;
import org.yamcs.protobuf.ListBucketsRequest;
import org.yamcs.protobuf.ListBucketsResponse;
import org.yamcs.protobuf.ListObjectsRequest;
import org.yamcs.protobuf.ListObjectsResponse;
import org.yamcs.protobuf.ObjectInfo;
import org.yamcs.protobuf.UploadObjectRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.FileSystemBucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class BucketsApi extends AbstractBucketsApi<Context> {

    private static final Log log = new Log(BucketsApi.class);

    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w[\\w\\-]+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    @Override
    public void listBuckets(Context ctx, ListBucketsRequest request, Observer<ListBucketsResponse> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        YarchDatabaseInstance yarch = getYarch(instance);
        try {
            List<Bucket> buckets = yarch.listBuckets().stream()
                    .filter(bucket -> mayReadBucket(bucket.getName(), ctx.user))
                    .collect(Collectors.toList());
            ListBucketsResponse.Builder responseb = ListBucketsResponse.newBuilder();
            for (Bucket bucket : buckets) {
                responseb.addBuckets(toBucketInfo(bucket));
            }
            observer.complete(responseb.build());
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void getBucket(Context ctx, GetBucketRequest request, Observer<BucketInfo> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();

        checkReadBucketPrivilege(bucketName, ctx.user);
        Bucket b = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            observer.complete(toBucketInfo(b));
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void createBucket(Context ctx, CreateBucketRequest request, Observer<BucketInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        verifyBucketName(request.getName());
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        YarchDatabaseInstance yarch = getYarch(instance);
        try {
            if (yarch.getBucket(request.getName()) != null) {
                throw new BadRequestException("A bucket with the name '" + request.getName() + "' already exist");
            }
            var b = yarch.createBucket(request.getName());
            observer.complete(toBucketInfo(b));
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when creating bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBucket(Context ctx, DeleteBucketRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();

        YarchDatabaseInstance yarch = getYarch(instance);
        Bucket b = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            yarch.deleteBucket(b.getName());
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when deleting bucket: " + e.getMessage(), e);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void getObjectInfo(Context ctx, GetObjectInfoRequest request, Observer<ObjectInfo> observer) {
        String bucketName = request.getBucketName();
        checkReadBucketPrivilege(bucketName, ctx.user);

        String objName = request.getObjectName();
        Bucket bucket = verifyAndGetBucket(YamcsServer.GLOBAL_INSTANCE, bucketName, ctx.user);
        try {
            ObjectProperties props = bucket.findObject(objName);
            if (props == null) {
                throw new NotFoundException();
            } else {
                observer.complete(toObjectInfo(props));
            }
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage(), e);
        }
    }

    @Override
    public void getObject(Context ctx, GetObjectRequest request, Observer<HttpBody> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();
        checkReadBucketPrivilege(bucketName, ctx.user);

        String objName = request.getObjectName();
        Bucket bucket = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            ObjectProperties props = bucket.findObject(objName);
            if (props == null) {
                throw new NotFoundException();
            }
            byte[] objData = bucket.getObject(objName);
            String contentType = props.hasContentType() ? props.getContentType() : "application/octet-stream";

            HttpBody body = HttpBody.newBuilder()
                    .setContentType(contentType)
                    .setData(ByteString.copyFrom(objData))
                    .build();

            observer.complete(body);
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage(), e);
        }
    }

    @Override
    public void uploadObject(Context ctx, UploadObjectRequest request, Observer<Empty> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();

        checkManageBucketPrivilege(bucketName, ctx.user);
        Bucket bucket = verifyAndGetBucket(instance, bucketName, ctx.user);
        HttpBody body = request.getData();

        String objectName;
        if (request.hasObjectName()) {
            objectName = request.getObjectName();
        } else if (body.hasFilename()) {
            objectName = body.getFilename();
        } else {
            throw new BadRequestException("Unknown target object name");
        }
        verifyObjectName(objectName);

        String contentType = body.hasContentType() ? body.getContentType() : null;
        byte[] objectData = body.getData().toByteArray();
        try {
            bucket.putObject(objectName, contentType, body.getMetadataMap(), objectData);
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when uploading object to bucket: " + e.getMessage(), e);
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void listObjects(Context ctx, ListObjectsRequest request, Observer<ListObjectsResponse> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();

        checkReadBucketPrivilege(bucketName, ctx.user);
        Bucket b = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            String delimiter = request.hasDelimiter() ? request.getDelimiter() : null;
            String prefix = request.hasPrefix() ? request.getPrefix() : null;

            List<ObjectProperties> objects;
            ListObjectsResponse.Builder responseb = ListObjectsResponse.newBuilder();

            if (delimiter == null) {
                objects = b.listObjects(prefix);
            } else {
                int prefixLength = prefix != null ? prefix.length() : 0;
                List<String> prefixes = new ArrayList<>();
                objects = b.listObjects(prefix, op -> {
                    String name = op.getName();
                    int idx = name.indexOf(delimiter, prefixLength);
                    if (idx != -1) {
                        String pref = name.substring(0, idx + 1);
                        if (prefixes.isEmpty() || !prefixes.get(prefixes.size() - 1).equals(pref)) {
                            prefixes.add(pref);
                        }
                        return false;
                    } else {
                        return true;
                    }
                });
                Collections.sort(prefixes);
                responseb.addAllPrefixes(prefixes);
            }

            for (ObjectProperties props : objects) {
                responseb.addObjects(toObjectInfo(props));
            }
            observer.complete(responseb.build());
        } catch (IOException e) {
            log.error("Error when retrieving object list from bucket {}", b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object list: " + e.getMessage());
        }
    }

    @Override
    public void deleteObject(Context ctx, DeleteObjectRequest request, Observer<Empty> observer) {
        var instance = request.hasInstance() ? request.getInstance() : YamcsServer.GLOBAL_INSTANCE;
        String bucketName = request.getBucketName();
        checkManageBucketPrivilege(bucketName, ctx.user);

        String objName = request.getObjectName();
        Bucket bucket = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            ObjectProperties props = bucket.findObject(objName);
            if (props == null) {
                throw new NotFoundException();
            }
            bucket.deleteObject(objName);
            observer.complete(Empty.getDefaultInstance());
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }
    }

    private static BucketInfo toBucketInfo(Bucket bucket) throws IOException {
        BucketProperties props = bucket.getProperties();
        BucketInfo.Builder bucketb = BucketInfo.newBuilder()
                .setName(bucket.getName())
                .setMaxSize(props.getMaxSize())
                .setMaxObjects(props.getMaxNumObjects())
                .setCreated(TimeEncoding.toProtobufTimestamp(props.getCreated()))
                .setNumObjects(props.getNumObjects())
                .setSize(props.getSize());
        if (bucket instanceof FileSystemBucket) {
            FileSystemBucket fsBucket = (FileSystemBucket) bucket;
            bucketb.setDirectory(fsBucket.getBucketRoot().toAbsolutePath().normalize().toString());
        }
        return bucketb.build();
    }

    private static ObjectInfo toObjectInfo(ObjectProperties props) {
        return ObjectInfo.newBuilder()
                .setCreated(TimeEncoding.toProtobufTimestamp(props.getCreated()))
                .setName(props.getName())
                .setSize(props.getSize())
                .putAllMetadata(props.getMetadataMap())
                .build();
    }

    public static void checkReadBucketPrivilege(String bucketName, User user) throws HttpException {
        if (!mayReadBucket(bucketName, user)) {
            throw new ForbiddenException("Insufficient privileges to read bucket '" + bucketName + "'");
        }
    }

    private static boolean mayReadBucket(String bucketName, User user) {
        if (bucketName.equals(getUserBucketName(user))) {
            return true; // user can do whatever to its own bucket (but not to increase quota!! currently not possible
            // anyway)
        }

        return user.hasObjectPrivilege(ObjectPrivilegeType.ReadBucket, bucketName)
                || user.hasObjectPrivilege(ObjectPrivilegeType.ManageBucket, bucketName)
                || user.hasSystemPrivilege(SystemPrivilege.ManageAnyBucket);
    }

    public static void checkManageBucketPrivilege(String bucketName, User user) throws HttpException {
        if (bucketName.equals(getUserBucketName(user))) {
            return; // user can do whatever to its own bucket (but not to increase quota!! currently not possible
                    // anyway)
        }

        if (!user.hasObjectPrivilege(ObjectPrivilegeType.ManageBucket, bucketName)
                && !user.hasSystemPrivilege(SystemPrivilege.ManageAnyBucket)) {
            throw new ForbiddenException("Insufficient privileges to manage bucket '" + bucketName + "'");
        }
    }

    static String getUserBucketName(User user) {
        return "user." + user.getName();
    }

    static Bucket verifyAndGetBucket(String instance, String bucketName, User user) throws HttpException {
        YarchDatabaseInstance yarch = getYarch(instance);
        try {
            Bucket bucket = yarch.getBucket(bucketName);
            if (bucket == null) {
                if (bucketName.equals(getUserBucketName(user))) {
                    try {
                        bucket = yarch.createBucket(bucketName);
                    } catch (IOException e) {
                        throw new InternalServerErrorException("Error creating user bucket", e);
                    }
                } else {
                    throw new NotFoundException();
                }
            }

            return bucket;
        } catch (IOException e) {
            throw new InternalServerErrorException("Error while resolving bucket", e);
        }
    }

    static YarchDatabaseInstance getYarch(String instance) throws HttpException {
        String yamcsInstance = InstancesApi.verifyInstance(instance, true);
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
