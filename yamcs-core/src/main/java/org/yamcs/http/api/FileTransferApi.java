package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.cfdp.CfdpFileTransfer;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.filetransfer.TransferOptions;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractFileTransferApi;
import org.yamcs.protobuf.CancelTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.FileTransferServiceInfo;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListFileTransferServicesRequest;
import org.yamcs.protobuf.ListFileTransferServicesResponse;
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

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class FileTransferApi extends AbstractFileTransferApi<Context> {

    private static final Logger log = LoggerFactory.getLogger(FileTransferApi.class);

    @Override
    public void listFileTransferServices(Context ctx, ListFileTransferServicesRequest request,
            Observer<ListFileTransferServicesResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YamcsServer yamcs = YamcsServer.getServer();
        ListFileTransferServicesResponse.Builder responseb = ListFileTransferServicesResponse.newBuilder();
        YamcsServerInstance ysi = yamcs.getInstance(instance);
        for (ServiceWithConfig service : ysi.getServicesWithConfig(FileTransferService.class)) {
            responseb.addServices(
                    toFileTransferServiceInfo(service.getName(), (FileTransferService) service.getService()));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listTransfers(Context ctx, ListTransfersRequest request,
            Observer<ListTransfersResponse> observer) {

        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        List<FileTransfer> transfers = new ArrayList<>(ftService.getTransfers());
        Collections.sort(transfers, (a, b) -> Long.compare(a.getStartTime(), b.getStartTime()));

        ListTransfersResponse.Builder responseb = ListTransfersResponse.newBuilder();
        for (FileTransfer transfer : transfers) {
            responseb.addTransfers(toTransferInfo(transfer));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTransfer(Context ctx, GetTransferRequest request, Observer<TransferInfo> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        FileTransfer transaction = verifyTransaction(ftService, request.getId());
        observer.complete(toTransferInfo(transaction));
    }

    @Override
    public void createTransfer(Context ctx, CreateTransferRequest request, Observer<TransferInfo> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        if (!request.hasDirection()) {
            throw new BadRequestException("Direction not specified");
        }

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

        if (request.getDirection() == TransferDirection.UPLOAD) {
            TransferOptions transferOptions = new TransferOptions();

            transferOptions.setOverwrite(true);
            transferOptions.setCreatePath(true);
            if (request.hasUploadOptions()) {
                UploadOptions opts = request.getUploadOptions();
                if (opts.hasOverwrite()) {
                    transferOptions.setOverwrite(opts.getOverwrite());
                }
                if (opts.hasCreatePath()) {
                    transferOptions.setCreatePath(opts.getCreatePath());
                }
                if (opts.hasReliable()) {
                    transferOptions.setReliable(opts.getReliable());
                }
                if (opts.hasClosureRequested()) {
                    transferOptions.setClosureRequested(opts.getClosureRequested());
                }
            }

            if (transferOptions.isReliable() && transferOptions.isClosureRequested()) {
                throw new BadRequestException("Cannot set both reliable and closureRequested options");
            }
            String destinationPath = request.getRemotePath();
            String source = request.hasSource() ? request.getSource() : null;
            String destination = request.hasDestination() ? request.getDestination() : null;

            try {
                FileTransfer transfer = ftService.startUpload(source, bucket, objectName, destination,
                        destinationPath, transferOptions);
                observer.complete(toTransferInfo(transfer));

            } catch (InvalidRequestException e) {
                throw new BadRequestException(e.getMessage());
            } catch (IOException e) {
                log.error("Error when retrieving object {} from bucket {}", objectName, bucketName, e);
                throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
            }
        } else if (request.getDirection() == TransferDirection.DOWNLOAD) {
            TransferOptions transferOptions = new TransferOptions();
            transferOptions.setOverwrite(true);
            transferOptions.setCreatePath(true);

            String sourcePath = request.getRemotePath();
            String source = request.hasSource() ? request.getSource() : null;
            String destination = request.hasDestination() ? request.getDestination() : null;

            try {
                FileTransfer transfer = ftService.startDownload(source, sourcePath, destination, bucket, objectName,
                        transferOptions);
                observer.complete(toTransferInfo(transfer));
            } catch (InvalidRequestException e) {
                throw new BadRequestException(e.getMessage());
            } catch (IOException e) {
                log.error("Error when retrieving object {} from bucket {}", objectName, bucketName, e);
                throw new InternalServerErrorException("Error when retrieving object: " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Unexpected direction '" + request.getDirection() + "'");
        }
    }

    @Override
    public void pauseTransfer(Context ctx, PauseTransferRequest request, Observer<Empty> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        FileTransfer transaction = verifyTransaction(ftService, request.getId());
        if (transaction.pausable()) {
            ftService.pause(transaction);
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be paused");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void cancelTransfer(Context ctx, CancelTransferRequest request, Observer<Empty> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        FileTransfer transaction = verifyTransaction(ftService, request.getId());
        if (transaction.cancellable()) {
            ftService.cancel(transaction);
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be cancelled");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void resumeTransfer(Context ctx, ResumeTransferRequest request, Observer<Empty> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        FileTransfer transaction = verifyTransaction(ftService, request.getId());

        if (transaction.pausable()) {
            ftService.resume(transaction);
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be resumed");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeTransfers(Context ctx, SubscribeTransfersRequest request, Observer<TransferInfo> observer) {
        FileTransferService ftService = verifyService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        TransferMonitor listener = transfer -> {
            observer.next(toTransferInfo(transfer));
        };
        observer.setCancelHandler(() -> ftService.unregisterTransferMonitor(listener));

        for (FileTransfer transfer : ftService.getTransfers()) {
            observer.next(toTransferInfo(transfer));
        }
        ftService.registerTransferMonitor(listener);
    }

    private static FileTransferServiceInfo toFileTransferServiceInfo(String name, FileTransferService service) {
        FileTransferServiceInfo.Builder infob = FileTransferServiceInfo.newBuilder()
                .setInstance(service.getYamcsInstance())
                .setName(name);
        infob.addAllLocalEntities(service.getLocalEntities());
        infob.addAllRemoteEntities(service.getRemoteEntities());
        return infob.build();
    }

    private FileTransfer verifyTransaction(FileTransferService ftService, long id) throws NotFoundException {
        FileTransfer transaction = ftService.getFileTransfer(id);
        if (transaction == null) {
            throw new NotFoundException("No such transaction");
        } else {
            return transaction;
        }
    }

    private static TransferInfo toTransferInfo(FileTransfer transfer) {
        Timestamp startTime = TimeEncoding.toProtobufTimestamp(transfer.getStartTime());
        TransferInfo.Builder tib = TransferInfo.newBuilder()
                .setId(transfer.getId())
                .setStartTime(startTime)
                .setState(transfer.getTransferState())
                .setBucket(transfer.getBucketName())
                .setObjectName(transfer.getObjectName())
                .setRemotePath(transfer.getRemotePath())
                .setDirection(transfer.getDirection())
                .setTotalSize(transfer.getTotalSize())
                .setSizeTransferred(transfer.getTransferredSize())
                .setReliable(transfer.isReliable());
        if (transfer instanceof CfdpFileTransfer) {
            CfdpTransactionId txid = ((CfdpFileTransfer) transfer).getTransactionId();
            tib.setTransactionId(toTransactionId(txid));
        }
        String failureReason = transfer.getFailuredReason();
        if (failureReason != null) {
            tib.setFailureReason(failureReason);
        }

        return tib.build();
    }

    private static TransactionId toTransactionId(CfdpTransactionId id) {
        return TransactionId.newBuilder().setInitiatorEntity(id.getInitiatorEntity())
                .setSequenceNumber(id.getSequenceNumber()).build();
    }

    private FileTransferService verifyService(String yamcsInstance, String serviceName) throws NotFoundException {
        String instance = ManagementApi.verifyInstance(yamcsInstance);
        FileTransferService ftServ = null;
        if (serviceName != null) {
            ftServ = YamcsServer.getServer().getInstance(instance)
                    .getService(FileTransferService.class, serviceName);
        } else {
            List<FileTransferService> cl = YamcsServer.getServer().getInstance(instance)
                    .getServices(FileTransferService.class);
            if (cl.size() > 0) {
                ftServ = cl.get(0);
            }
        }
        if (ftServ == null) {
            if (serviceName == null) {
                throw new NotFoundException("No file transfer service found");
            } else {
                throw new NotFoundException("File transfer service '" + serviceName + "' not found");
            }
        }
        return ftServ;
    }

}