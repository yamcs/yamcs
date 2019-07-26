package org.yamcs.archive;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.AbstractYamcsService;
import org.yamcs.api.InitException;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Handles index retrievals and tags
 *
 */
public class IndexServer extends AbstractYamcsService {

    TmIndex tmIndexer;

    ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

    TagDb tagDb;
    boolean readonly = false;

    // Maps instance names to archive directories
    final HashSet<String> instances = new HashSet<>();

    @Override
    public void init(String instanceName, YConfiguration args) throws InitException {
        super.init(instanceName, args);

        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YConfiguration instanceConfig = instance.getConfig();

        try {
            if (args.containsKey("tmIndexer")) {
                String icn = args.getString("tmIndexer");
                tmIndexer = loadIndexerFromClass(icn, instanceName, readonly);
            } else if (instanceConfig.containsKey("tmIndexer")) {
                String icn = instanceConfig.getString("tmIndexer");
                tmIndexer = loadIndexerFromClass(icn, instanceName, readonly);
            } else {
                tmIndexer = new CcsdsTmIndex(instanceName, readonly);
            }
        } catch (IOException e) {
            throw new InitException(e);
        }

        try {
            tagDb = YarchDatabase.getInstance(instanceName).getTagDb();
        } catch (YarchException e) {
            throw new InitException(e);
        }

        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    protected void doStart() {
        for (StreamConfigEntry sce : getStreams()) {
            subscribe(sce);
        }
        notifyStarted();
    }

    private List<StreamConfigEntry> getStreams() {
        List<StreamConfigEntry> r = new ArrayList<>();
        if (!readonly) {
            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            if (!config.containsKey("streams")) {
                List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.tm);
                for (StreamConfigEntry sce : sceList) {
                    r.add(sce);
                }
            } else {
                List<String> streamNames = config.getList("streams");
                for (String sn : streamNames) {
                    StreamConfigEntry sce = sc.getEntry(StandardStreamType.tm, sn);
                    if (sce == null) {
                        throw new ConfigurationException("No stream config found for '" + sn + "'");
                    }
                    r.add(sce);
                }
            }
        }
        return r;
    }

    @Override
    protected void doStop() {
        try {
            for (StreamConfigEntry sce : getStreams()) {
                unsubscribe(sce);
            }
            tmIndexer.close();
            tagDb.close();
            notifyStopped();
        } catch (IOException e) {
            log.error("failed to stop the indexer", e);
            notifyFailed(e);
        }
    }

    /**
     * Asynchronously submit an index request. When the request is processed the provided listener will receive
     * callbacks.
     * 
     * @param req
     *            the request to be executed
     * @param limit
     *            if greater than 0, the result will be limited to this number of records. If more records are
     *            available, the last call will provide a token which can be used for a subsequent request to get more
     *            data
     * @param token
     *            if this is a subsequent request, indicates a token which is used for continuation.
     * @param listener
     *            where to send the resulting data
     * @throws YamcsException
     *             exception thrown if the service is not in running state or the the request is invalid
     */
    public void submitIndexRequest(IndexRequest req, int limit, String token, IndexRequestListener listener)
            throws YamcsException {
        State state = state();
        if (state != State.RUNNING) {
            throw new YamcsException("The IndexServer service is not in state RUNNING but " + state);
        }

        if (!YamcsServer.hasInstance(req.getInstance())) {
            throw new YamcsException("Invalid instance " + req.getInstance());
        }
        IndexRequestProcessor p = new IndexRequestProcessor(tmIndexer, req, limit, token, listener);
        executor.submit(p);
    }

    /**
     * Asynchronously submit an index request. When the request is processed the provided listener will receive
     * callbacks.
     * 
     * @param req
     *            the request to be executed
     * @param listener
     *            where to send the resulting data
     * @throws YamcsException
     *             exception thrown if the service is not in running state or the the request is invalid
     */
    public void submitIndexRequest(IndexRequest req, IndexRequestListener listener) throws YamcsException {
        submitIndexRequest(req, -1, null, listener);
    }

    private void unsubscribe(StreamConfigEntry sce) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream tmStream = ydb.getStream(sce.getName());
        if (tmStream == null) {
            return;
        }
        tmStream.removeSubscriber(tmIndexer);
    }

    private void subscribe(StreamConfigEntry sce) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream tmStream = ydb.getStream(sce.getName());
        if (tmStream == null) {
            throw new ConfigurationException("There is no stream named " + sce.getName());
        }
        tmStream.addSubscriber(tmIndexer);
    }

    private static TmIndex loadIndexerFromClass(String icn, String instance, boolean readonly) throws IOException {
        try {
            Class<TmIndex> ic = (Class<TmIndex>) Class.forName(icn);
            Constructor<TmIndex> c = ic.getConstructor(String.class, Boolean.TYPE);
            return c.newInstance(instance, readonly);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof ConfigurationException) {
                throw (ConfigurationException) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new ConfigurationException(t.toString(), e);
            }
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create indexer from class " + icn + ": " + e, e);
        }
    }
}
