package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.buckets.Bucket;
import org.yamcs.buckets.BucketProperties;
import org.yamcs.buckets.FileSystemBucket;
import org.yamcs.buckets.ObjectProperties;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractBucketsApi;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.BucketLocation;
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

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class BucketsApi extends AbstractBucketsApi<Context> {

    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w[\\w\\-]+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    @Override
    public void listBuckets(Context ctx, ListBucketsRequest request, Observer<ListBucketsResponse> observer) {
        var bucketManager = YamcsServer.getServer().getBucketManager();
        try {
            List<Bucket> buckets = bucketManager.listBuckets().stream()
                    .filter(bucket -> mayReadBucket(bucket.getName(), ctx.user))
                    .collect(Collectors.toList());

            var futures = new ArrayList<CompletableFuture<BucketInfo>>();
            for (Bucket bucket : buckets) {
                futures.add(bucket.getPropertiesAsync().thenApply(props -> {
                    return toBucketInfo(bucket, props);
                }));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((res, err) -> {
                if (err == null) {
                    var responseb = ListBucketsResponse.newBuilder();
                    for (var future : futures) {
                        responseb.addBuckets(future.join());
                    }
                    observer.complete(responseb.build());
                } else {
                    observer.completeExceptionally(err.getCause());
                }
            });
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void getBucket(Context ctx, GetBucketRequest request, Observer<BucketInfo> observer) {
        String bucketName = request.getBucketName();

        checkReadBucketPrivilege(bucketName, ctx.user);
        Bucket b = verifyAndGetBucket(bucketName, ctx.user);
        b.getPropertiesAsync().whenComplete((props, err) -> {
            if (err == null) {
                observer.complete(toBucketInfo(b, props));
            } else {
                observer.completeExceptionally(err.getCause());
            }
        });
    }

    @Override
    public void createBucket(Context ctx, CreateBucketRequest request, Observer<BucketInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        verifyBucketName(request.getName());
        var bucketManager = YamcsServer.getServer().getBucketManager();
        try {
            if (bucketManager.getBucket(request.getName()) != null) {
                throw new BadRequestException("A bucket with the name '" + request.getName() + "' already exist");
            }
            var b = bucketManager.createBucket(request.getName());
            b.getPropertiesAsync().whenComplete((props, err) -> {
                if (err == null) {
                    observer.complete(toBucketInfo(b, props));
                } else {
                    observer.completeExceptionally(err.getCause());
                }
            });
        } catch (IOException e) {
            throw new InternalServerErrorException("Error when creating bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBucket(Context ctx, DeleteBucketRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageAnyBucket);

        String bucketName = request.getBucketName();

        var bucketManager = YamcsServer.getServer().getBucketManager();
        Bucket b = verifyAndGetBucket(bucketName, ctx.user);
        try {
            bucketManager.deleteBucket(b.getName());
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
        Bucket bucket = verifyAndGetBucket(bucketName, ctx.user);
        bucket.findObjectAsync(objName).whenComplete((props, err) -> {
            if (err == null) {
                if (props == null) {
                    observer.completeExceptionally(new NotFoundException());
                } else {
                    observer.complete(toObjectInfo(props));
                }
            } else {
                observer.completeExceptionally(new InternalServerErrorException(
                        "Error when retrieving object: " + err.getMessage(), err));
            }
        });
    }

    @Override
    public void getObject(Context ctx, GetObjectRequest request, Observer<HttpBody> observer) {
        String bucketName = request.getBucketName();
        checkReadBucketPrivilege(bucketName, ctx.user);

        String objName = request.getObjectName();
        Bucket bucket = verifyAndGetBucket(bucketName, ctx.user);

        bucket.findObjectAsync(objName).whenComplete((props, err) -> {
            if (err == null) {
                if (props == null) {
                    observer.completeExceptionally(new NotFoundException());
                } else {
                    bucket.getObjectAsync(objName).whenComplete((objData, err2) -> {
                        if (err2 == null) {
                            String contentType = props.contentType() != null
                                    ? props.contentType()
                                    : "application/octet-stream";

                            HttpBody body = HttpBody.newBuilder()
                                    .setContentType(contentType)
                                    .setData(ByteString.copyFrom(objData))
                                    .build();

                            observer.complete(body);
                        } else {
                            observer.completeExceptionally(err2);
                        }
                    });
                }
            } else {
                observer.completeExceptionally(new InternalServerErrorException(
                        "Error when retrieving object: " + err.getMessage(), err));
            }
        });
    }

    @Override
    public void uploadObject(Context ctx, UploadObjectRequest request, Observer<Empty> observer) {
        String bucketName = request.getBucketName();

        checkManageBucketPrivilege(bucketName, ctx.user);
        Bucket bucket = verifyAndGetBucket(bucketName, ctx.user);
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

        bucket.putObjectAsync(objectName, contentType, body.getMetadataMap(), objectData).whenComplete((res, err) -> {
            if (err == null) {
                observer.complete(Empty.getDefaultInstance());
            } else {
                observer.completeExceptionally(new InternalServerErrorException(
                        "Error while uploading object to bucket: " + err.getMessage(), err));
            }
        });
    }

    @Override
    public void listObjects(Context ctx, ListObjectsRequest request, Observer<ListObjectsResponse> observer) {
        String bucketName = request.getBucketName();

        checkReadBucketPrivilege(bucketName, ctx.user);
        Bucket b = verifyAndGetBucket(bucketName, ctx.user);

        String delimiter = request.hasDelimiter() ? request.getDelimiter() : null;
        String prefix = request.hasPrefix() ? request.getPrefix() : null;

        CompletableFuture<List<ObjectProperties>> objectsFuture;
        List<String> prefixes = new ArrayList<>();
        if (delimiter == null) {
            objectsFuture = b.listObjectsAsync(prefix);
        } else {
            int prefixLength = prefix != null ? prefix.length() : 0;
            objectsFuture = b.listObjectsAsync(prefix, props -> {
                String name = props.name();
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
        }

        objectsFuture.whenComplete((objects, err) -> {
            if (err == null) {
                Collections.sort(prefixes);
                var responseb = ListObjectsResponse.newBuilder()
                        .addAllPrefixes(prefixes);
                for (var props : objects) {
                    responseb.addObjects(toObjectInfo(props));
                }
                observer.complete(responseb.build());
            } else {
                observer.completeExceptionally(err);
            }
        });
    }

    @Override
    public void deleteObject(Context ctx, DeleteObjectRequest request, Observer<Empty> observer) {
        String bucketName = request.getBucketName();
        checkManageBucketPrivilege(bucketName, ctx.user);

        String objName = request.getObjectName();
        Bucket bucket = verifyAndGetBucket(bucketName, ctx.user);

        bucket.findObjectAsync(objName).whenComplete((props, err) -> {
            if (err == null) {
                if (props == null) {
                    observer.completeExceptionally(new NotFoundException());
                } else {
                    bucket.deleteObjectAsync(objName).whenComplete((res, err2) -> {
                        if (err2 == null) {
                            observer.complete(Empty.getDefaultInstance());
                        } else {
                            observer.completeExceptionally(new InternalServerErrorException(err2));
                        }
                    });
                }
            } else {
                observer.completeExceptionally(err);
            }
        });
    }

    private static BucketInfo toBucketInfo(Bucket bucket, BucketProperties props) {
        BucketInfo.Builder bucketb = BucketInfo.newBuilder()
                .setName(bucket.getName())
                .setLocation(BucketLocation.newBuilder()
                        .setName(bucket.getLocation().name())
                        .setDescription(bucket.getLocation().description())
                        .build())
                .setMaxSize(props.maxSize())
                .setMaxObjects(props.maxNumObjects())
                .setCreated(TimeEncoding.toProtobufTimestamp(props.created()))
                .setNumObjects(props.numObjects())
                .setSize(props.size());
        if (bucket instanceof FileSystemBucket) {
            FileSystemBucket fsBucket = (FileSystemBucket) bucket;
            bucketb.setDirectory(fsBucket.getBucketRoot().toAbsolutePath().normalize().toString());
        }
        return bucketb.build();
    }

    private static ObjectInfo toObjectInfo(ObjectProperties props) {
        var infob = ObjectInfo.newBuilder()
                .setCreated(TimeEncoding.toProtobufTimestamp(props.created()))
                .setName(props.name())
                .setSize(props.size())
                .putAllMetadata(props.metadata());

        var contentType = props.contentType();
        if (contentType != null) {
            infob.setContentType(contentType);
        }

        return infob.build();
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

    static Bucket verifyAndGetBucket(String bucketName, User user) throws HttpException {
        var bucketManager = YamcsServer.getServer().getBucketManager();
        try {
            Bucket bucket = bucketManager.getBucket(bucketName);
            if (bucket == null) {
                if (bucketName.equals(getUserBucketName(user))) {
                    try {
                        bucket = bucketManager.createBucket(bucketName);
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
