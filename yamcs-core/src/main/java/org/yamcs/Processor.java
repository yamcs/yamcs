package org.yamcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.cmdhistory.StreamCommandHistoryProvider;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.Acknowledgment;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmProcessor;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterCacheConfig;
import org.yamcs.parameter.ParameterPersistence;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.tctm.ArchiveTmPacketProvider;
import org.yamcs.tctm.StreamParameterSender;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * 
 * This class helps keeping track of the different objects used in a Yamcs Processor - i.e. all the objects required to
 * have a TM/TC processing chain (either realtime or playback).
 *
 */
public class Processor extends AbstractService {
    public static final String PROC_PARAMETERS_STREAM = "proc_param";

    // runs algorithms and distributes parameters
    private ParameterProcessorManager parameterProcessorManager;

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

    private Mdb mdb;

    private final String name;
    private final String type;
    private final String yamcsInstance;

    private ProcessorConfig config;

    private String creator = "system";
    private boolean persistent = false;
    private boolean protected_ = false;

    final Log log;
    static Set<ProcessorListener> listeners = new CopyOnWriteArraySet<>(); // send notifications for added and removed
    // processors to this

    private boolean quitting;
    // a synchronous processor waits for all the clients to deliver tm packets and parameters
    private boolean synchronous = false;

    XtceTmProcessor tmProcessor;

    private final ScheduledThreadPoolExecutor timer;
    TimeService timeService;

    ProcessorData processorData;
    List<ProcessorServiceWithConfig> serviceList;
    StreamParameterSender streamParameterSender;
    EventAlarmServer eventAlarmServer;
    YamcsServerInstance ysi;

    // Globally available acknowledgments (in addition to Q, R, S)
    private Set<Acknowledgment> acknowledgments = new CopyOnWriteArraySet<>();

    private ParameterPersistence paramPersistence;

    public Processor(String yamcsInstance, String name, String type, String creator) throws ProcessorException {
        if ((name == null) || "".equals(name)) {
            throw new ProcessorException("The processor name must not be empty");
        }
        this.yamcsInstance = yamcsInstance;
        this.name = name;
        this.creator = creator;
        this.type = type;
        log = new Log(Processor.class, yamcsInstance);
        log.info("Creating new processor '{}' of type '{}'", name, type);
        log.setContext(name);
        timer = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("Processor-" + yamcsInstance + "." + name).build());
    }

    /**
     * If recording to the archive initial values and local parameters is enabled, this class can be used to do it.
     * 
     * Otherwise it will return null.
     * 
     * @return the stream parameter sender that can be used to send data on the {@link #PROC_PARAMETERS_STREAM} stream
     *         to be recorded in the archive
     */
    public StreamParameterSender getStreamParameterSender() {
        return streamParameterSender;
    }

    /**
     * 
     * @param serviceList
     * @param config
     *            - configuration from processor.yaml
     * @param spec
     *            - configuration passed from the client when creating the processor
     * @throws ProcessorException
     * @throws ValidationException
     * @throws ConfigurationException
     */
    void init(List<ProcessorServiceWithConfig> serviceList, ProcessorConfig config, Object spec)
            throws ProcessorException, InitException, ValidationException {
        log.debug("Initialzing the processor with the configuration {}", config);

        mdb = MdbFactory.getInstance(yamcsInstance);

        this.config = config;

        this.serviceList = serviceList;

        timeService = YamcsServer.getTimeService(yamcsInstance);

        Map<Parameter, ParameterValue> persistedParams = new HashMap<>();

        if (config.persistParameters) {
            paramPersistence = new ParameterPersistence(yamcsInstance, name);
            var it = paramPersistence.load();

            if (it != null) {
                while (it.hasNext()) {
                    var pv = it.next();
                    var p = mdb.getParameter(pv.getParameterQualifiedName());
                    if (p != null) {
                        if (p.isPersistent()) {
                            pv.setParameter(p);
                            persistedParams.put(p, pv);
                        } else {
                            log.debug("Found persisted parameter without the persistance flag set {}",
                                    pv.getParameterQualifiedName());
                        }
                    } else {
                        log.debug("No parameter found in the MDB for persisted parameter value {}",
                                pv.getParameterQualifiedName());
                    }
                }
            }
        }

        processorData = new ProcessorData(this, config, persistedParams);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        Stream pps = ydb.getStream(PROC_PARAMETERS_STREAM);
        if (pps != null) {
            streamParameterSender = new StreamParameterSender(yamcsInstance, pps);
        }
        if (config.recordInitialValues || config.recordLocalValues) {
            if (pps == null) {
                throw new ConfigurationException("recordInitialValues is set to true but the stream '"
                        + PROC_PARAMETERS_STREAM + "' does not exist");
            }
            streamParameterSender.sendParameters(processorData.getLastValueCache().getValues());
        }
        if (config.eventAlarmServerEnabled) {
            eventAlarmServer = new EventAlarmServer(yamcsInstance, config, timer);
        }
        // Shared between prm and crm
        tmProcessor = new XtceTmProcessor(this);
        containerRequestManager = new ContainerRequestManager(this, tmProcessor);
        parameterProcessorManager = new ParameterProcessorManager(this, tmProcessor);

        for (ProcessorServiceWithConfig swc : serviceList) {
            if (swc.service instanceof CommandHistoryPublisher) {
                commandHistoryPublisher = (CommandHistoryPublisher) swc.service;
            }

            if (swc.service instanceof CommandHistoryProvider) {
                setCommandHistoryProvider((CommandHistoryProvider) swc.service);
            }
            if (swc.service instanceof CommandReleaser) {
                this.commandReleaser = (CommandReleaser) swc.service;
            }
        }
        if (commandReleaser != null) {
            if (commandHistoryPublisher == null) {
                commandHistoryPublisher = new StreamCommandHistoryPublisher(yamcsInstance);
            }
            if (commandHistoryProvider == null) {
                setCommandHistoryProvider(new StreamCommandHistoryProvider(yamcsInstance));
            }

            commandingManager = new CommandingManager(this);
            commandReleaser.setCommandHistory(commandHistoryPublisher);
        }
        for (ProcessorServiceWithConfig swc : serviceList) {
            ProcessorService service = (ProcessorService) swc.service;
            service.init(this, swc.getConfig(), spec);
        }

        parameterProcessorManager.init();

        listeners.forEach(l -> l.processorAdded(this));
    }

    public void setPacketProvider(TmPacketProvider tpp) {
        if (tmPacketProvider != null) {
            throw new IllegalStateException("There is already a packet provider");
        }
        tmPacketProvider = tpp;
    }

    public void setCommandHistoryProvider(CommandHistoryProvider chp) {
        if (commandHistoryProvider != null) {
            throw new IllegalStateException("There is already a command history provider");
        }
        commandHistoryProvider = chp;
        commandHistoryRequestManager = new CommandHistoryRequestManager(this);
        commandHistoryProvider.setCommandHistoryRequestManager(commandHistoryRequestManager);
    }

    public CommandHistoryPublisher getCommandHistoryPublisher() {
        return commandHistoryPublisher;
    }

    public ParameterProcessorManager getParameterProcessorManager() {
        return parameterProcessorManager;
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
            startIfNecessary(parameterProcessorManager);
            startIfNecessary(tmPacketProvider);
            startIfNecessary(commandingManager);
            startIfNecessary(eventAlarmServer);

            for (ProcessorServiceWithConfig swc : serviceList) {
                startIfNecessary(swc.service);
            }

            tmProcessor.awaitRunning();

            awaitIfNecessary(commandHistoryRequestManager);
            awaitIfNecessary(commandHistoryProvider);
            awaitIfNecessary(parameterProcessorManager);
            awaitIfNecessary(tmPacketProvider);
            awaitIfNecessary(commandingManager);
            awaitIfNecessary(eventAlarmServer);

            for (ProcessorServiceWithConfig swc : serviceList) {
                swc.service.awaitRunning();
            }

            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
        propagateProcessorStateChange();
    }

    public List<ProcessorServiceWithConfig> getServices() {
        return serviceList.stream().collect(Collectors.toList());
    }

    private void startIfNecessary(Service service) {
        if (service != null) {
            if (service.state() == State.NEW) {
                service.startAsync();
            }
        }
    }

    void setYamcsServerInstance(YamcsServerInstance ysi) {
        this.ysi = ysi;
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
        ArchiveTmPacketProvider provider = (ArchiveTmPacketProvider) tmPacketProvider;
        provider.resume();
        if (provider.getSpeed() != null
                && provider.getSpeed().getType() != ReplaySpeedType.STEP_BY_STEP) {
            propagateProcessorStateChange();
        }
    }

    private void propagateProcessorStateChange() {
        listeners.forEach(l -> l.processorStateChanged(this));
    }

    public void seek(long instant) {
        seek(instant, true);
    }

    public void seek(long instant, boolean autostart) {
        getTmProcessor().resetStatistics();
        ((ArchiveTmPacketProvider) tmPacketProvider).seek(instant, autostart);
        propagateProcessorStateChange();
    }

    public void changeSpeed(ReplaySpeed speed) {
        ((ArchiveTmPacketProvider) tmPacketProvider).changeSpeed(speed);
        propagateProcessorStateChange();
    }

    public void changeRange(long start, long stop) {
        ((ArchiveTmPacketProvider) tmPacketProvider).changeRange(start, stop);
        ((ArchiveTmPacketProvider) tmPacketProvider).seek(start, false);
        propagateProcessorStateChange();
    }

    public void changeEndAction(EndAction endAction) {
        ((ArchiveTmPacketProvider) tmPacketProvider).changeEndAction(endAction);
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

    /**
     * Returns globally available acknowledgments (in addition to Acknowledge_Queued, Acknowledge_Released and
     * Acknowledge_Sent).
     */
    public Collection<Acknowledgment> getAcknowledgments() {
        return acknowledgments;
    }

    /**
     * Add a globally available acknowledgment (in addition to Acknowledge_Queued, Acknowledge_Released and
     * Acknowledge_Sent).
     */
    public void addAcknowledgment(Acknowledgment acknowledgment) {
        acknowledgments.add(acknowledgment);
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

        for (ProcessorServiceWithConfig swc : serviceList) {
            swc.service.stopAsync();
        }

        if (commandReleaser != null) {
            commandReleaser.stopAsync();
            commandingManager.stopAsync();
        }
        if (tmProcessor != null) {
            tmProcessor.stopAsync();
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.stopAsync();
        }
        log.info("Processor {} is out of business", name);

        if (ysi != null) {
            ysi.removeProcessor(name);
        }

        if (paramPersistence != null) {
            paramPersistence.save(processorData.getValuesToBePersisted().iterator());
        }

        if (getState() == ServiceState.RUNNING || getState() == ServiceState.STOPPING) {
            notifyStopped();
        }
        // and now a CLOSED event
        listeners.forEach(l -> l.processorClosed(this));
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

    /**
     * Returns if this processor is protected. A protected processor may not be deleted.
     */
    public boolean isProtected() {
        return protected_;
    }

    public void setProtected(boolean protected_) {
        this.protected_ = protected_;
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

    public ReplayRequest getCurrentReplayRequest() {
        return ((ArchiveTmPacketProvider) tmPacketProvider).getCurrentReplayRequest();
    }

    public ServiceState getState() {
        return ServiceState.valueOf(state().name());
    }

    public CommandingManager getCommandingManager() {
        return commandingManager;
    }

    /**
     *
     * @return the yamcs instance this processor is part of
     */
    public String getInstance() {
        return yamcsInstance;
    }

    public Mdb getMdb() {
        return mdb;
    }

    public CommandHistoryRequestManager getCommandHistoryManager() {
        return commandHistoryRequestManager;
    }

    public boolean hasAlarmChecker() {
        return config.checkParameterAlarms;
    }

    public boolean hasAlarmServer() {
        return config.parameterAlarmServerEnabled;
    }

    public ScheduledThreadPoolExecutor getTimer() {
        return timer;
    }

    /**
     * Returns the processor time
     * 
     * for realtime processors it is the mission time (could be simulated) for replay processors it is the replay time
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

    public ParameterCacheConfig getPameterCacheConfig() {
        return config.parameterCacheConfig;
    }

    public ParameterCache getParameterCache() {
        return parameterProcessorManager.getParameterCache();
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

    public boolean isSubscribeAll() {
        return config.subscribeAll;
    }

    @SuppressWarnings("unchecked")
    public <T extends ProcessorService> List<T> getServices(Class<T> serviceClass) {
        List<T> services = new ArrayList<>();
        if (serviceList != null) {
            for (ProcessorServiceWithConfig swc : serviceList) {
                if (swc.getServiceClass().equals(serviceClass.getName())) {
                    services.add((T) swc.service);
                }
            }
        }
        return services;
    }

    public boolean recordLocalValues() {
        return config.recordLocalValues;
    }

    public EventAlarmServer getEventAlarmServer() {
        return eventAlarmServer;
    }

    public ProcessorConfig getConfig() {
        return config;
    }

    public ParameterRequestManager getParameterRequestManager() {
        return parameterProcessorManager.getParameterRequestManager();
    }

    @Override
    public String toString() {
        return "name: " + name + " type: " + type;
    }
}
