package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.RouteContext;
import org.yamcs.http.ServiceUnavailableException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractBucketsApi;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.CreateBucketRequest;
import org.yamcs.protobuf.DeleteBucketRequest;
import org.yamcs.protobuf.DeleteObjectRequest;
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
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

public class BucketsApi extends AbstractBucketsApi<Context> {

    private static final Log log = new Log(BucketsApi.class);

    // max body size used for upload object (this includes both data and metadata).
    static final int MAX_BODY_SIZE = 5 * 1024 * 1024;
    static final int MAX_METADATA_SIZE = 16 * 1024;

    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    @Override
    public void listBuckets(Context ctx, ListBucketsRequest request, Observer<ListBucketsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        YarchDatabaseInstance yarch = getYarch(request.getInstance());
        try {
            List<BucketProperties> l = yarch.listBuckets();
            ListBucketsResponse.Builder responseb = ListBucketsResponse.newBuilder();
            for (BucketProperties bp : l) {
                BucketInfo.Builder bucketb = BucketInfo.newBuilder()
                        .setName(bp.getName());
                if (bp.hasNumObjects()) {
                    bucketb.setNumObjects(bp.getNumObjects());
                }
                if (bp.hasSize()) {
                    bucketb.setSize(bp.getSize());
                }
                responseb.addBuckets(bucketb);
            }
            observer.complete(responseb.build());
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void createBucket(Context ctx, CreateBucketRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        verifyBucketName(request.getName());
        YarchDatabaseInstance yarch = getYarch(request.getInstance());
        try {
            if (yarch.getBucket(request.getName()) != null) {
                throw new BadRequestException("A bucket with the name '" + request.getName() + "' already exist");
            }
            yarch.createBucket(request.getName());
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when creating bucket: " + e.getMessage(), e);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void deleteBucket(Context ctx, DeleteBucketRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        String instance = request.getInstance();
        String bucketName = request.getBucketName();

        YarchDatabaseInstance yarch = getYarch(request.getInstance());
        Bucket b = verifyAndGetBucket(instance, bucketName, ctx.user);
        try {
            yarch.deleteBucket(b.getName());
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when deleting bucket: " + e.getMessage(), e);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void getObject(Context ctx, GetObjectRequest request, Observer<HttpBody> observer) {
        String instance = request.getInstance();
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
            log.error("Error when retrieving object {} from bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }
    }

    @Override
    public void uploadObject(Context ctx, UploadObjectRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
        String bucketName = request.getBucketName();
        String objectName = request.getObjectName();

        checkManageBucketPrivilege(bucketName, ctx.user);
        Bucket bucket = verifyAndGetBucket(instance, bucketName, ctx.user);

        String contentType = ((RouteContext) ctx).nettyRequest.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType.startsWith("multipart/form-data")) {
            uploadObjectMultipartFormData(ctx, bucket);
        } else if (contentType.startsWith("multipart/related")) {
            uploadObjectMultipartRelated(ctx, bucket);
        } else {
            ByteBuf buf = ((RouteContext) ctx).getBody();
            saveObject(bucket, objectName, contentType, buf, null);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void listObjects(Context ctx, ListObjectsRequest request, Observer<ListObjectsResponse> observer) {
        String instance = request.getInstance();
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
                ObjectInfo oinfo = ObjectInfo.newBuilder()
                        .setCreated(TimeEncoding.toProtobufTimestamp(props.getCreated()))
                        .setName(props.getName())
                        .setSize(props.getSize())
                        .putAllMetadata(props.getMetadataMap())
                        .build();
                responseb.addObjects(oinfo);
            }
            observer.complete(responseb.build());
        } catch (IOException e) {
            log.error("Error when retrieving object list from bucket {}", b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object list: " + e.getMessage());
        }
    }

    @Override
    public void deleteObject(Context ctx, DeleteObjectRequest request, Observer<Empty> observer) {
        String instance = request.getInstance();
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

    private void uploadObjectMultipartFormData(Context ctx, Bucket bucket) throws HttpException {
        HttpRequest nettyRequest = ((RouteContext) ctx).fullNettyRequest;
        HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(nettyRequest);

        FileUpload fup = null;
        Map<String, String> metadata = new HashMap<>();
        int metadataSize = 0;
        for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
            if (data instanceof FileUpload) {
                if (fup != null) {
                    throw new BadRequestException("Only one file upload is allowed in the multipart/form");
                }
                fup = (FileUpload) data;
            } else if (data instanceof Attribute) {
                Attribute att = (Attribute) data;
                try {
                    String name = att.getName();
                    String value = att.getValue();
                    metadataSize += (name.length() + value.length());
                    metadata.put(att.getName(), att.getValue());
                } catch (IOException e) { // shouldn't happen for MemoryAttribute
                    log.warn("got error when reading form/data attribute value", e);
                    throw new InternalServerErrorException("error reading attribute value");
                }
            }
        }
        if (metadataSize > MAX_METADATA_SIZE) {
            throw new BadRequestException("Metadata size " + metadataSize
                    + " bytes exceeds maximum allowed " + MAX_METADATA_SIZE);
        }
        if (fup == null) {
            throw new BadRequestException("No file upload was found in the multipart/form");
        }
        saveObject(bucket, fup.getFilename(), fup.getContentType(), fup.content(), metadata);
    }

    private void uploadObjectMultipartRelated(Context ctx, Bucket bucket) throws HttpException {
        throw new ServiceUnavailableException("multipart/related uploads not yet implemented");
        /*
         * HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(req.getHttpRequest());
         * for(InterfaceHttpData data: decoder.getBodyHttpDatas()) {
         * System.out.println("data: "+data);
         * }
         */
    }

    private void saveObject(Bucket bucket, String objName, String contentType, ByteBuf buf,
            Map<String, String> metadata) throws HttpException {
        verifyObjectName(objName);
        byte[] objectData = new byte[buf.readableBytes()];
        buf.readBytes(objectData);

        try {
            bucket.putObject(objName, contentType, metadata, objectData);
        } catch (IOException e) {
            log.error("Error when uploading object {} to bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when uploading object to bucket: " + e.getMessage());
        }
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

    static void checkManageBucketPrivilege(String bucketName, User user) throws HttpException {
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
        String yamcsInstance = ManagementApi.verifyInstance(instance, true);
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
