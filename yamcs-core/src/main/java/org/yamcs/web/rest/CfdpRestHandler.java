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
import org.yamcs.cfdp.CancelRequest;
import org.yamcs.cfdp.CfdpDatabase;
import org.yamcs.cfdp.CfdpDatabaseInstance;
import org.yamcs.cfdp.CfdpTransaction;
import org.yamcs.cfdp.CfdpTransfer;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.PutRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.protobuf.Cfdp.CancelTransfersResponse;
import org.yamcs.protobuf.Cfdp.DownloadResponse;
import org.yamcs.protobuf.Cfdp.InfoTransfersResponse;
import org.yamcs.protobuf.Cfdp.ListRemoteFilesResponse;
import org.yamcs.protobuf.Cfdp.PausedTransfersResponse;
import org.yamcs.protobuf.Cfdp.RemoteFile;
import org.yamcs.protobuf.Cfdp.ResumedTransfersResponse;
import org.yamcs.protobuf.Cfdp.TransferStatus;
import org.yamcs.protobuf.Cfdp.UploadResponse;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.yarch.Bucket;
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

        log.info("CfdpUpload");

        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);
        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

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

        String target = req.getQueryParameter("target");
        boolean overwrite = req.getQueryParameterAsBoolean("overwrite", true);
        boolean createpath = req.getQueryParameterAsBoolean("createpath", true);

        CfdpTransfer transfer = (CfdpTransfer) ci.processRequest(
                new PutRequest(CfdpDatabase.mySourceId, 2, objName, target, overwrite, createpath, b, objData));

        UploadResponse.Builder ur = UploadResponse.newBuilder();

        ur.setTransferId(transfer.getTransactionId().getSequenceNumber());

        completeOK(req, ur.build());
    }

    @Route(path = "/api/cfdp/:instance/:bucketName/:objectName", method = "GET")
    public void CfdpDownload(RestRequest req) throws HttpException {
        log.info("CfdpDownload");

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

        // TODO, get the transferId using the CFDP service
        long transferId = 0;

        DownloadResponse.Builder dr = DownloadResponse.newBuilder();

        dr.setTransferId(transferId);

        completeCREATED(req, dr.build());
    }

    @Route(path = "/api/cfdp/list", method = "GET")
    public void CfdpList(RestRequest req) throws HttpException {
        log.info("CfdpInfo");

        ListRemoteFilesResponse.Builder lrfr = ListRemoteFilesResponse.newBuilder();

        String remotePath = req.getQueryParameter("target");

        // TODO get the remote files using CFDP
        Collection<RemoteFile> remoteFiles = null;

        lrfr.setRemotePath(remotePath);
        for (RemoteFile rf : remoteFiles) {
            lrfr.addFilepaths(rf);
        }
        completeOK(req, lrfr.build());
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

        InfoTransfersResponse.Builder itr = InfoTransfersResponse.newBuilder();

        for (CfdpTransfer transfer : transfers) {
            itr.addTransfers(TransferStatus.newBuilder()
                    .setTransferId(transfer.getTransactionId().getSequenceNumber())
                    .setState(transfer.getTransferState())
                    .setLocalBucketName(transfer.getRequest().getBucket().getName())
                    .setLocalObjectName(transfer.getRequest().getObjectName())
                    .setRemotePath(transfer.getRequest().getTargetPath())
                    .setDirection(transfer.getDirection())
                    .setTotalSize(transfer.getTotalSize())
                    .setSizeTransferred(transfer.getTransferredSize()));
        }
        completeOK(req, itr.build());
    }

    // TODO update rest doc
    @Route(path = "/api/cfdp/:instance/cancel", method = "POST")
    public void CfdpCancel(RestRequest req) throws HttpException {
        log.info("CfdpCancel");

        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);

        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

        List<String> transferIds = req.getQueryParameterList("transaction ids", new ArrayList<String>());
        Collection<CfdpTransfer> transfers = transferIds.isEmpty()
                ? ci.getCfdpTransfers(true)
                : ci.getCfdpTransfers(transferIds.stream().map(Long::parseLong).collect(Collectors.toList()));

        List<CfdpTransaction> cancelledTransfers = transfers.stream()
                .map(CancelRequest::new)
                .map(ci::processRequest)
                .filter(x -> x != null)
                .collect(Collectors.toList());

        CancelTransfersResponse.Builder ctr = CancelTransfersResponse.newBuilder();

        for (CfdpTransaction transfer : cancelledTransfers) {
            ctr.addTransfers(transfer.getTransactionId().getSequenceNumber());
        }
        completeOK(req, ctr.build());
    }

    @Route(path = "/api/cfdp/delete", method = "POST")
    public void CfdpDelete(RestRequest req) throws HttpException {
        log.info("CfdpDelete");

        String pathToDelete = req.getQueryParameter("target");
        // TODO issue delete command

        completeOK(req);
    }

    // TODO update rest documentation
    @Route(path = "/api/cfdp/:instance/pause", method = "POST")
    public void CfdpPause(RestRequest req) throws HttpException {
        log.info("CfdpPause");

        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);

        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

        List<String> transferIds = req.getQueryParameterList("transaction ids", new ArrayList<String>());
        Collection<CfdpTransfer> transfers = transferIds.isEmpty()
                ? ci.getCfdpTransfers(true)
                : ci.getCfdpTransfers(transferIds.stream().map(Long::parseLong).collect(Collectors.toList()));

        List<CfdpTransaction> pausedTransfers = transfers.stream()
                .map(PauseRequest::new)
                .map(ci::processRequest)
                .filter(x -> x != null)
                .collect(Collectors.toList());

        PausedTransfersResponse.Builder ptr = PausedTransfersResponse.newBuilder();

        for (CfdpTransaction transfer : pausedTransfers) {
            ptr.addTransfers(transfer.getTransactionId().getSequenceNumber());
        }
        completeOK(req, ptr.build());
    }

    // TODO update rest documentation
    @Route(path = "/api/cfdp/:instance/resume", method = "POST")
    public void CfdpResume(RestRequest req) throws HttpException {
        log.info("CfdpResume");

        String yamcsInstance = RestHandler.verifyInstance(req, req.getRouteParam("instance"), true);

        CfdpDatabaseInstance ci = CfdpDatabase.getInstance(yamcsInstance);

        List<String> transferIds = req.getQueryParameterList("transaction ids", new ArrayList<String>());
        Collection<CfdpTransfer> transfers = transferIds.isEmpty()
                ? ci.getCfdpTransfers(true)
                : ci.getCfdpTransfers(transferIds.stream().map(Long::parseLong).collect(Collectors.toList()));

        List<CfdpTransaction> resumedTransfers = transfers.stream()
                .map(ResumeRequest::new)
                .map(ci::processRequest)
                .filter(x -> x != null)
                .collect(Collectors.toList());

        ResumedTransfersResponse.Builder rtr = ResumedTransfersResponse.newBuilder();

        for (CfdpTransaction transfer : resumedTransfers) {
            rtr.addTransfers(transfer.getTransactionId().getSequenceNumber());
        }
        completeOK(req, rtr.build());
    }
}
