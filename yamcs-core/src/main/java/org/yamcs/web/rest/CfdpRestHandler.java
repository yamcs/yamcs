package org.yamcs.web.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.CancelRequest;
import org.yamcs.cfdp.CfdpOutgoingTransfer;
import org.yamcs.cfdp.CfdpService;
import org.yamcs.cfdp.CfdpTransaction;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.protobuf.Cfdp.CreateTransferRequest;
import org.yamcs.protobuf.Cfdp.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.Cfdp.EditTransferRequest;
import org.yamcs.protobuf.Cfdp.ListRemoteFilesResponse;
import org.yamcs.protobuf.Cfdp.ListTransfersResponse;
import org.yamcs.protobuf.Cfdp.RemoteFile;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import com.google.protobuf.Timestamp;

/**
 * Implements the CFDP Protocol
 * 
 * @author ddw
 *
 */
public class CfdpRestHandler extends RestHandler {

    private static final Logger log = LoggerFactory.getLogger(CfdpRestHandler.class);

    final CfdpService cfdpService;

    public CfdpRestHandler(CfdpService cfdpService) {
        this.cfdpService = cfdpService;
    }

    @Route(path = "/api/cfdp/:instance/transfers", method = "GET")
    public void listTransfers(RestRequest req) throws HttpException {
        List<CfdpTransaction> transactions = new ArrayList<>(cfdpService.getCfdpTransfers(true));
        Collections.sort(transactions, (c1, c2) -> {
            return Long.compare(
                    c1.getTransactionId().getStartTime(),
                    c2.getTransactionId().getStartTime());
        });

        ListTransfersResponse.Builder responseb = ListTransfersResponse.newBuilder();
        for (CfdpTransaction transaction : transactions) {
            responseb.addTransfer(toTransferInfo(transaction));
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/cfdp/:instance/transfers/:id", method = "GET")
    public void getTransfer(RestRequest req) throws HttpException {
        long transactionId = req.getLongRouteParam("id");

        CfdpTransaction transaction = verifyTransaction(req, transactionId);
        completeOK(req, toTransferInfo(transaction));
    }

    @Route(path = "/api/cfdp/:instance/transfers", method = "POST")
    public void createTransfer(RestRequest req) throws HttpException {
        CreateTransferRequest request = req.bodyAsMessage(CreateTransferRequest.newBuilder()).build();
        if (!request.hasDirection()) {
            throw new BadRequestException("Direction not specified");
        }

        byte[] objData;

        String bucketName = request.getBucket();
        BucketHelper.checkReadBucketPrivilege(bucketName, req.getUser());

        String objectName = request.getObjectName();

        YarchDatabaseInstance ydi = YarchDatabase.getInstance(BucketRestHandler.GLOBAL_INSTANCE);
        BucketDatabase bdb;
        try {
            bdb = ydi.getBucketDatabase();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Bucket database not available");
        }

        Bucket bucket;
        try {
            bucket = bdb.getBucket(bucketName);
        } catch (IOException e) {
            throw new InternalServerErrorException("Error while resolving bucket", e);
        }

        try {
            ObjectProperties props = bucket.findObject(objectName);
            if (props == null) {
                throw new NotFoundException(req);
            }
            objData = bucket.getObject(objectName);
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {}", objectName, bucketName, e);
            throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
        }

        if (request.getDirection() == TransferDirection.UPLOAD) {
            boolean overwrite = true;
            boolean createPath = true;
            boolean reliable = false;
            if (request.hasUploadOptions()) {
                UploadOptions opts = request.getUploadOptions();
                if (opts.hasOverwrite()) {
                    overwrite = opts.getOverwrite();
                }
                if (opts.hasCreatePath()) {
                    createPath = opts.getCreatePath();
                }
                if (opts.hasReliable()) {
                    reliable = opts.getReliable();
                }
            }

            String target = request.getRemotePath();
            CfdpOutgoingTransfer transfer = cfdpService.upload(objectName, target, overwrite,
                    reliable, createPath, bucket, objData);
            completeOK(req, toTransferInfo(transfer));
        } else if (request.getDirection() == TransferDirection.DOWNLOAD) {
            throw new BadRequestException("Download not yet implemented");
        } else {
            throw new BadRequestException("Unexpected direction '" + request.getDirection() + "'");
        }
    }

    @Route(path = "/api/cfdp/:instance/transfers/:id", method = { "PATCH", "PUT", "POST" })
    public void editTransfer(RestRequest req) throws HttpException {
        EditTransferRequest request = req.bodyAsMessage(EditTransferRequest.newBuilder()).build();
        long transactionId = req.getLongRouteParam("id");
        CfdpTransaction transaction = verifyTransaction(req, transactionId);
        if (request.hasOperation()) {
            switch (request.getOperation()) {
            case "pause":
                if (transaction.pausable()) {
                    cfdpService.processRequest(new PauseRequest(transaction));
                } else {
                    throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be paused");
                }
                break;
            case "cancel":
                if (transaction.cancellable()) {
                    cfdpService.processRequest(new CancelRequest(transaction));
                } else {
                    throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be cancelled");
                }
                break;
            case "resume":
                if (transaction.pausable()) {
                    cfdpService.processRequest(new ResumeRequest(transaction));
                } else {
                    throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be resumed");
                }
            }
        }
    }

    // TODO @Route(path = "/api/cfdp/:instance/filestore", method = "GET")
    // TODO @Route(path = "/api/cfdp/:instance/filestore/path*", method = "GET")
    public void listRemoteFiles(RestRequest req) throws HttpException {
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

    private CfdpTransaction verifyTransaction(RestRequest req, long transactionId) throws NotFoundException {
        CfdpTransaction transaction = cfdpService.getCfdpTransfer(transactionId);
        if (transaction == null) {
            throw new NotFoundException(req, "No such transaction");
        } else {
            return transaction;
        }
    }

    private static TransferInfo toTransferInfo(CfdpTransaction transaction) {
        CfdpTransactionId id = transaction.getTransactionId();
        id.getStartTime();
        Timestamp startTime = TimeEncoding.toProtobufTimestamp(id.getStartTime());
        return TransferInfo.newBuilder()
                .setTransactionId(id.getSequenceNumber())
                .setStartTime(startTime)
                .setState(transaction.getTransferState())
                .setBucket(transaction.getBucket().getName())
                .setObjectName(transaction.getObjectName())
                .setRemotePath(transaction.getRemotePath())
                .setDirection(transaction.getDirection())
                .setTotalSize(transaction.getTotalSize())
                .setSizeTransferred(transaction.getTransferredSize())
                .build();
    }
}
