package org.yamcs.filetransfer;

import org.yamcs.cfdp.DataFile;
import org.yamcs.logging.Log;
import org.yamcs.yarch.Bucket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

public class FileSaveHandler {

    private final Log log;
    private final Bucket defaultBucket;
    private Bucket bucket;
    private String objectName;

    public FileSaveHandler(String yamcsInstance, Bucket defaultBucket) {
        this.log = new Log(this.getClass(), yamcsInstance);
        this.defaultBucket = defaultBucket;
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

        System.out.println(name);
        name = name.replace("/", "_");
        System.out.println(name);
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
