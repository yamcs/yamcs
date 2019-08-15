package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.ServiceUnavailableException;
import org.yamcs.protobuf.BucketInfo;
import org.yamcs.protobuf.CreateBucketRequest;
import org.yamcs.protobuf.ListBucketsResponse;
import org.yamcs.protobuf.ListObjectsResponse;
import org.yamcs.protobuf.ObjectInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * Implements object storage
 */
public class BucketRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(BucketRestHandler.class);
    static String BUCKET_NAME_PARAM = "bucketName";
    static String OBJECT_NAME_PARAM = "objectName";

    // max body size used for upload object (this includes both data and metadata).
    static final int MAX_BODY_SIZE = 5 * 1024 * 1024;
    static final int MAX_METADATA_SIZE = 16 * 1024;

    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[ \\w\\s\\-\\./]+");

    @Route(rpc = "Buckets.ListBuckets")
    public void listBuckets(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ManageAnyBucket);

        YarchDatabaseInstance yarch = BucketHelper.getYarch(req);
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
            completeOK(req, responseb.build());
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to resolve buckets", e);
        }
    }

    @Route(rpc = "Buckets.CreateBucket")
    public void createBucket(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ManageAnyBucket);

        CreateBucketRequest crb = req.bodyAsMessage(CreateBucketRequest.newBuilder()).build();
        BucketHelper.verifyBucketName(crb.getName());
        YarchDatabaseInstance yarch = BucketHelper.getYarch(req);
        try {
            if (yarch.getBucket(crb.getName()) != null) {
                throw new BadRequestException("A bucket with the name '" + crb.getName() + "' already exist");
            }
            yarch.createBucket(crb.getName());
        } catch (IOException e) {
            log.error("Error when creating bucket", e);
            throw new InternalServerErrorException("Error when creating bucket: " + e.getMessage());
        }
        completeOK(req);
    }

    @Route(rpc = "Buckets.DeleteBucket")
    public void deleteBucket(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ManageAnyBucket);

        YarchDatabaseInstance yarch = BucketHelper.getYarch(req);
        Bucket b = BucketHelper.verifyAndGetBucket(req);
        try {
            yarch.deleteBucket(b.getName());
        } catch (IOException e) {
            log.warn("Error when deleting bucket", e);
            throw new InternalServerErrorException("Error when deleting bucket: " + e.getMessage());
        }
        completeOK(req);
    }

    @Route(path = "/api/buckets/{instance}/{bucketName}/{objectName**}", method = "POST", maxBodySize = MAX_BODY_SIZE)
    public void uploadObject(RestRequest req) throws HttpException {
        BucketHelper.checkManageBucketPrivilege(req);
        String contentType = req.getHeader(HttpHeaderNames.CONTENT_TYPE);

        if (contentType.startsWith("multipart/form-data")) {
            uploadObjectMultipartFormData(req);
        } else if (contentType.startsWith("multipart/related")) {
            uploadObjectMultipartRelated(req);
        } else {
            uploadObjectSimple(req);
        }
    }

    private void uploadObjectSimple(RestRequest req) throws HttpException {
        Bucket b = BucketHelper.verifyAndGetBucket(req);
        String contentType = req.getHeader(HttpHeaderNames.CONTENT_TYPE);

        String objName = null;
        if (req.hasRouteParam(OBJECT_NAME_PARAM)) {
            objName = req.getRouteParam(OBJECT_NAME_PARAM);
        } else {
            objName = req.getQueryParameter("name");
        }
        saveObject(b, objName, contentType, req.bodyAsBuf(), null);
        completeOK(req);
    }

    private void uploadObjectMultipartFormData(RestRequest req) throws HttpException {
        HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(req.getHttpRequest());
        Bucket b = BucketHelper.verifyAndGetBucket(req);
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
        saveObject(b, fup.getFilename(), fup.getContentType(), fup.content(), metadata);
        completeOK(req);
    }

    private void uploadObjectMultipartRelated(RestRequest req) throws HttpException {
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
        BucketHelper.verifyObjectName(objName);
        byte[] objectData = new byte[buf.readableBytes()];
        buf.readBytes(objectData);

        try {
            bucket.putObject(objName, contentType, metadata, objectData);
        } catch (IOException e) {
            log.error("Error when uploading object {} to bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when uploading object to bucket: " + e.getMessage());
        }
    }

    @Route(rpc = "Buckets.ListObjects")
    public void listObjects(RestRequest req) throws HttpException {
        BucketHelper.checkReadBucketPrivilege(req);
        Bucket b = BucketHelper.verifyAndGetBucket(req);
        try {
            String delimiter = req.getQueryParameter("delimiter");
            String prefix = req.getQueryParameter("prefix");

            List<ObjectProperties> objects;
            ListObjectsResponse.Builder lor = ListObjectsResponse.newBuilder();

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
                lor.addAllPrefixes(prefixes);
            }

            for (ObjectProperties props : objects) {
                ObjectInfo oinfo = ObjectInfo.newBuilder().setCreated(TimeEncoding.toString(props.getCreated()))
                        .setName(props.getName()).setSize(props.getSize()).putAllMetadata(props.getMetadataMap())
                        .build();
                lor.addObjects(oinfo);
            }
            completeOK(req, lor.build());
        } catch (IOException e) {
            log.error("Error when retrieving object list from bucket {}", b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object list: " + e.getMessage());
        }

    }

    @Route(path = "/api/buckets/{instance}/{bucketName}/{objectName*}", method = "GET")
    public void getObject(RestRequest req) throws HttpException {
        BucketHelper.checkReadBucketPrivilege(req);

        String objName = req.getRouteParam(OBJECT_NAME_PARAM);
        Bucket b = BucketHelper.verifyAndGetBucket(req);
        try {
            ObjectProperties props = b.findObject(objName);
            if (props == null) {
                throw new NotFoundException(req);
            }
            byte[] objData = b.getObject(objName);
            String contentType = props.hasContentType() ? props.getContentType() : "application/octet-stream";
            completeOK(req, contentType, Unpooled.wrappedBuffer(objData));
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }
    }

    @Route(rpc = "Buckets.DeleteBucket")
    public void deleteObject(RestRequest req) throws HttpException {
        BucketHelper.checkManageBucketPrivilege(req);

        String objName = req.getRouteParam(OBJECT_NAME_PARAM);
        Bucket b = BucketHelper.verifyAndGetBucket(req);
        try {
            ObjectProperties props = b.findObject(objName);
            if (props == null) {
                throw new NotFoundException(req);
            }
            b.deleteObject(objName);
            completeOK(req);
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }
    }
}
