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
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.CfdpTransfer;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.cfdp.TransferMonitor;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractCfdpApi;
import org.yamcs.protobuf.CancelTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListTransfersRequest;
import org.yamcs.protobuf.ListTransfersResponse;
import org.yamcs.protobuf.PauseTransferRequest;
import org.yamcs.protobuf.ResumeTransferRequest;
import org.yamcs.protobuf.SubscribeTransfersRequest;
import org.yamcs.protobuf.TransactionId;
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
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);

        List<CfdpTransfer> transfers = new ArrayList<>(cfdpService.getCfdpTransfers());
        Collections.sort(transfers, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));

        ListTransfersResponse.Builder responseb = ListTransfersResponse.newBuilder();
        for (CfdpTransfer transfer : transfers) {
            responseb.addTransfers(toTransferInfo(transfer));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTransfer(Context ctx, GetTransferRequest request, Observer<TransferInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
        observer.complete(toTransferInfo(transaction));
    }

    @Override
    public void createTransfer(Context ctx, CreateTransferRequest request, Observer<TransferInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
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
    public void pauseTransfer(Context ctx, PauseTransferRequest request, Observer<Empty> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
        if (transaction.pausable()) {
            cfdpService.processRequest(new PauseRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be paused");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void cancelTransfer(Context ctx, CancelTransferRequest request, Observer<Empty> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
        if (transaction.cancellable()) {
            cfdpService.processRequest(new CancelRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be cancelled");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void resumeTransfer(Context ctx, ResumeTransferRequest request, Observer<Empty> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = verifyTransaction(instance, request.getId());
        if (transaction.pausable()) {
            cfdpService.processRequest(new ResumeRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be resumed");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeTransfers(Context ctx, SubscribeTransfersRequest request, Observer<TransferInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        CfdpService cfdpService = verifyCfdpService(instance);
        TransferMonitor listener = transfer -> {
            observer.next(toTransferInfo(transfer));
        };
        observer.setCancelHandler(() -> cfdpService.removeTransferListener(listener));

        for (CfdpTransfer transfer : cfdpService.getCfdpTransfers()) {
            observer.next(toTransferInfo(transfer));
        }
        cfdpService.addTransferListener(listener);
    }

    private CfdpTransfer verifyTransaction(String instance, long id) throws NotFoundException {
        CfdpService cfdpService = verifyCfdpService(instance);
        CfdpTransfer transaction = cfdpService.getCfdpTransfer(id);
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
                .setId(transaction.getId())
                .setStartTime(startTime)
                .setState(transaction.getTransferState())
                .setBucket(transaction.getBucket().getName())
                .setObjectName(transaction.getObjectName())
                .setRemotePath(transaction.getRemotePath())
                .setDirection(transaction.getDirection())
                .setTotalSize(transaction.getTotalSize())
                .setSizeTransferred(transaction.getTransferredSize())
                .setReliable(transaction.isReliable())
                .setTransactionId(toTransactionId(id));
        String failureReason = transaction.getFailuredReason();
        if (failureReason != null) {
            tib.setFailureReason(failureReason);
        }

        return tib.build();
    }

    private static TransactionId toTransactionId(CfdpTransactionId id) {
        return TransactionId.newBuilder().setInitiatorEntity(id.getInitiatorEntity())
                .setSequenceNumber(id.getSequenceNumber()).build();
    }

    private CfdpService verifyCfdpService(String instance) throws NotFoundException {
        List<CfdpService> services = YamcsServer.getServer().getInstance(instance).getServices(CfdpService.class);
        if (services.isEmpty()) {
            throw new NotFoundException();
        }
        return services.get(0);
    }
}
