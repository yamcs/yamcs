package org.yamcs.archive;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.hornetq.HornetQIndexServer;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Handles index retrievals and tags
 *
 */
public class IndexServer extends AbstractService {
    static Logger log=LoggerFactory.getLogger(IndexServer.class.getName());

    TmIndex tmIndexer;

    final String yamcsInstance;

    ThreadPoolExecutor executor=new ThreadPoolExecutor(10,10,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(10));

    final TagDb tagDb;

    HornetQIndexServer hqIndexServer;
    boolean startArtemisServer;

    /**
     * Maps instance names to archive directories
     */
    final HashSet<String> instances=new HashSet<>();

    public IndexServer(String instance) throws IOException, YarchException {
        this(instance, null);
    }

    public IndexServer(String yamcsInstance, Map<String, Object> config) throws YarchException, IOException {
        boolean readonly = false;
        this.yamcsInstance = yamcsInstance;
        YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);

        if(c.containsKey("tmIndexer")) {
            String icn = c.getString("tmIndexer");
            tmIndexer = loadIndexerFromClass(icn, yamcsInstance, readonly);
        } else {
            tmIndexer = new CccsdsTmIndex(yamcsInstance, readonly);
        }
        startArtemisServer = c.getBoolean("startArtemisServer", false);

        if(!readonly) {
            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            if(config==null) {
                List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.tm);
                for(StreamConfigEntry sce: sceList){
                    subscribe(sce);
                }
            } else {
                List<String> streamNames = YConfiguration.getList(config, "streams");
                for(String sn: streamNames) {
                    StreamConfigEntry sce = sc.getEntry(StandardStreamType.tm, sn);
                    if(sce==null) {
                        throw new ConfigurationException("No stream config found for '"+sn+"'");
                    }
                    subscribe(sce);
                }
            }
        }

        tagDb = YarchDatabase.getInstance(yamcsInstance).getDefaultStorageEngine().getTagDb();
        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    protected void doStart() {
        try {
            if(startArtemisServer) {
                hqIndexServer = new HornetQIndexServer(this, tagDb);
                hqIndexServer.startAsync();
                hqIndexServer.awaitRunning();
            }
            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            if(hqIndexServer!=null) {
                hqIndexServer.stopAsync();
                hqIndexServer.awaitTerminated();
            }
            tmIndexer.close();
            tagDb.close();
        } catch (IOException e) {
            log.error("failed to stop the indexer", e);
            notifyFailed(e);
        }
    }

    public String getInstance() {
        return yamcsInstance;
    }

    /**
     * Asynchronously submit an index request. When the request is processed the
     * provided listener will receive callbacks.
     * @param req the request to be executed
     * @param listener where to send the resulting data
     * @throws YamcsException exception thrown if the service is not in running state or the the request is invalid
     */
    public void submitIndexRequest(IndexRequest req, IndexRequestListener listener) throws YamcsException {
        State state = state();
        if(state!=State.RUNNING) {
            throw new YamcsException("The IndexServer service is not in state RUNNING but "+state);
        }

        if(!YamcsServer.hasInstance(req.getInstance())) {
            throw new YamcsException("Invalid instance "+req.getInstance());
        }
        IndexRequestProcessor p = new IndexRequestProcessor(tmIndexer, req, listener);
        executor.submit(p);
    }

    private void subscribe(StreamConfigEntry sce) {
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream tmStream = ydb.getStream(sce.getName());
        if(tmStream==null) throw new ConfigurationException("There is no stream named "+sce.getName());
        tmStream.addSubscriber(tmIndexer);
    }

    private static TmIndex loadIndexerFromClass(String icn, String instance, boolean readonly) throws IOException {
        try {
            Class<TmIndex> ic=(Class<TmIndex>)Class.forName(icn);
            Constructor<TmIndex> c=ic.getConstructor(String.class, Boolean.TYPE);
            return c.newInstance(instance, readonly);
        } catch (InvocationTargetException e) {
            Throwable t=e.getCause();
            if(t instanceof ConfigurationException) {
                throw (ConfigurationException)t;
            } else if(t instanceof IOException) {
                throw (IOException)t;
            } else {
                throw new ConfigurationException(t.toString());
            }
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create indexer from class "+icn+": "+e);
        }
    }
}
