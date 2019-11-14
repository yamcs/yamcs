package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.cfdp.CancelRequest;
import org.yamcs.cfdp.CfdpOutgoingTransfer;
import org.yamcs.cfdp.CfdpService;
import org.yamcs.cfdp.CfdpTransfer;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractCfdpApi;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.EditTransferRequest;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListTransfersRequest;
import org.yamcs.protobuf.ListTransfersResponse;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class CfdpApi extends AbstractCfdpApi<Context> {

    private static final Logger log = LoggerFactory.getLogger(CfdpApi.class);

    @Override
    public void listTransfers(Context ctx, ListTransfersRequest request,
            Observer<ListTransfersResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);

        List<CfdpTransfer> transfers = new ArrayList<>(cfdpService.getCfdpTransfers(true));
        Collections.sort(transfers, (c1, c2) -> {
            return Long.compare(
                    c1.getStartTime(),
                    c2.getStartTime());
        });

        ListTransfersResponse.Builder responseb = ListTransfersResponse.newBuilder();
        for (CfdpTransfer transaction : transfers) {
            responseb.addTransfer(toTransferInfo(transaction));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTransfer(Context ctx, GetTransferRequest request, Observer<TransferInfo> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
        observer.complete(toTransferInfo(transaction));
    }

    @Override
    public void createTransfer(Context ctx, CreateTransferRequest request, Observer<TransferInfo> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);

        if (!request.hasDirection()) {
            throw new BadRequestException("Direction not specified");
        }

        byte[] objData;

        String bucketName = request.getBucket();
        BucketsApi.checkReadBucketPrivilege(bucketName, ctx.user);

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
                throw new NotFoundException();
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
            observer.complete(toTransferInfo(transfer));
        } else if (request.getDirection() == TransferDirection.DOWNLOAD) {
            throw new BadRequestException("Download not yet implemented");
        } else {
            throw new BadRequestException("Unexpected direction '" + request.getDirection() + "'");
        }
    }

    @Override
    public void updateTransfer(Context ctx, EditTransferRequest request, Observer<Empty> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
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
        observer.complete(Empty.getDefaultInstance());
    }

    private CfdpTransfer verifyTransaction(String instance, long transactionId) throws NotFoundException {
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = cfdpService.getCfdpTransfer(transactionId);
        if (transaction == null) {
            throw new NotFoundException("No such transaction");
        } else {
            return transaction;
        }
    }

    private static TransferInfo toTransferInfo(CfdpTransfer transaction) {
        CfdpTransactionId id = transaction.getTransactionId();
        Timestamp startTime = TimeEncoding.toProtobufTimestamp(transaction.getStartTime());
        TransferInfo.Builder tib = TransferInfo.newBuilder()
                .setTransactionId(id.getSequenceNumber())
                .setStartTime(startTime)
                .setState(transaction.getTransferState())
                .setBucket(transaction.getBucket().getName())
                .setObjectName(transaction.getObjectName())
                .setRemotePath(transaction.getRemotePath())
                .setDirection(transaction.getDirection())
                .setTotalSize(transaction.getTotalSize())
                .setSizeTransferred(transaction.getTransferredSize())
                .setReliable(transaction.isReliable());
        String failureReason = transaction.getFailuredReason();
        if (failureReason != null) {
            tib.setFailureReason(failureReason);
        }

        return tib.build();
    }

    private CfdpService verifyCfdpService(String instance) throws NotFoundException {
        List<CfdpService> services = YamcsServer.getServer().getInstance(instance).getServices(CfdpService.class);
        if (services.isEmpty()) {
            throw new NotFoundException();
        }
        return services.get(0);
    }
}
