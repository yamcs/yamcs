package org.yamcs;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static org.yamcs.YamcsServer.CFG_CRASH_HANDLER_KEY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.yamcs.Spec.OptionType;
import org.yamcs.logging.Log;
import org.yamcs.management.LinkManager;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Mdb.MissionDatabase;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.ServiceUtil;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Header;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Represents a Yamcs instance together with the instance specific services and the processors
 * 
 */
public class YamcsServerInstance extends YamcsInstanceService {
    private String name;
    Log log;
    TimeService timeService;
    private CrashHandler crashHandler;
    List<ServiceWithConfig> services;
    private Mdb mdb;

    InstanceMetadata metadata;
    YConfiguration config;
    final Map<String, Processor> processors = new LinkedHashMap<>();
    LinkManager linkManager;
    final int instanceId;

    YamcsServerInstance(String name) {
        this(name, new InstanceMetadata());
    }

    YamcsServerInstance(String name, InstanceMetadata metadata) {
        this.name = name;
        this.metadata = metadata;
        log = new Log(getClass(), name);
        this.instanceId = (YamcsServer.getServer().getServerId() + "." + name).hashCode();
    }

    public static Spec getSpec() {
        Spec serviceSpec = new Spec();
        serviceSpec.addOption("class", OptionType.STRING).withRequired(true);
        serviceSpec.addOption("args", OptionType.ANY);
        serviceSpec.addOption("name", OptionType.STRING);
        serviceSpec.addOption("enabledAtStartup", OptionType.BOOLEAN);

        Spec mdbSpec = new Spec();
        mdbSpec.addOption("type", OptionType.STRING).withRequired(true);
        mdbSpec.addOption("spec", OptionType.STRING);
        mdbSpec.addOption("writable", OptionType.BOOLEAN).withDefault(false);
        mdbSpec.addOption("args", OptionType.MAP).withSpec(Spec.ANY);
        mdbSpec.addOption("subLoaders", OptionType.LIST).withElementType(OptionType.MAP).withSpec(mdbSpec);

        Spec spec = new Spec();
        spec.addOption("services", OptionType.LIST).withElementType(OptionType.MAP).withSpec(serviceSpec);

        // Detailed validation on these is done
        // in LinkManager, MdbFactory, and StreamInitializer
        spec.addOption("dataLinks", OptionType.LIST).withElementType(OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("streamConfig", OptionType.MAP).withSpec(Spec.ANY);

        spec.addOption("mdb", OptionType.LIST).withElementType(OptionType.MAP).withSpec(mdbSpec);
        spec.addOption("mdbSpec", OptionType.STRING);
        spec.mutuallyExclusive("mdb", "mdbSpec");

        spec.addOption("timeService", OptionType.ANY);
        spec.addOption("tmIndexer", OptionType.ANY);
        spec.addOption("eventDecoders", OptionType.ANY);

        YarchDatabaseInstance.addSpec(spec);

        // "anchors" is used to allow yaml anchors (reuse of blocks)
        spec.addOption("anchors", OptionType.ANY);

        YamcsServer yamcs = YamcsServer.getServer();
        Map<String, Spec> extraSections = yamcs.getConfigurationSections(ConfigScope.YAMCS_INSTANCE);
        extraSections.forEach((key, sectionSpec) -> {
            spec.addOption(key, OptionType.MAP).withSpec(sectionSpec)
                    .withApplySpecDefaults(true);
        });

        return spec;
    }

    void init(YConfiguration config) {
        try {
            this.config = getSpec().validate(config);
        } catch (ValidationException e) {
            // Don't care about stacktrace inside spec
            throw new UncheckedExecutionException(new ValidationException(
                    e.getContext(), e.getMessage()));
        }
        initAsync();
        try {
            awaitInitialized();
        } catch (IllegalStateException e) {
            throw new UncheckedExecutionException(e.getCause());
        }
    }

    @Override
    public void doInit() {
        try {
            loadTimeService();
            loadCrashHandler();

            // first load the MDB (if there is an error in it, we don't want to load any other service)
            mdb = MdbFactory.getInstance(name);
            StreamInitializer.createStreams(name);

            // create services before the link manager so that the pre-processors can find them
            // if required (even uninitialized)
            List<YConfiguration> serviceConfigs = config.getServiceConfigList("services");
            services = YamcsServer.createServices(name, serviceConfigs, log);

            linkManager = new LinkManager(name);

            YamcsServer.initServices(name, services);

            notifyInitialized();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStart() {
        linkManager.startLinks();
        for (ServiceWithConfig swc : services) {
            if (swc.enableAtStartup) {
                log.debug("Starting service {}", swc.getName());
                swc.service.startAsync();
            } else {
                log.debug("Not starting service {} because enableAtStartup is false", swc.getName());
            }
        }
        for (ServiceWithConfig swc : services) {
            if (swc.enableAtStartup) {
                log.info("Awaiting start of service {}", swc.getName());
                ServiceUtil.awaitServiceRunning(swc.service);
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        linkManager.stopLinks();
        ListeningExecutorService serviceStoppers = listeningDecorator(Executors.newCachedThreadPool());
        List<ListenableFuture<?>> stopFutures = new ArrayList<>();
        for (ServiceWithConfig swc : services) {
            stopFutures.add(serviceStoppers.submit(() -> {
                swc.service.stopAsync();
                log.info("Awaiting termination of service {}", swc.getName());
                ServiceUtil.awaitServiceTerminated(swc.service, YamcsServer.SERVICE_STOP_GRACE_TIME, log);
            }));
        }
        linkManager = null;

        serviceStoppers.shutdown();
        Futures.addCallback(Futures.allAsList(stopFutures), new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                log.info("Stopping Yamcs DB");
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(name);
                ydb.close();
                YarchDatabase.removeInstance(name);
                notifyStopped();
            }

            @Override
            public void onFailure(Throwable t) {
                notifyFailed(ExceptionUtil.unwind(t));
            }
        }, MoreExecutors.directExecutor());
    }

    public Mdb getMdb() {
        return mdb;
    }

    /**
     * Stops this instance, and waits until it terminates
     * 
     * @throws IllegalStateException
     *             if the instance fails to do a clean stop
     */
    public void stop() throws IllegalStateException {
        stopAsync();
        awaitOffline();

        // set to null to free some memory
        mdb = null;
        services = null;
    }

    public void loadTimeService() {
        if (config.containsKey("timeService")) {
            YConfiguration m = config.getConfig("timeService");
            String servclass = m.getString("class");
            if (m.containsKey("args")) {
                YConfiguration args = m.getConfig("args");
                timeService = YObjectLoader.loadObject(servclass, name, args);
            } else {
                timeService = YObjectLoader.loadObject(servclass, name);
            }
        } else {
            timeService = new RealtimeTimeService();
        }
    }

    private void loadCrashHandler() throws IOException {
        if (config.containsKey(CFG_CRASH_HANDLER_KEY)) {
            crashHandler = YamcsServer.loadCrashHandler(config);
        } else {
            crashHandler = YamcsServer.getServer().getGlobalCrashHandler();
        }
    }

    /**
     * Returns the main configuration for this Yamcs instance
     */
    public YConfiguration getConfig() {
        return config;
    }

    public ServiceWithConfig getServiceWithConfig(String serviceName) {
        if (services == null) {
            return null;
        }

        for (ServiceWithConfig swc : services) {
            if (swc.getName().equals(serviceName)) {
                return swc;
            }
        }
        return null;
    }

    public Service getService(String serviceName) {
        ServiceWithConfig serviceWithConfig = getServiceWithConfig(serviceName);
        return serviceWithConfig != null ? serviceWithConfig.getService() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends YamcsService> List<T> getServices(Class<T> serviceClass) {
        List<T> result = new ArrayList<>();
        if (services != null) {
            for (ServiceWithConfig swc : services) {
                if (serviceClass.isInstance(swc.service)) {
                    result.add((T) swc.service);
                }
            }
        }
        return result;
    }

    public <T extends YamcsService> List<ServiceWithConfig> getServicesWithConfig(Class<T> serviceClass) {
        List<ServiceWithConfig> result = new ArrayList<>();
        if (services != null) {
            for (ServiceWithConfig swc : services) {
                if (serviceClass.isInstance(swc.service)) {
                    result.add(swc);
                }
            }
        }
        return result;
    }

    /**
     * Return the service of the given class and name or null if not existing.
     * <p>
     * If a service of the given name but a different class exists (or the other way around), this function returns
     * null.
     * 
     * @param serviceClass
     * @param serviceName
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends YamcsService> T getService(Class<T> serviceClass, String serviceName) {
        if (services != null) {
            for (ServiceWithConfig swc : services) {
                YamcsService ys = swc.service;
                if (serviceClass.isInstance(ys) && swc.getName().equals(serviceName)) {
                    return (T) ys;
                }
            }
        }
        return null;
    }

    public TimeService getTimeService() {
        return timeService;
    }

    public List<ServiceWithConfig> getServices() {
        return new ArrayList<>(services);
    }

    public void startService(String serviceName) throws ConfigurationException, ValidationException, InitException {
        YamcsServer.startService(name, serviceName, services);
    }

    CrashHandler getCrashHandler() {
        return crashHandler;
    }

    /**
     * Returns the name of this Yamcs instance
     */
    public String getName() {
        return name;
    }

    public YamcsInstance getInstanceInfo() {
        YamcsInstance.Builder aib = YamcsInstance.newBuilder().setName(name);
        InstanceState state = state();
        aib.setState(state);
        if (state == InstanceState.FAILED) {
            aib.setFailureCause(failureCause().toString());
        }
        if (config != null) { // Can be null for an offline instance
            try {
                MissionDatabase.Builder mdbproto = MissionDatabase.newBuilder();
                if (config.containsKey("mdbSpec")) {
                    String configName = config.getString("mdbSpec");
                    mdbproto.setConfigName(configName);
                } else if (!config.isList("mdb")) {
                    String configName = config.getString("mdb");
                    mdbproto.setConfigName(configName);
                }
                if (mdb != null) { // if the instance is in a failed state, it could be that it doesn't have a MDB
                                      // (the failure might be due to the load of the MDB)
                    mdbproto.setName(mdb.getRootSpaceSystem().getName());
                    Header h = mdb.getRootSpaceSystem().getHeader();
                    if (h != null && h.getVersion() != null) {
                        mdbproto.setVersion(h.getVersion());
                    }
                }
                aib.setMissionDatabase(mdbproto.build());
            } catch (ConfigurationException | DatabaseLoadException e) {
                log.warn("Got error when finding the mission database for instance {}", name, e);
            }
        }
        aib.putAllLabels(getLabels());
        return aib.build();
    }

    public String getTemplate() {
        return metadata.getTemplate();
    }

    public String getTemplateSource() {
        return metadata.getTemplateSource();
    }

    public Map<String, Object> getTemplateArgs() {
        return metadata.getTemplateArgs();
    }

    public Object getMetadata(Object key) {
        return metadata.get(key);
    }

    public Map<String, String> getLabels() {
        return metadata.getLabels();
    }

    /**
     * Adds the processor to the instance. If already existing a processor with the same name, an exception is thrown
     * 
     * @param proc
     * @throws ProcessorException
     */
    public synchronized void addProcessor(Processor proc) throws ProcessorException {
        if (processors.containsKey(proc.getName())) {
            throw new ProcessorException(
                    "A processor named '" + proc.getName() + "' already exists in instance " + name);
        }
        processors.put(proc.getName(), proc);
        proc.setYamcsServerInstance(this);
    }

    /**
     * Returns the first register processor or null if there is no processor registered.
     * 
     * @return the first registered processor
     */
    public synchronized Processor getFirstProcessor() {
        if (processors.isEmpty()) {
            return null;
        } else {
            return processors.values().iterator().next();
        }
    }

    public synchronized List<Processor> getProcessors() {
        return new ArrayList<>(processors.values());
    }

    public synchronized Processor getProcessor(String processorName) {
        return processors.get(processorName);
    }

    public synchronized void removeProcessor(String processorName) {
        processors.remove(processorName);
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public int getInstanceId() {
        return instanceId;
    }
}
