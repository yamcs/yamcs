package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.cfdp.CancelRequest;
import org.yamcs.cfdp.CfdpService;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.CfdpTransfer;
import org.yamcs.cfdp.OngoingCfdpTransfer;
import org.yamcs.cfdp.PauseRequest;
import org.yamcs.cfdp.PutRequest;
import org.yamcs.cfdp.ResumeRequest;
import org.yamcs.cfdp.TransferMonitor;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractCfdpApi;
import org.yamcs.protobuf.CFDPServiceInfo;
import org.yamcs.protobuf.CancelTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest.UploadOptions;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListCFDPServicesRequest;
import org.yamcs.protobuf.ListCFDPServicesResponse;
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
    public void listCFDPServices(Context ctx, ListCFDPServicesRequest request,
            Observer<ListCFDPServicesResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YamcsServer yamcs = YamcsServer.getServer();
        ListCFDPServicesResponse.Builder responseb = ListCFDPServicesResponse.newBuilder();
        YamcsServerInstance ysi = yamcs.getInstance(instance);
        for (ServiceWithConfig service : ysi.getServicesWithConfig(CfdpService.class)) {
            responseb.addServices(toCFDPServiceInfo(service.getName(), (CfdpService) service.getService()));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listTransfers(Context ctx, ListTransfersRequest request,
            Observer<ListTransfersResponse> observer) {

        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

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
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        CfdpTransfer transaction = verifyTransaction(cfdpService, request.getId());
        observer.complete(toTransferInfo(transaction));
    }

    @Override
    public void createTransfer(Context ctx, CreateTransferRequest request, Observer<TransferInfo> observer) {
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

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
            boolean closureRequested = false;
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
                if (opts.hasClosureRequested()) {
                    closureRequested = opts.getClosureRequested();
                }
            }

            if (reliable && closureRequested) {
                throw new BadRequestException("Cannot set both reliable and closureRequested options");
            }
            String target = request.getRemotePath();

            long sourceId, destinationId;
            if (request.hasSource()) {
                Long l = cfdpService.getLocalEntities().get(request.getSource());
                if (l == null) {
                    throw new BadRequestException("Invalid source '" + request.getSource());
                }
                sourceId = l;
            } else {
                Long l = cfdpService.getLocalEntities().get(CfdpService.DEFAULT_SRCDST);
                if (l == null) {
                    throw new BadRequestException("No source specified and no default either.");
                }
                sourceId = l;
            }

            if (request.hasDestination()) {
                Long l = cfdpService.getRemoteEntities().get(request.getDestination());
                if (l == null) {
                    throw new BadRequestException("Invalid destination '" + request.getDestination());
                }
                destinationId = l;
            } else {
                Long l = cfdpService.getRemoteEntities().get(CfdpService.DEFAULT_SRCDST);
                if (l == null) {
                    throw new BadRequestException("No destination specified and no default either.");
                }
                destinationId = l;
            }

            PutRequest req = new PutRequest(sourceId, destinationId, objectName, target, overwrite, reliable,
                    closureRequested, createPath, bucket, objData);
            OngoingCfdpTransfer transfer = cfdpService.processRequest(req);
            observer.complete(toTransferInfo(transfer));
        } else if (request.getDirection() == TransferDirection.DOWNLOAD) {
            throw new BadRequestException("Download not yet implemented");
        } else {
            throw new BadRequestException("Unexpected direction '" + request.getDirection() + "'");
        }
    }

    @Override
    public void pauseTransfer(Context ctx, PauseTransferRequest request, Observer<Empty> observer) {
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        CfdpTransfer transaction = verifyTransaction(cfdpService, request.getId());
        if (transaction.pausable()) {
            cfdpService.processRequest(new PauseRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be paused");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void cancelTransfer(Context ctx, CancelTransferRequest request, Observer<Empty> observer) {
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);

        CfdpTransfer transaction = verifyTransaction(cfdpService, request.getId());
        if (transaction.cancellable()) {
            cfdpService.processRequest(new CancelRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be cancelled");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void resumeTransfer(Context ctx, ResumeTransferRequest request, Observer<Empty> observer) {
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        CfdpTransfer transaction = verifyTransaction(cfdpService, request.getId());

        if (transaction.pausable()) {
            cfdpService.processRequest(new ResumeRequest(transaction));
        } else {
            throw new BadRequestException("Transaction '" + transaction.getId() + "' cannot be resumed");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeTransfers(Context ctx, SubscribeTransfersRequest request, Observer<TransferInfo> observer) {
        CfdpService cfdpService = verifyCfdpService(request.getInstance(),
                request.hasServiceName() ? request.getServiceName() : null);
        TransferMonitor listener = transfer -> {
            observer.next(toTransferInfo(transfer));
        };
        observer.setCancelHandler(() -> cfdpService.removeTransferListener(listener));

        for (CfdpTransfer transfer : cfdpService.getCfdpTransfers()) {
            observer.next(toTransferInfo(transfer));
        }
        cfdpService.addTransferListener(listener);
    }

    private static CFDPServiceInfo toCFDPServiceInfo(String name, CfdpService service) {
        CFDPServiceInfo.Builder infob = CFDPServiceInfo.newBuilder()
                .setInstance(service.getYamcsInstance())
                .setName(name);
        for (Map.Entry<String, Long> me : service.getLocalEntities().entrySet()) {
            infob.addLocalEntities(EntityInfo.newBuilder().setId(me.getValue()).setName(me.getKey()).build());
        }
        for (Map.Entry<String, Long> me : service.getRemoteEntities().entrySet()) {
            infob.addRemoteEntities(EntityInfo.newBuilder().setId(me.getValue()).setName(me.getKey()).build());
        }
        return infob.build();
    }

    private CfdpTransfer verifyTransaction(CfdpService cfdpService, long id) throws NotFoundException {
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
                .setBucket(transaction.getBucketName())
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

    private CfdpService verifyCfdpService(String yamcsInstance, String serviceName) throws NotFoundException {
        String instance = ManagementApi.verifyInstance(yamcsInstance);
        CfdpService cfdpServ = null;
        if (serviceName != null) {
            cfdpServ = YamcsServer.getServer().getInstance(instance)
                    .getService(CfdpService.class, serviceName);
        } else {
            List<CfdpService> cl = YamcsServer.getServer().getInstance(instance)
                    .getServices(CfdpService.class);
            if (cl.size() > 0) {
                cfdpServ = cl.get(0);
            }
        }
        if (cfdpServ == null) {
            if (serviceName == null) {
                throw new NotFoundException("No CFDP service found");
            } else {
                throw new NotFoundException("CFDP service '" + serviceName + "' not found");
            }
        }
        return cfdpServ;
    }

}
