package org.yamcs.web.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Cfdp.ListTransfersResponse;
import org.yamcs.protobuf.Cfdp.TransferStatus;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.CfdpDatabase;
import org.yamcs.yarch.CfdpDatabaseInstance;
import org.yamcs.yarch.CfdpTransfer;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

/**
 * Implements the CFDP Protocol
 * 
 * @author ddw
 *
 */

public class CfdpRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(CfdpRestHandler.class);

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "POST")
    public void CfdpUpload(RestRequest req) throws HttpException {
        byte[] objData;

        /**
         * TODO largely copied from BucketRestHandler, probably better/easier to do a REST call to
         * BucketRestHandler.getObject
         */
        BucketHelper.checkReadBucketPrivilege(req);
        String objName = req.getRouteParam(BucketHelper.OBJECT_NAME_PARAM);
        Bucket b = BucketHelper.verifyAndGetBucket(req);
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
        BucketHelper.checkManageBucketPrivilege(req);
        Bucket bucket = BucketHelper.verifyAndGetBucket(req);
        String objName = req.getRouteParam(BucketHelper.OBJECT_NAME_PARAM);
        BucketHelper.verifyObjectName(objName);

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

    @Route(path = "/api/cfdp/list", method = "GET")
    public void CfdpList(RestRequest req) throws HttpException {
        log.info("CfdpInfo");
        // TODO
    }

    // TODO update rest doc
    @Route(path = "/api/cfdp/:instance/info", method = "GET")
    public void CfdpInfo(RestRequest req) throws HttpException {
        log.info("CfdpList");

        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);

        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

        List<String> transferIds = req.getQueryParameterList("transaction ids", new ArrayList<String>());
        Collection<CfdpTransfer> transfers = transferIds.isEmpty()
                ? ci.getCfdpTransfers(req.getQueryParameterAsBoolean("all", true))
                : ci.getCfdpTransfers(transferIds.stream().map(Long::parseLong).collect(Collectors.toList()));

        ListTransfersResponse.Builder ltr = ListTransfersResponse.newBuilder();

        for (CfdpTransfer transfer : transfers) {
            ltr.addTransfers(TransferStatus.newBuilder()
                    .setTransferId(transfer.getId())
                    .setState(transfer.getState().getState())
                    .setLocalBucketName(transfer.getBucket().getName())
                    .setLocalObjectName(transfer.getObjectName())
                    .setRemotePath(transfer.getRemotePath())
                    .setDirection(transfer.getDirection())
                    .setTotalSize(transfer.getTotalSize())
                    .setSizeTransferred(transfer.getState().getTransferredSize()));
        }
        completeOK(req, ltr.build());
    }

    @Route(path = "/api/cfdp/cancel", method = "POST")
    public void CfdpCancel(RestRequest req) throws HttpException {
        log.info("CfdpCancel");

        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

        List<CfdpTransfer> transfers = ci.getCfdpTransfers();

        ListTransfersResponse.Builder ltr = ListTransfersResponse.newBuilder();

        for (CfdpTransfer transfer : transfers) {
            ltr.addTransfers(TransferStatus.newBuilder()
                    .setTransferId(transfer.getId())
                    .setState(transfer.getState().getState())
                    .setLocalBucketName(transfer.getBucket().getName())
                    .setLocalObjectName(transfer.getObjectName())
                    .setRemotePath(transfer.getRemotePath())
                    .setDirection(transfer.getDirection())
                    .setTotalSize(transfer.getTotalSize())
                    .setSizeTransferred(transfer.getState().getTransferredSize()));
        }
        completeOK(req, ltr.build());

    }
    /*
    @Route(path = "/api/cfdp/delete", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        // TODO
    }
    
    @Route(path = "/api/cfdp/pause", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        // TODO
    }
    
    @Route(path = "/api/cfdp/resume", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        // TODO
    }
    */
}
