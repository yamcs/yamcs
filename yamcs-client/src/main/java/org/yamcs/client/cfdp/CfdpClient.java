package org.yamcs.client.cfdp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.cfdp.CfdpClient.UploadOptions.CreatePathOption;
import org.yamcs.client.cfdp.CfdpClient.UploadOptions.OverwriteOption;
import org.yamcs.client.cfdp.CfdpClient.UploadOptions.ReliableOption;
import org.yamcs.client.cfdp.CfdpClient.UploadOptions.UploadOption;
import org.yamcs.client.storage.ObjectId;
import org.yamcs.protobuf.CfdpApiClient;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListTransfersRequest;
import org.yamcs.protobuf.ListTransfersResponse;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferInfo;

public class CfdpClient {

    private String instance;
    private CfdpApiClient cfdpService;

    public CfdpClient(YamcsClient baseClient, String instance) {
        this.instance = instance;
        cfdpService = new CfdpApiClient(baseClient.getMethodHandler());
    }

    public String getInstance() {
        return instance;
    }

    public CompletableFuture<List<TransferInfo>> listTransfers() {
        ListTransfersRequest.Builder requestb = ListTransfersRequest.newBuilder()
                .setInstance(instance);
        CompletableFuture<ListTransfersResponse> f = new CompletableFuture<>();
        cfdpService.listTransfers(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getTransfersList());
    }

    public CompletableFuture<TransferInfo> getTransfer(long id) {
        GetTransferRequest.Builder requestb = GetTransferRequest.newBuilder()
                .setInstance(instance)
                .setId(id);
        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        cfdpService.getTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TransferInfo> upload(ObjectId source, UploadOption... options) {
        CreateTransferRequest.Builder requestb = CreateTransferRequest.newBuilder()
                .setInstance(instance)
                .setBucket(source.getBucket())
                .setObjectName(source.getObjectName())
                .setDirection(TransferDirection.UPLOAD);
        CreateTransferRequest.UploadOptions.Builder optionsb = requestb.getUploadOptionsBuilder();
        for (UploadOption option : options) {
            if (option instanceof ReliableOption) {
                optionsb.setReliable(((ReliableOption) option).reliable);
            } else if (option instanceof OverwriteOption) {
                optionsb.setOverwrite(((OverwriteOption) option).overwrite);
            } else if (option instanceof CreatePathOption) {
                optionsb.setCreatePath(((CreatePathOption) option).createPath);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        requestb.setUploadOptions(optionsb);
        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        cfdpService.createTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TransferInfo> download(String remotePath, ObjectId target) {
        CreateTransferRequest.Builder requestb = CreateTransferRequest.newBuilder()
                .setInstance(instance)
                .setRemotePath(remotePath)
                .setDirection(TransferDirection.DOWNLOAD)
                .setBucket(target.getBucket())
                .setObjectName(target.getObjectName());
        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        cfdpService.createTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public static final class UploadOptions {

        public static interface UploadOption {
        }

        public static UploadOption reliable(boolean reliable) {
            return new ReliableOption(reliable);
        }

        public static UploadOption overwrite(boolean overwrite) {
            return new OverwriteOption(overwrite);
        }

        public static UploadOption createPath(boolean createPath) {
            return new CreatePathOption(createPath);
        }

        static final class ReliableOption implements UploadOption {
            final boolean reliable;

            public ReliableOption(boolean reliable) {
                this.reliable = reliable;
            }
        }

        static final class OverwriteOption implements UploadOption {
            final boolean overwrite;

            public OverwriteOption(boolean overwrite) {
                this.overwrite = overwrite;
            }
        }

        static final class CreatePathOption implements UploadOption {
            final boolean createPath;

            public CreatePathOption(boolean createPath) {
                this.createPath = createPath;
            }
        }
    }
}
