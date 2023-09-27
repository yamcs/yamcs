package org.yamcs.tctm.pus.services.tm;

import java.io.IOException;

import org.yamcs.InitException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public abstract class BucketSaveHandler {
    public Bucket getBucket(String bucketName, String yamcsInstance) throws InitException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        try {
            Bucket bucket = ydb.getBucket(bucketName);
            if (bucket == null) {
                bucket = ydb.createBucket(bucketName);
            }
            return bucket;
        } catch (IOException e) {
            throw new InitException(e);
        }
    }
}
