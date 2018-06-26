package org.yamcs.parameterarchive;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsService;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

/**
 * Wrapper around the old ParameterArchive or the new one that looks if the &lt;instance-data-dir&gt;/ParameterArchive
 * exists and if it does it starts the old ParameterArchive (with a big deprecation warning), if it doesn't it starts
 * the new one.
 */
public class ParameterArchive extends AbstractService implements YamcsService {
    org.yamcs.oldparchive.ParameterArchive oldparchive;
    static Logger log = LoggerFactory.getLogger(ParameterArchive.class.getName());

    Service parchive;

    public ParameterArchive(String instance, Map<String, Object> args) throws IOException, RocksDBException {
        if (startOld(instance)) {
            parchive = new org.yamcs.oldparchive.ParameterArchive(instance, args);
        } else {
            parchive = new ParameterArchiveV2(instance, args);
        }
    }

    public ParameterArchive(String instance) throws IOException, RocksDBException {
        if (startOld(instance)) {
            parchive = new org.yamcs.oldparchive.ParameterArchive(instance);
        } else {
            parchive = new ParameterArchiveV2(instance);
        }
    }

    @Override
    protected void doStart() {
        parchive.startAsync();
        parchive.awaitRunning();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        parchive.stopAsync();
        parchive.awaitTerminated();
        notifyStopped();
    }

    boolean startOld(String instance) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        File f = new File(ydb.getRoot() + "/ParameterArchive");

        boolean b = f.exists();
        if (b) {
            log.warn(
                    "You are using the old rocksdb storage engine for the Parameter Archive. This is deprecated and it will be removed from future versions. "
                            + "Please upgrade using \"yamcs archive upgrade --instance " + instance + "\" command");
        }
        return b;
    }

    public Service getParchive() {
        return parchive;
    }
}
