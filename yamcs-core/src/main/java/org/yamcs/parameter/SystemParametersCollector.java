package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Collects each second system processed parameters from whomever registers and sends them on the sys_var stream
 *
 *
 * @author nm
 *
 */
public class SystemParametersCollector extends AbstractService implements YamcsService, Runnable {
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

    ScheduledThreadPoolExecutor timer;
    final Stream stream;

    int seqCount = 0;
    private final Logger log;

    private final String namespace;
    private final String serverId;

    final String instance;
    TimeService timeService;

    public SystemParametersCollector(String instance) throws ConfigurationException {
        this(instance, null);
    }

    public SystemParametersCollector(String instance, Map<String, Object> args) throws ConfigurationException {
        this.instance = instance;
        log = LoggingUtils.getLogger(this.getClass(), instance);
        processArgs(args);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream s = ydb.getStream(STREAM_NAME);
        if (s == null) {
            throw new ConfigurationException("Stream '" + STREAM_NAME + "' does not exist");
        }
        stream = s;

        serverId = YamcsServer.getServerId();
        namespace = XtceDb.YAMCS_SPACESYSTEM_NAME + NameDescription.PATH_SEPARATOR + serverId;
        log.debug("Using {} as serverId, and {} as namespace for system parameters", serverId, namespace);
        if (provideJvmVariables) {
            spJvmTotalMemory = namespace + "/jvmTotalMemory";
            log.debug("publishing jvmTotalMemory with parameter id {}", spJvmTotalMemory);

            spJvmMemoryUsed = namespace + "/jvmMemoryUsed";
            log.debug("publishing jvmMemoryUsed with parameter id {}", spJvmMemoryUsed);

            spJvmTheadCount = namespace + "/jvmThreadCount";
            log.debug("publishing jvmThreadCount with parameter id {}", spJvmTheadCount);
        }
        synchronized (instances) {
            instances.put(instance, this);
        }
    }

    public static SystemParametersCollector getInstance(String instance) {
        synchronized (instances) {
            return instances.get(instance);
        }
    }

    private void processArgs(Map<String, Object> args) {
        if (args == null) {
            return;
        }
        if (args.containsKey("provideJvmVariables")) {
            provideJvmVariables = YConfiguration.getBoolean(args, "provideJvmVariables");
        }
    }

    @Override
    public void doStart() {
        timeService = YamcsServer.getInstance(instance).getTimeService();
        timer = new ScheduledThreadPoolExecutor(1);
        timer.scheduleAtFixedRate(this, 1000L, frequencyMillisec, TimeUnit.MILLISECONDS);
        notifyStarted();
    }

    @Override
    public void doStop() {
        timer.shutdown();
        synchronized (instances) {
            instances.remove(instance);
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

    /**
     * Register a parameter producer to be called each time the parameters are collected
     *
     * @deprecated use {@link #registerProducer(SystemParametersProducer p)} instead. There is no need to specify which
     *             paramameters will be provided and in addition they don't even need to be part of the Xtcedb (i.e. the
     *             provider can make them up on the fly)
     * 
     */
    @Deprecated
    public void registerProvider(SystemParametersProducer p, Collection<Parameter> params) {
        registerProvider(p);
    }

    /**
     * @deprecated old name for {@link #registerProducer}
     */
    @Deprecated
    public void registerProvider(SystemParametersProducer p) {
        registerProducer(p);
    }

    /**
     * Register a parameter producer to be called each time the parameters are collected
     */
    public void registerProducer(SystemParametersProducer p) {
        log.debug("Registering system variables producer {}", p);
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

}
