package org.yamcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.cmdhistory.StreamCommandHistoryProvider;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterCacheConfig;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.tctm.ArchiveTmPacketProvider;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;

/**
 * 
 * This class helps keeping track of the different objects used in a Yamcs Processor - i.e. all the objects required to
 * have a TM/TC processing chain (either realtime or playback).
 *
 *
 */
public class Processor extends AbstractService {
    private static final String CONFIG_KEY_TM_PROCESSOR = "tmProcessor";
    private static final String CONFIG_KEY_PARAMETER_CACHE = "parameterCache";
    private static final String CONFIG_KEY_ALARM = "alarm";
    private static final String CONFIG_KEY_GENERATE_EVENTS = "generateEvents";

    // handles subscriptions to parameters
    private ParameterRequestManager parameterRequestManager;
    // handles subscriptions to containers
    private ContainerRequestManager containerRequestManager;
    // handles subscriptions to command history
    private CommandHistoryRequestManager commandHistoryRequestManager;

    // handles command building and queues
    private CommandingManager commandingManager;

    // publishes events to command history
    private CommandHistoryPublisher commandHistoryPublisher;

    // these are services defined in the processor.yaml.
    // They have to register themselves to the processor in their init method
    private TmPacketProvider tmPacketProvider;
    private CommandHistoryProvider commandHistoryProvider;
    private CommandReleaser commandReleaser;

    private static Map<String, Processor> instances = Collections.synchronizedMap(new LinkedHashMap<>());

    private XtceDb xtcedb;

    private String name;
    private String type;
    private final String yamcsInstance;

    private boolean checkAlarms = true;
    private boolean alarmServerEnabled = false;

    private String creator = "system";
    private boolean persistent = false;

    ParameterCacheConfig parameterCacheConfig = new ParameterCacheConfig(false, false, 0, 0);

    final Logger log;
    static Set<ProcessorListener> listeners = new CopyOnWriteArraySet<>(); // send notifications for added and removed
    // processors to this

    private boolean quitting;
    // a synchronous processor waits for all the clients to deliver tm packets and parameters
    private boolean synchronous = false;
    XtceTmProcessor tmProcessor;

    // unless very good performance reasons, we should try to serialize all the processing in this thread
    private final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    TimeService timeService;

    ProcessorData processorData;
    @GuardedBy("this")
    HashSet<ProcessorClient> connectedClients = new HashSet<>();
    List<ServiceWithConfig> serviceList;

    public Processor(String yamcsInstance, String name, String type, String creator) throws ProcessorException {
        if ((name == null) || "".equals(name)) {
            throw new ProcessorException("The processor name must not be empty");
        }
        this.yamcsInstance = yamcsInstance;
        this.name = name;
        this.creator = creator;
        this.type = type;
        log = LoggingUtils.getLogger(Processor.class, this);
        log.info("Creating new processor '{}' of type '{}'", name, type);
    }

    /**
     * 
     * @param serviceList
     * @param config - configuration from processor.yaml
     * @param spec - configuration passed from the client when creating the processor
     * @throws ProcessorException
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    void init(List<ServiceWithConfig> serviceList, Map<String, Object> config, Object spec)
            throws ProcessorException, ConfigurationException {
        xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        boolean generateEvents = false;
        if(config !=null) {
            generateEvents = YConfiguration.getBoolean(config, CONFIG_KEY_GENERATE_EVENTS, true);
        }
        
        processorData = new ProcessorData(yamcsInstance, name, xtcedb, generateEvents);
        this.serviceList = serviceList;

        timeService = YamcsServer.getTimeService(yamcsInstance);
        Map<String, Object> tmProcessorConfig = null;

        synchronized (instances) {
            if (instances.containsKey(key(yamcsInstance, name))) {
                throw new ProcessorException(
                        "A processor named '" + name + "' already exists in instance " + yamcsInstance);
            }
            if (config != null) {
                for (Map.Entry<String, Object> me : config.entrySet()) {
                    String c = me.getKey();
                    Object o = me.getValue();
                    if (CONFIG_KEY_ALARM.equals(c)) {
                        if (!(o instanceof Map)) {
                            throw new ConfigurationException(CONFIG_KEY_ALARM + " configuration should be a map");
                        }
                        configureAlarms((Map<String, Object>) o);
                    } else if (CONFIG_KEY_PARAMETER_CACHE.equals(c)) {
                        if (!(o instanceof Map)) {
                            throw new ConfigurationException(
                                    CONFIG_KEY_PARAMETER_CACHE + " configuration should be a map");
                        }
                        configureParameterCache((Map<String, Object>) o);
                    } else if (CONFIG_KEY_TM_PROCESSOR.equals(c)) {
                        if (!(o instanceof Map)) {
                            throw new ConfigurationException(
                                    CONFIG_KEY_TM_PROCESSOR + " configuration should be a map");
                        }
                        tmProcessorConfig = (Map<String, Object>) o;
                    } else {
                        log.warn("Ignoring unknown config key '{}'", c);
                    }
                }
            }

            // Shared between prm and crm
            tmProcessor = new XtceTmProcessor(this, tmProcessorConfig);
            containerRequestManager = new ContainerRequestManager(this, tmProcessor);
            parameterRequestManager = new ParameterRequestManager(this, tmProcessor);

            for (ServiceWithConfig swc : serviceList) {
                ProcessorService service = (ProcessorService) swc.service;
                if (spec == null) {
                    service.init(this);
                } else {
                    service.init(this, spec);
                }
            }

            parameterRequestManager.init();

            instances.put(key(yamcsInstance, name), this);
            listeners.forEach(l -> l.processorAdded(this));
        }
    }

    public void setPacketProvider(TmPacketProvider tpp) {
        if (tmPacketProvider != null) {
            throw new IllegalStateException("There is already a packet provider");
        }
        tmPacketProvider = tpp;
    }

    public void setCommandReleaser(CommandReleaser cr) {
        if (commandReleaser != null) {
            throw new IllegalStateException("There is already a command releaser");
        }
        commandReleaser = cr;
        // maybe we should un-hardcode these two and make them services
        commandHistoryPublisher = new StreamCommandHistoryPublisher(yamcsInstance);
        setCommandHistoryProvider(new StreamCommandHistoryProvider(yamcsInstance));

        commandingManager = new CommandingManager(this);
        commandReleaser.setCommandHistory(commandHistoryPublisher);

    }

    public void setCommandHistoryProvider(CommandHistoryProvider chp) {
        if (commandHistoryProvider != null) {
            throw new IllegalStateException("There is already a command history provider");
        }
        commandHistoryProvider = chp;
        commandHistoryRequestManager = new CommandHistoryRequestManager(this);
        commandHistoryProvider.setCommandHistoryRequestManager(commandHistoryRequestManager);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    private void configureAlarms(Map<String, Object> alarmConfig) {
        Object v = alarmConfig.get("check");
        if (v != null) {
            if (!(v instanceof Boolean)) {
                throw new ConfigurationException(
                        "Unknown value '" + v + "' for alarmConfig -> check. Boolean expected.");
            }
            checkAlarms = (Boolean) v;
        }

        v = alarmConfig.get("server");
        if (v != null) {
            if (!(v instanceof String)) {
                throw new ConfigurationException(
                        "Unknown value '" + v + "' for alarmConfig -> server. String expected.");

            }
            alarmServerEnabled = "enabled".equalsIgnoreCase((String) v);
            if (alarmServerEnabled) {
                checkAlarms = true;
            }
        }
    }

    private void configureParameterCache(Map<String, Object> cacheConfig) {
        boolean enabled = false;
        boolean cacheAll = false;

        Object v = cacheConfig.get("enabled");
        if (v != null) {
            if (!(v instanceof Boolean)) {
                throw new ConfigurationException(
                        "Unknown value '" + v + "' for parameterCache -> enabled. Boolean expected.");
            }
            enabled = (Boolean) v;
        }
        if (!enabled) { // this is the default but print a warning if there are some things configured
            Set<String> keySet = cacheConfig.keySet();
            keySet.remove("enabled");
            if (!keySet.isEmpty()) {
                log.warn(
                        "Parmeter cache is disabled, the following keys are ignored: {}, use enable: true to enable the parameter cache",
                        keySet);
            }
            return;
        }

        v = cacheConfig.get("cacheAll");
        if (v != null) {
            if (!(v instanceof Boolean)) {
                throw new ConfigurationException(
                        "Unknown value '" + v + "' for parameterCache -> cacheAll. Boolean expected.");
            }
            cacheAll = (Boolean) v;
            if (cacheAll) {
                enabled = true;
            }
        }
        long duration = 1000L * YConfiguration.getInt(cacheConfig, "duration", 300);
        int maxNumEntries = YConfiguration.getInt(cacheConfig, "maxNumEntries", 512);

        parameterCacheConfig = new ParameterCacheConfig(enabled, cacheAll, duration, maxNumEntries);
    }

    private static String key(String instance, String name) {
        return instance + "." + name;
    }

    public CommandHistoryPublisher getCommandHistoryPublisher() {
        return commandHistoryPublisher;
    }

    public ParameterRequestManager getParameterRequestManager() {
        return parameterRequestManager;
    }

    public ContainerRequestManager getContainerRequestManager() {
        return containerRequestManager;
    }

    public XtceTmProcessor getTmProcessor() {
        return tmProcessor;
    }

    /**
     * starts processing by invoking the start method for all the associated components
     *
     */
    @Override
    public void doStart() {
        try {
            tmProcessor.startAsync();
            tmProcessor.addListener(new Listener() {
                @Override
                public void terminated(State from) {
                    stopAsync();
                }
            }, MoreExecutors.directExecutor());
            startIfNecessary(commandHistoryRequestManager);
            startIfNecessary(commandHistoryProvider);
            startIfNecessary(parameterRequestManager);
            startIfNecessary(tmPacketProvider);
            startIfNecessary(commandingManager);

            for (ServiceWithConfig swc : serviceList) {
                startIfNecessary(swc.service);
            }

            tmProcessor.awaitRunning();
            ;
            awaitIfNecessary(commandHistoryRequestManager);
            awaitIfNecessary(commandHistoryProvider);
            awaitIfNecessary(parameterRequestManager);
            awaitIfNecessary(tmPacketProvider);
            awaitIfNecessary(commandingManager);

            for (ServiceWithConfig swc : serviceList) {
                swc.service.awaitRunning();
            }

            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
        propagateProcessorStateChange();
    }

    public List<ServiceWithConfig> getServices() {
        return serviceList.stream().collect(Collectors.toList());
    }

    private void startIfNecessary(Service service) {
        if (service != null) {
            if (service.state() == State.NEW) {
                service.startAsync();
            }
        }
    }

    private void awaitIfNecessary(Service service) {
        if (service != null) {
            service.awaitRunning();
        }
    }

    public void pause() {
        ((ArchiveTmPacketProvider) tmPacketProvider).pause();
        propagateProcessorStateChange();
    }

    public void resume() {
        ((ArchiveTmPacketProvider) tmPacketProvider).resume();
        propagateProcessorStateChange();
    }

    private void propagateProcessorStateChange() {
        listeners.forEach(l -> l.processorStateChanged(this));
    }

    public void seek(long instant) {
        getTmProcessor().resetStatistics();
        ((ArchiveTmPacketProvider) tmPacketProvider).seek(instant);
        propagateProcessorStateChange();
    }

    public void changeSpeed(ReplaySpeed speed) {
        ((ArchiveTmPacketProvider) tmPacketProvider).changeSpeed(speed);
        propagateProcessorStateChange();
    }

    /**
     * @return the tcUplinker
     */
    public CommandReleaser getCommandReleaser() {
        return commandReleaser;
    }

    /**
     * @return the tmPacketProvider
     */
    public TmPacketProvider getTmPacketProvider() {
        return tmPacketProvider;
    }

    public String getName() {
        return name;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public int getConnectedClients() {
        return connectedClients.size();
    }

    public static Processor getInstance(String yamcsInstance, String name) {
        return instances.get(key(yamcsInstance, name));
    }

    /**
     * Returns the first register processor for the given instance or null if there is no processor registered.
     * 
     * @param yamcsInstance
     *            - instance name for which the processor has to be returned.
     * @return the first registered processor for the given instance
     */
    public static Processor getFirstProcessor(String yamcsInstance) {
        for (Map.Entry<String, Processor> me : instances.entrySet()) {
            if (me.getKey().startsWith(yamcsInstance + ".")) {
                return me.getValue();
            }
        }
        return null;
    }

    /**
     * Increase with one the number of connected clients to the named processor and return the processor.
     * 
     * @param yamcsInstance
     * @param name
     * @param s
     * @return the processor with the given name
     * @throws ProcessorException
     */
    public static Processor connect(String yamcsInstance, String name, ProcessorClient s) throws ProcessorException {
        Processor ds = instances.get(key(yamcsInstance, name));
        if (ds == null) {
            throw new ProcessorException("There is no processor named '" + name + "'");
        }
        ds.connect(s);
        return ds;
    }

    /**
     * Increase with one the number of connected clients
     */
    public synchronized void connect(ProcessorClient s) throws ProcessorException {
        log.debug("Processor {} has one more user: {}", name, s);
        if (quitting) {
            throw new ProcessorException("This processor has been closed");
        }
        connectedClients.add(s);
    }

    /**
     * Disconnects a client from this processor. If the processor has no more clients, quit.
     *
     */
    public void disconnect(ProcessorClient s) {
        if (quitting) {
            return;
        }
        boolean hasToQuit = false;
        synchronized (this) {
            if (connectedClients.remove(s)) {
                log.info("Processor {} has one less user: connectedUsers: {}", name, connectedClients.size());
                if ((connectedClients.isEmpty()) && (!persistent)) {
                    hasToQuit = true;
                }
            }
        }
        if (hasToQuit) {
            stopAsync();
        }
    }

    public static Collection<Processor> getProcessors() {
        return instances.values();
    }

    public static Collection<Processor> getProcessors(String instance) {
        List<Processor> processors = new ArrayList<>();
        for (Processor processor : instances.values()) {
            if (instance.equals(processor.getInstance())) {
                processors.add(processor);
            }
        }
        return processors;
    }

    /**
     * Closes the processor by stoping the tm/pp and tc It can be that there are still clients connected, but they will
     * not get any data and new clients can not connect to these processors anymore. Once it is closed, you can create a
     * processor with the same name which will make it maybe a bit confusing :(
     *
     */
    @Override
    public void doStop() {
        if (quitting) {
            return;
        }

        log.info("Processor {} quitting", name);
        quitting = true;
        timer.shutdown();
        // first send a STOPPING event
        listeners.forEach(l -> l.processorStateChanged(this));

        for (ServiceWithConfig swc : serviceList) {
            swc.service.stopAsync();
        }

        instances.remove(key(yamcsInstance, name));

        if (commandReleaser != null) {
            commandReleaser.stopAsync();
            commandingManager.stopAsync();
        }
        if (tmProcessor != null) {
            tmProcessor.stopAsync();
        }
        if (tmPacketProvider != null) {
            tmPacketProvider.stopAsync();
        }

        log.info("Processor {} is out of business", name);

        if (getState() == ServiceState.RUNNING || getState() == ServiceState.STOPPING) {
            notifyStopped();
        }
        // and now a CLOSED event
        listeners.forEach(l -> l.processorClosed(this));
        synchronized (this) {
            for (ProcessorClient s : connectedClients) {
                s.processorQuit();
            }
        }
    }

    public static void addProcessorListener(ProcessorListener processorListener) {
        listeners.add(processorListener);
    }

    public static void removeProcessorListener(ProcessorListener processorListener) {
        listeners.remove(processorListener);
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean systemSession) {
        this.persistent = systemSession;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public boolean hasCommanding() {
        return commandingManager != null;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public boolean isReplay() {
        if (tmPacketProvider == null) {
            return false;
        }

        return tmPacketProvider.isArchiveReplay();
    }

    /**
     * valid only if isArchiveReplay returns true
     * 
     * @return
     */
    public ReplayRequest getReplayRequest() {
        return ((ArchiveTmPacketProvider) tmPacketProvider).getReplayRequest();
    }

    /**
     * valid only if isArchiveReplay returns true
     * 
     * @return
     */
    public ReplayState getReplayState() {
        return ((ArchiveTmPacketProvider) tmPacketProvider).getReplayState();
    }

    public ServiceState getState() {
        return ServiceState.valueOf(state().name());
    }

    public CommandingManager getCommandingManager() {
        return commandingManager;
    }

    @Override
    public String toString() {
        return "name: " + name + " type: " + type + " connectedClients:" + connectedClients.size();
    }

    /**
     *
     * @return the yamcs instance this processor is part of
     */
    public String getInstance() {
        return yamcsInstance;
    }

    public XtceDb getXtceDb() {
        return xtcedb;
    }

    public CommandHistoryRequestManager getCommandHistoryManager() {
        return commandHistoryRequestManager;
    }

    public boolean hasAlarmChecker() {
        return checkAlarms;
    }

    public boolean hasAlarmServer() {
        return alarmServerEnabled;
    }

    public ScheduledThreadPoolExecutor getTimer() {
        return timer;
    }

    /**
     * Returns the processor time
     * 
     * for realtime processors it is the mission time or simulation time for replay processors it is the replay time
     * 
     * @return
     */
    public long getCurrentTime() {
        if (isReplay()) {
            return ((ArchiveTmPacketProvider) tmPacketProvider).getReplayTime();
        } else {
            return timeService.getMissionTime();
        }
    }

    public void quit() {
        stopAsync();
        awaitTerminated();
    }

    public void start() {
        startAsync();
        awaitRunning();
    }

    public void notifyStateChange() {
        propagateProcessorStateChange();
    }

    /**
     * returns a list of all processors names
     * 
     * @return all processors names as a list of instance.processorName
     */
    public static List<String> getAllProcessors() {
        List<String> l = new ArrayList<>(instances.size());
        l.addAll(instances.keySet());
        return l;
    }

    public ParameterCacheConfig getPameterCacheConfig() {
        return parameterCacheConfig;
    }

    public ParameterCache getParameterCache() {
        return parameterRequestManager.getParameterCache();
    }

    /**
     * Returns the processor data used to store processor specific calibration, alarms
     * 
     * @return processor specific data
     */
    public ProcessorData getProcessorData() {
        return processorData;
    }

    public LastValueCache getLastValueCache() {
        return processorData.getLastValueCache();
    }
}
