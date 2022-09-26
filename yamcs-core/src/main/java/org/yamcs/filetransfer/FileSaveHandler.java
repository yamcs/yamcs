package org.yamcs.filetransfer;

import org.yamcs.YamcsServer;
import org.yamcs.cfdp.DataFile;
import org.yamcs.logging.Log;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

public class FileSaveHandler {

    private final Log log;
    private final Bucket defaultBucket;
    private final boolean allowRemoteProvidedBucket;
    private final boolean allowRemoteProvidedSubdirectory;
    private final String yamcsInstance;
    private Bucket bucket;
    private String objectName;

    public FileSaveHandler(String yamcsInstance, Bucket defaultBucket, boolean allowRemoteProvidedBucket,
            boolean allowRemoteProvidedSubdirectory) {
        this.yamcsInstance = yamcsInstance;
        this.log = new Log(this.getClass(), yamcsInstance);
        this.defaultBucket = defaultBucket;
        this.allowRemoteProvidedBucket = allowRemoteProvidedBucket;
        this.allowRemoteProvidedSubdirectory = allowRemoteProvidedSubdirectory;
    }

    public FileSaveHandler(String yamcsInstance, Bucket defaultBucket) {
        this(yamcsInstance, defaultBucket, false, false);
    }

    public void saveFile(String objectName, DataFile file, Map<String, String> metadata) {
        setObjectName(objectName);
        saveFile(file, metadata);
    }

    public void saveFile(DataFile file, Map<String, String> metadata) {
        if(bucket == null) { bucket = defaultBucket; }

        try {
            bucket.putObject(this.objectName, null, metadata, file.getData());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot save incoming file in bucket " + bucket.getName(), e);
        }
    }

    private String parseObjectName(String name) throws IOException {
        /**
         *@todo This assumes that filesystem separator is "/". Shouldn't this be configurable?
         */
        bucket = defaultBucket;

        if(allowRemoteProvidedBucket) {
            String[] split = name.split(":", 2);
            if(split.length == 2) {
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE); // Instance buckets?

                System.out.println(ydb.listBuckets());
                Bucket customBucket = ydb.getBucket(split[0]);
                System.out.println(customBucket);
                if(customBucket != null) {
                    System.out.println(customBucket.getName());
                    this.bucket = customBucket;
                    name = split[1];
                }
            }
        }

        name = name.replace("/", "_");
        if (bucket.findObject(name) == null) {
            return name;
        }
        /**
         *@note Any chance we can make this "10000" configurable?
         */
        for (int i = 1; i < 10000; i++) {
            String namei = name + "(" + i + ")";
            if (bucket.findObject(namei) == null) {
                return namei;
            }
        }
        log.warn("Cannot find a new name for {}, overwirting object", name);
        return name;
    }

    public void setObjectName(String objectName) {
        try {
            this.objectName = parseObjectName(objectName);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot save incoming file in bucket " + bucket.getName(), e);
        }
    }

    public String getBucketName() {
        return bucket != null ? bucket.getName() : null;
    }

    public String getObjectName() {
        return objectName;
    }

}
