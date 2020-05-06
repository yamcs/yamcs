package org.yamcs.parameter;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Collects each second system processed parameters from whomever registers and sends them on the sys_var stream
 *
 *
 * @author nm
 *
 */
public class SystemParametersCollector extends AbstractYamcsService implements Runnable {

    static Map<String, SystemParametersCollector> instances = new HashMap<>();
    static long frequencyMillisec = 1000;
    List<SystemParametersProducer> providers = new CopyOnWriteArrayList<>();

    static final String STREAM_NAME = "sys_param";
    private String spJvmTotalMemory;
    private String spJvmMemoryUsed;
    private String spJvmTheadCount;

    public static final int JVM_COLLECTION_INTERVAL = 10;

    private boolean provideJvmVariables = false;
    private int jvmCollectionCountdown = 0;

    public static final int FS_COLLECTION_INTERVAL = 60;
    private boolean provideFsVariables = false;
    private int fsCollectionCountdown = 0;

    static final List<String> FILE_SYSTEM_TYPES = Arrays.asList("ext4", "ext3", "xfs");

    ScheduledThreadPoolExecutor timer;
    Stream stream;

    int seqCount = 0;

    private String namespace;
    private String serverId;

    TimeService timeService;
    List<FileStore> fileStores;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("provideJvmVariables", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("provideFsVariables", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        provideJvmVariables = config.getBoolean("provideJvmVariables");
        provideFsVariables = config.getBoolean("provideFsVariables");

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        stream = ydb.getStream(STREAM_NAME);
        if (stream == null) {
            throw new ConfigurationException("Stream '" + STREAM_NAME + "' does not exist");
        }

        serverId = YamcsServer.getServer().getServerId();
        namespace = XtceDb.YAMCS_SPACESYSTEM_NAME + NameDescription.PATH_SEPARATOR + serverId;
        log.debug("Using {} as serverId, and {} as namespace for system parameters", serverId, namespace);
        if (provideJvmVariables) {
            spJvmTotalMemory = namespace + "/jvmTotalMemory";
            log.debug("Publishing jvmTotalMemory with parameter id {}", spJvmTotalMemory);

            spJvmMemoryUsed = namespace + "/jvmMemoryUsed";
            log.debug("Publishing jvmMemoryUsed with parameter id {}", spJvmMemoryUsed);

            spJvmTheadCount = namespace + "/jvmThreadCount";
            log.debug("Publishing jvmThreadCount with parameter id {}", spJvmTheadCount);
        }

        if (provideFsVariables) {
            fileStores = new ArrayList<>();
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                if (FILE_SYSTEM_TYPES.contains(store.type())) {
                    if (fileStores.stream().filter(fs -> fs.name().equals(store.name())).findFirst().isPresent()) {
                        // sometimes (e.g. docker) the same filesystem is mounted multiple times in different locations
                        log.debug("Do not adding duplicate store '{}' to the file stores to be monitored", store);
                    } else {
                        log.debug("Adding store '{}' to the file stores to be monitored", store);
                        fileStores.add(store);
                    }
                }
            }
        }
        synchronized (instances) {
            instances.put(yamcsInstance, this);
        }
    }

    public static SystemParametersCollector getInstance(String instance) {
        synchronized (instances) {
            return instances.get(instance);
        }
    }

    @Override
    public void doStart() {
        timeService = YamcsServer.getServer().getInstance(yamcsInstance).getTimeService();
        timer = new ScheduledThreadPoolExecutor(1);
        timer.scheduleAtFixedRate(this, 1000L, frequencyMillisec, TimeUnit.MILLISECONDS);
        notifyStarted();
    }

    @Override
    public void doStop() {
        timer.shutdown();
        synchronized (instances) {
            instances.remove(yamcsInstance);
        }
        notifyStopped();
    }

    /**
     * Run from the timer, collect all parameters and send them on the stream
     */
    @Override
    public void run() {
        long gentime = timeService.getMissionTime();

        List<ParameterValue> params = new ArrayList<>();
        if (provideJvmVariables) {
            jvmCollectionCountdown--;
            if (jvmCollectionCountdown <= 0) {
                collectJvmParameters(params, gentime);
                jvmCollectionCountdown = JVM_COLLECTION_INTERVAL;
            }
        }
        if (provideFsVariables) {
            fsCollectionCountdown--;
            if (fsCollectionCountdown <= 0) {
                collectFsParameters(params, gentime);
                fsCollectionCountdown = FS_COLLECTION_INTERVAL;
            }
        }

        for (SystemParametersProducer p : providers) {
            try {
                Collection<ParameterValue> pvc = p.getSystemParameters();
                params.addAll(pvc);
            } catch (Exception e) {
                log.warn("Error getting parameters from provider {}", p, e);
            }
        }

        if (params.isEmpty()) {
            return;
        }
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER.copy();
        List<Object> cols = new ArrayList<>(4 + params.size());
        cols.add(gentime);
        cols.add(namespace);
        cols.add(seqCount);
        cols.add(gentime);
        for (ParameterValue pv : params) {
            if (pv == null) {
                log.error("Null parameter value encountered, skipping");
                continue;
            }
            String name = pv.getParameterQualifiedNamed();
            int idx = tdef.getColumnIndex(name);
            if (idx != -1) {
                log.warn("duplicate value for {}\nfirst: {}\n second: {}", name, cols.get(idx), pv);
                continue;
            }
            tdef.addColumn(name, DataType.PARAMETER_VALUE);
            cols.add(pv);
        }
        Tuple t = new Tuple(tdef, cols);
        stream.emitTuple(t);
    }

    private void collectJvmParameters(List<ParameterValue> params, long gentime) {
        Runtime r = Runtime.getRuntime();
        ParameterValue jvmTotalMemory = SystemParametersCollector.getPV(spJvmTotalMemory, gentime,
                r.totalMemory() / 1024);
        ParameterValue jvmMemoryUsed = SystemParametersCollector.getPV(spJvmMemoryUsed, gentime,
                (r.totalMemory() - r.freeMemory()) / 1024);
        ParameterValue jvmThreadCount = SystemParametersCollector.getUnsignedIntPV(spJvmTheadCount, gentime,
                Thread.activeCount());

        params.add(jvmTotalMemory);
        params.add(jvmMemoryUsed);
        params.add(jvmThreadCount);
    }

    private void collectFsParameters(List<ParameterValue> params, long gentime) {
        try {
            for (FileStore store : fileStores) {
                String name = store.name();
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                long ts = store.getTotalSpace();
                long av = store.getUsableSpace();
                float perc = (float) (100 - av * 100.0 / ts);

                ParameterValue total = SystemParametersCollector.getPV(namespace + "/df/" + name + "/total",
                        gentime, ts / 1024);
                ParameterValue available = SystemParametersCollector.getPV(namespace + "/df/" + name + "/available",
                        gentime, av / 1024);
                ParameterValue use = SystemParametersCollector.getPV(namespace + "/df/" + name + "/percentageUse",
                        gentime, perc);

                params.add(total);
                params.add(available);
                params.add(use);

            }
        } catch (IOException e) {
            log.error("Error when collecting disk space", e);
        }
    }

    /**
     * Register a parameter producer to be called each time the parameters are collected
     */
    public void registerProducer(SystemParametersProducer p) {
        log.debug("Registering system variables producer {}", p);
        if(providers.contains(p)) {
            throw new IllegalStateException("Producer already registered");
        }
        providers.add(p);
    }

    /**
     * Unregister producer - from now on it will not be invoked. Note that the collector collects parameters into a
     * different thread taking all producer in turns, and there might be one collection already started when this method
     * is called.
     * 
     */
    public void unregisterProducer(SystemParametersProducer p) {
        log.debug("Unregistering system variables producer {}", p);
        providers.remove(p);

    }

    /**
     * this is the namespace all system parameters should be in
     *
     * @return the namespace to be used by the system parameters
     */
    public String getNamespace() {
        return namespace;
    }

    public static ParameterValue getNewPv(String fqn, long time) {
        ParameterValue pv = new ParameterValue(fqn);
        pv.setAcquisitionTime(time);
        pv.setGenerationTime(time);
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, String v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getStringValue(v));
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, double v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getDoubleValue(v));
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, float v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getFloatValue(v));
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, boolean v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getBooleanValue(v));
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, long v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getSint64Value(v));
        return pv;
    }

    public static ParameterValue getUnsignedIntPV(String fqn, long time, int v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(ValueUtility.getUint64Value(v));
        return pv;
    }

    public static ParameterValue getPV(String fqn, long time, Value v) {
        ParameterValue pv = getNewPv(fqn, time);
        pv.setEngValue(v);
        return pv;
    }
}
