package org.yamcs.filetransfer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.DataFile;
import org.yamcs.cfdp.FileDownloadRequests;
import org.yamcs.logging.Log;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class FileSaveHandler {

    private final Log log;
    private final Bucket defaultBucket;
    private FileDownloadRequests fileDownloadRequests;
    private final boolean allowRemoteProvidedBucket;
    private final boolean allowRemoteProvidedSubdirectory;
    private final boolean allowDownloadOverwrites;
    private final int maxExistingFileRenames;
    private Bucket bucket;
    private String objectName;

    public FileSaveHandler(String yamcsInstance, Bucket defaultBucket, FileDownloadRequests fileDownloadRequests,
            boolean allowRemoteProvidedBucket, boolean allowRemoteProvidedSubdirectory, boolean allowDownloadOverwrites,
            int maxExistingFileRenames) {
        this.log = new Log(this.getClass(), yamcsInstance);
        this.defaultBucket = defaultBucket;
        this.fileDownloadRequests = fileDownloadRequests;
        this.allowRemoteProvidedBucket = allowRemoteProvidedBucket;
        this.allowRemoteProvidedSubdirectory = allowRemoteProvidedSubdirectory;
        this.allowDownloadOverwrites = allowDownloadOverwrites;
        this.maxExistingFileRenames = maxExistingFileRenames;
    }

    public FileSaveHandler(String yamcsInstance, Bucket defaultBucket) {
        this(yamcsInstance, defaultBucket, null, false, false, false, 1000);
    }

    public void saveFile(String objectName, DataFile file, Map<String, String> metadata,
            CfdpTransactionId originatingTransactionId)
            throws FileAlreadyExistsException {
        setObjectName(objectName);
        saveFile(file, metadata, originatingTransactionId);
    }

    public void saveFile(DataFile file, Map<String, String> metadata, CfdpTransactionId originatingTransactionId) {
        if (objectName == null) {
            log.warn("File name not set, not saving");
            return;
        }
        if (bucket == null) {
            bucket = defaultBucket;
        }

        try {
            bucket.putObject(this.objectName, null, metadata, file.getData());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save incoming file in bucket: " + objectName
                    + (bucket != null ? " -> " + bucket.getName() : ""), e);
        }
    }

    private String parseObjectName(String name) throws IOException {
        if (bucket == null) {
            bucket = defaultBucket;

            if (allowRemoteProvidedBucket) {
                String[] split = name.split(":", 2);
                if (split.length == 2) {
                    YarchDatabaseInstance ydb = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);

                    Bucket customBucket = ydb.getBucket(split[0]);
                    if (customBucket != null) {
                        this.bucket = customBucket;
                        name = split[1];
                    }
                }
            }
        }

        if (!allowRemoteProvidedSubdirectory) {
            name = name.replaceAll("[/\\\\]", "_");
        } else {
            // Removing leading slashes, spaces and dots (permitting ".filename")
            name = name.replaceAll("^(?![.]\\w)[./\\\\ ]+", "");
            // Removing directory traversal characters
            name = name.replaceAll("[.]{2,}[/\\\\]", "");
        }

        name = name.strip();

        if (allowDownloadOverwrites || bucket.findObject(name) == null) {
            return name;
        }

        for (int i = 1; i < maxExistingFileRenames; i++) {
            String namei = name + "(" + i + ")";
            if (bucket.findObject(namei) == null) {
                return namei;
            }
        }

        throw new FileAlreadyExistsException(
                "CANCELLED: \"" + name + "\" already exists in bucket \"" + bucket.getName() + "\"");
    }

    public void setObjectName(String objectName) throws FileAlreadyExistsException {
        if (objectName == null) {
            return;
        }

        try {
            this.objectName = parseObjectName(objectName);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save incoming file in bucket: " + objectName
                    + (bucket != null ? " -> " + bucket.getName() : ""), e);
        }
    }

    public String getBucketName() {
        return bucket != null ? bucket.getName() : null;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setBucket(Bucket bucket) {
        this.bucket = bucket;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public void processOriginatingTransactionId(CfdpTransactionId originatingTransactionId) throws IOException {
        String bucketName = fileDownloadRequests.getBuckets().get(originatingTransactionId);
        fileDownloadRequests.removeTransfer(originatingTransactionId);
        if (bucketName != null) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE); // Instance buckets?
            try {
                bucket = ydb.getBucket(bucketName);
            } catch (IOException e) {
                throw new IOException("Recognised originating transaction id " + originatingTransactionId
                        + " from incoming transfer but bucket does not exist");
            }
        }
    }
}
