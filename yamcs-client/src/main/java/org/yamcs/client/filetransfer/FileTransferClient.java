package org.yamcs.client.filetransfer;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.filetransfer.FileTransferClient.UploadOptions.CreatePathOption;
import org.yamcs.client.filetransfer.FileTransferClient.UploadOptions.OverwriteOption;
import org.yamcs.client.filetransfer.FileTransferClient.UploadOptions.ReliableOption;
import org.yamcs.client.filetransfer.FileTransferClient.UploadOptions.UploadOption;
import org.yamcs.client.storage.ObjectId;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.protobuf.CancelTransferRequest;
import org.yamcs.protobuf.CreateTransferRequest;
import org.yamcs.protobuf.FileTransferApiClient;
import org.yamcs.protobuf.GetTransferRequest;
import org.yamcs.protobuf.ListTransfersRequest;
import org.yamcs.protobuf.ListTransfersResponse;
import org.yamcs.protobuf.PauseTransferRequest;
import org.yamcs.protobuf.ResumeTransferRequest;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferInfo;

import com.google.protobuf.Empty;

public class FileTransferClient {

    private String instance;
    private String serviceName;
    private FileTransferApiClient ftService;

    public FileTransferClient(YamcsClient baseClient, String instance, String serviceName) {
        this.instance = instance;
        this.serviceName = serviceName;
        ftService = new FileTransferApiClient(baseClient.getMethodHandler());
    }

    public String getInstance() {
        return instance;
    }

    /**
     * List the on-going file transfers
     * 
     * @return
     */
    public CompletableFuture<List<TransferInfo>> listTransfers() {
        ListTransfersRequest.Builder requestb = ListTransfersRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName);
        CompletableFuture<ListTransfersResponse> f = new CompletableFuture<>();
        ftService.listTransfers(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getTransfersList());
    }

    public CompletableFuture<TransferInfo> getTransfer(long id) {
        GetTransferRequest.Builder requestb = GetTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setId(id);
        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        ftService.getTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TransferInfo> upload(ObjectId source, UploadOption... options) {
        return upload(source, source.getObjectName(), options);
    }

    /**
     * Initiate file upload
     */
    public CompletableFuture<TransferInfo> upload(ObjectId source, String remotePath, UploadOption... options) {
        CreateTransferRequest.Builder requestb = CreateTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setBucket(source.getBucket())
                .setObjectName(source.getObjectName())
                .setRemotePath(remotePath)
                .setDirection(TransferDirection.UPLOAD);

        if (options.length > 0) {
            var optionsMap = new HashMap<String, Object>();
            for (var option : options) {
                if (option instanceof ReliableOption) {
                    optionsMap.put("reliable", ((ReliableOption) option).reliable);
                } else if (option instanceof OverwriteOption) {
                    optionsMap.put("overwrite", ((OverwriteOption) option).overwrite);
                } else if (option instanceof CreatePathOption) {
                    optionsMap.put("createPath", ((CreatePathOption) option).createPath);
                } else {
                    throw new IllegalArgumentException("Unsupported option " + option.getClass());
                }
            }
            requestb.setOptions(WellKnownTypes.toStruct(optionsMap));
        }

        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        ftService.createTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    /**
     * Initiate file download
     */
    public CompletableFuture<TransferInfo> download(String remotePath, ObjectId target) {
        CreateTransferRequest.Builder requestb = CreateTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setRemotePath(remotePath)
                .setDirection(TransferDirection.DOWNLOAD)
                .setBucket(target.getBucket())
                .setObjectName(target.getObjectName());
        CompletableFuture<TransferInfo> f = new CompletableFuture<>();
        ftService.createTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    /**
     * Pause an on-going file transfer
     * 
     */
    public CompletableFuture<Void> pause(long id) {
        PauseTransferRequest.Builder requestb = PauseTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setId(id);
        CompletableFuture<Empty> f = new CompletableFuture<>();
        ftService.pauseTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    /**
     * Resume an on-going file transfer
     */
    public CompletableFuture<Void> resume(long id) {
        ResumeTransferRequest.Builder requestb = ResumeTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setId(id);
        CompletableFuture<Empty> f = new CompletableFuture<>();
        ftService.resumeTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    /**
     * Cancel an on-going file transfer
     */
    public CompletableFuture<Void> cancel(long id) {
        CancelTransferRequest.Builder requestb = CancelTransferRequest.newBuilder()
                .setInstance(instance)
                .setServiceName(serviceName)
                .setId(id);
        CompletableFuture<Empty> f = new CompletableFuture<>();
        ftService.cancelTransfer(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(response -> null);
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
