package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.CancelRequest;
import org.yamcs.cfdp.CfdpOutgoingTransfer;
import org.yamcs.cfdp.CfdpService;
import org.yamcs.cfdp.CfdpTransaction;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.EditTransferRequest;
import org.yamcs.protobuf.ListTransfersResponse;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
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

    @Route(rpc = "yamcs.protobuf.cfdp.CFDP.ListTransfers")
    public void listTransfers(RestRequest req) throws HttpException {
        CfdpService cfdpService = verifyCfdpService(req);

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

    @Route(rpc = "yamcs.protobuf.cfdp.CFDP.GetTransfer")
    public void getTransfer(RestRequest req) throws HttpException {
        long transactionId = req.getLongRouteParam("id");
        CfdpTransaction transaction = verifyTransaction(req, transactionId);
        completeOK(req, toTransferInfo(transaction));
    }

    @Route(rpc = "yamcs.protobuf.cfdp.CFDP.CreateTransfer")
    public void createTransfer(RestRequest req) throws HttpException {
        CfdpService cfdpService = verifyCfdpService(req);

        CreateTransferRequest request = req.bodyAsMessage(CreateTransferRequest.newBuilder()).build();
        if (!request.hasDirection()) {
            throw new BadRequestException("Direction not specified");
        }

        byte[] objData;

        String bucketName = request.getBucket();
        BucketHelper.checkReadBucketPrivilege(bucketName, req.getUser());

        String objectName = request.getObjectName();

        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);

        Bucket bucket;
        try {
            bucket = yarch.getBucket(bucketName);
        } catch (IOException e) {
            throw new InternalServerErrorException("Error while resolving bucket", e);
        }
        if (bucket == null) {
            throw new BadRequestException("No bucket by name '" + bucketName + "'");
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

    @Route(rpc = "yamcs.protobuf.cfdp.CFDP.UpdateTransfer")
    public void editTransfer(RestRequest req) throws HttpException {
        CfdpService cfdpService = verifyCfdpService(req);

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
        completeOK(req);
    }

    private CfdpTransaction verifyTransaction(RestRequest req, long transactionId) throws NotFoundException {
        CfdpService cfdpService = verifyCfdpService(req);
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
                .setReliable(transaction.isReliable())
                .build();
    }

    private CfdpService verifyCfdpService(RestRequest req) throws NotFoundException {
        String instance = req.getRouteParam("instance");
        verifyInstance(req, instance);

        List<CfdpService> services = yamcsServer.getInstance(instance).getServices(CfdpService.class);
        if (services.isEmpty()) {
            throw new NotFoundException(req);
        }
        return services.get(0);
    }
}
