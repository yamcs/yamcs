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

import org.slf4j.Logger;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.cmdhistory.StreamCommandHistoryProvider;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterCacheConfig;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.tctm.ArchiveTmPacketProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;


/**
 * 
 * 
 * 
 * This class helps keeping track of the different objects used in a Yamcs Processor - i.e. all the 
 *  objects required to have a TM/TC processing chain (either realtime or playback).
 *
 * There are two ways in which parameter and packet delivery is performed:
 *  asynchronous - In this mode the parameters are put into a queue in order to not block the processing thread. The queue
 *                is flushed by the deliver method called from the sessionImpl own thread. This is the mode normally used 
 *                for realtime and playbacks at close to realtime speed.
 *  synchronous -  In this mode the parameter are delivered in the processing queue, blocking thus the extraction if the client
 *                is slow. This is the mode used in the as fast as possible retrievals. 
 *
 *  The synchronous/asynchronous logic is implemented in the TelemetryImpl and TelemetryPacketImpl classes
 * @author mache
 *
 */
public class YProcessor extends AbstractService {
    private final String CONFIG_KEY_tmProcessor ="tmProcessor";
    private final String CONFIG_KEY_parameterCache ="parameterCache";


    static private Map<String,YProcessor>instances=Collections.synchronizedMap(new LinkedHashMap<>());
    private ParameterRequestManagerImpl parameterRequestManager;
    private ContainerRequestManager containerRequestManager;
    private CommandHistoryPublisher commandHistoryPublisher;

    private CommandHistoryRequestManager commandHistoryRequestManager;

    private CommandingManager commandingManager;

    private final String CONFIG_KEY_alarm ="alarm";

    private CommandHistoryProvider commandHistoryProvider;


    private TmPacketProvider tmPacketProvider;
    private CommandReleaser commandReleaser;
    private List<ParameterProvider> parameterProviders = new ArrayList<ParameterProvider>();

    private XtceDb xtcedb;

    private String name;
    private String type;
    private final String yamcsInstance;

    private boolean checkAlarms = true;
    private boolean alarmServerEnabled = false;

    private String creator="system";
    private boolean persistent=false;

    ParameterCacheConfig parameterCacheConfig = new ParameterCacheConfig(false, false, 0, 0);

    final Logger log;
    static Set<ProcessorListener> listeners=new CopyOnWriteArraySet<>(); //send notifications for added and removed processors to this

    private boolean quitting;
    //a synchronous processor waits for all the clients to deliver tm packets and parameters
    private boolean synchronous=false;
    XtceTmProcessor tmProcessor;

    //unless very good performance reasons, we should try to serialize all the processing in this thread
    final private ExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    final private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    TimeService timeService;

    @GuardedBy("this")
    HashSet<ProcessorClient> connectedClients= new HashSet<ProcessorClient>();

    public YProcessor(String yamcsInstance, String name, String type, String creator) throws ProcessorException {
        if((name==null) || "".equals(name)) {
            throw new ProcessorException("The processor name must not be empty");
        }
        this.yamcsInstance=yamcsInstance;
        this.name=name;
        this.creator=creator;
        this.type=type;
        log=YamcsServer.getLogger(YProcessor.class, this);
        log.info("Creating new processor '{}' of type '{}'", name, type);
    }



    @SuppressWarnings("unchecked")
    void init(TcTmService tctms, Map<String, Object> config) throws ProcessorException, ConfigurationException {
        xtcedb = XtceDbFactory.getInstance(yamcsInstance);

        timeService = YamcsServer.getTimeService(yamcsInstance);
        Map<String, Object> tmProcessorConfig = null;

        synchronized(instances) {
            if(instances.containsKey(key(yamcsInstance,name))) throw new ProcessorException("A processor named '"+name+"' already exists in instance "+yamcsInstance);
            if(config!=null) {
                for(String c: config.keySet()) {
                    if(CONFIG_KEY_alarm.equals(c)) {
                        Object o = config.get(c);
                        if(!(o instanceof Map)) {
                            throw new ConfigurationException(CONFIG_KEY_alarm+" configuration should be a map");
                        }
                        configureAlarms((Map<String, Object>) o);
                    } else if(CONFIG_KEY_parameterCache.equals(c)) {
                        Object o = config.get(c);
                        if(!(o instanceof Map)) {
                            throw new ConfigurationException(CONFIG_KEY_parameterCache + " configuration should be a map");
                        }
                        configureParameterCache((Map<String, Object>) o);
                    } else if(CONFIG_KEY_tmProcessor.equals(c)) {
                        Object o = config.get(c);
                        if(!(o instanceof Map)) {
                            throw new ConfigurationException(CONFIG_KEY_tmProcessor+ " configuration should be a map");
                        }
                        tmProcessorConfig = (Map<String, Object>) o;
                    } else {
                        log.warn("Ignoring unknown config key '"+c+"'");
                    }
                }
            }


            this.tmPacketProvider=tctms.getTmPacketProvider();
            this.commandReleaser=tctms.getCommandReleaser();
            List<ParameterProvider> providers = tctms.getParameterProviders();
            if(providers!=null) {
                this.parameterProviders.addAll(providers);
            }

            synchronous = tctms.isSynchronous();


            // Shared between prm and crm
            tmProcessor = new XtceTmProcessor(this, tmProcessorConfig);
            if(tmPacketProvider!=null) {
                tmPacketProvider.init(this, tmProcessor);
            }
            containerRequestManager = new ContainerRequestManager(this, tmProcessor);
            parameterRequestManager = new ParameterRequestManagerImpl(this, tmProcessor);

            //    containerRequestManager.setPacketProvider(tmPacketProvider);

            for(ParameterProvider pprov: parameterProviders) {
                pprov.init(this);
                parameterRequestManager.addParameterProvider(pprov);
            }


            //QUICK HACK  TODO
            if((tmPacketProvider!=null) && (tmPacketProvider instanceof ParameterProvider) ) {
                parameterRequestManager.addParameterProvider((ParameterProvider)tmPacketProvider);
            }

            parameterRequestManager.init();


            if(commandReleaser!=null) {
                try {
                    this.commandHistoryPublisher=new YarchCommandHistoryAdapter(yamcsInstance);
                } catch (Exception e) {
                    throw new ConfigurationException("Cannot create command history" , e);
                }
                commandingManager=new CommandingManager(this);
                commandReleaser.setCommandHistory(commandHistoryPublisher);
                commandHistoryRequestManager = new CommandHistoryRequestManager(this);
                commandHistoryProvider = new StreamCommandHistoryProvider();
                commandHistoryProvider.setCommandHistoryRequestManager(commandHistoryRequestManager);

            } else {
                commandingManager=null;
                //QUICK HACK  TODO
                if((tmPacketProvider!=null) && (tmPacketProvider instanceof CommandHistoryProvider) ) {
                    commandHistoryProvider = (CommandHistoryProvider) tmPacketProvider;
                    commandHistoryRequestManager = new CommandHistoryRequestManager(this);
                    commandHistoryProvider.setCommandHistoryRequestManager(commandHistoryRequestManager);
                }
            }


            instances.put(key(yamcsInstance,name),this);
            listeners.forEach(l -> l.processorAdded(this));
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    private void configureAlarms(Map<String, Object> alarmConfig) {
        Object v = alarmConfig.get("check");
        if(v!=null) {
            if(!(v instanceof Boolean)) {
                throw new ConfigurationException("Unknown value '"+v+"' for alarmConfig -> check. Boolean expected.");
            }
            checkAlarms = (Boolean)v;
        }

        v = alarmConfig.get("server");
        if(v!=null) {
            if(!(v instanceof String)) {
                throw new ConfigurationException("Unknown value '"+v+"' for alarmConfig -> server. String expected.");

            }
            alarmServerEnabled = "enabled".equalsIgnoreCase((String)v);
            if(alarmServerEnabled) checkAlarms=true;
        }
    }

    private void configureParameterCache(Map<String, Object> cacheConfig) {
        boolean enabled = false;
        boolean cacheAll = false;
        long duration = 10*60*1000;

        Object v = cacheConfig.get("enabled");
        if(v!=null) {
            if(!(v instanceof Boolean)) {
                throw new ConfigurationException("Unknown value '"+v+"' for parameterCache -> enabled. Boolean expected.");
            }
            enabled = (Boolean)v;
        }
        if(!enabled) { //this is the default but print a warning if there are some things configured
            Set<String> keySet = cacheConfig.keySet();
            keySet.remove("enabled");
            if(!keySet.isEmpty()) {
                log.warn("Parmeter cache is disabled, the following keys are ignored: {}, use enable: true to enable the parameter cache", keySet);
            }
            return;
        }

        v = cacheConfig.get("cacheAll");
        if(v!=null) {
            if(!(v instanceof Boolean)) {
                throw new ConfigurationException("Unknown value '"+v+"' for parameterCache -> cacheAll. Boolean expected.");
            }
            cacheAll = (Boolean)v;
            if(cacheAll) enabled=true;
        }
        duration = 1000L * YConfiguration.getInt(cacheConfig, "duration", 600);
        int maxNumEntries = YConfiguration.getInt(cacheConfig, "maxNumEntries", 4096);

        parameterCacheConfig = new ParameterCacheConfig(enabled, cacheAll, duration, maxNumEntries);
    }

    private static String key(String instance, String name) {
        return instance+"."+name;
    }

    public CommandHistoryPublisher getCommandHistoryPublisher() {
        return commandHistoryPublisher;
    }

    public ParameterRequestManagerImpl getParameterRequestManager() {
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
            if(tmPacketProvider!=null) {
                tmPacketProvider.startAsync();
            }
            if(tmProcessor!=null) {
                tmProcessor.startAsync();
            }
            if(commandReleaser!=null) {
                commandReleaser.startAsync();
                commandReleaser.awaitRunning();
                commandingManager.startAsync();
                commandingManager.awaitRunning();
                CommandQueueManager cqm = commandingManager.getCommandQueueManager();
                cqm.startAsync();
                cqm.awaitRunning();
            }

            if(commandHistoryRequestManager!=null) {
                commandHistoryRequestManager.startAsync();
                startIfNecessary(commandHistoryProvider);

                commandHistoryRequestManager.awaitRunning();
                commandHistoryProvider.awaitRunning();
            }


            for(ParameterProvider pprov: parameterProviders) {
                pprov.startAsync();
            }



            parameterRequestManager.start();
            if(tmPacketProvider!=null) {
                tmPacketProvider.awaitRunning();
            }
            if(tmProcessor!=null) {
                tmProcessor.awaitRunning();
            }
            notifyStarted();
            
        } catch (IllegalStateException e) {
            notifyFailed(e.getCause());
        } catch (Exception e) {
            notifyFailed(e);
        }
        propagateProcessorStateChange();        
    }

    private void startIfNecessary(Service service) {
        if(service.state()==State.NEW) {
            service.startAsync();
        }
    }

    public void pause() {
        ((ArchiveTmPacketProvider)tmPacketProvider).pause();
        propagateProcessorStateChange();
    }

    public void resume() {
        ((ArchiveTmPacketProvider)tmPacketProvider).resume();
        propagateProcessorStateChange();
    }

    private void propagateProcessorStateChange() {
        listeners.forEach(l -> l.processorStateChanged(this));
    }

    public void seek(long instant) {
        getTmProcessor().resetStatistics();
        ((ArchiveTmPacketProvider)tmPacketProvider).seek(instant);
        propagateProcessorStateChange();
    }

    public void changeSpeed(ReplaySpeed speed) {        
        ((ArchiveTmPacketProvider)tmPacketProvider).changeSpeed(speed);
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

    public static YProcessor getInstance(String yamcsInstance, String name) {
        return instances.get(key(yamcsInstance, name));
    }

    /**
     * Returns the first register processor for the given instance or null if there is no processor registered.
     * 
     * @param yamcsInstance - instance name for which the processor has to be returned.
     * @return the first registered processor for the given instance 
     */
    public static YProcessor getFirstProcessor(String yamcsInstance) {
        for(String k: instances.keySet()) {
            if(k.startsWith(yamcsInstance+".")) {
                return instances.get(k);
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
    public static YProcessor connect(String yamcsInstance, String name, ProcessorClient s) throws ProcessorException {
        YProcessor ds = instances.get(key(yamcsInstance, name));
        if(ds==null) throw new ProcessorException("There is no processor named '"+name+"'");
        ds.connect(s);
        return ds;
    }

    /**
     * Increase with one the number of connected clients
     */
    public synchronized void connect(ProcessorClient s) throws ProcessorException {
        log.debug("Session "+name+" has one more user: " +s);
        if(quitting) throw new ProcessorException("This processor has been closed");
        connectedClients.add(s);
    }

    /**
     * Disconnects a client from this processor. If the processor has no more clients, quit.
     *
     */
    public void disconnect(ProcessorClient s) {
        if(quitting) return;
        boolean hasToQuit=false;
        synchronized(this) {
            connectedClients.remove(s);
            log.info("Processor "+name+" has one less user: connectedUsers: "+connectedClients.size());
            if((connectedClients.isEmpty())&&(!persistent)) {
                hasToQuit=true;
            }
        }
        if(hasToQuit) stopAsync();
    }


    public static Collection<YProcessor> getProcessors() {
        return instances.values();
    }

    public static Collection<YProcessor> getProcessors(String instance) {
        List<YProcessor> processors = new ArrayList<>();
        for (YProcessor processor : instances.values()) {
            if (instance.equals(processor.getInstance())) {
                processors.add(processor);
            }
        }
        return instances.values();
    }


    /**
     * Closes the processor by stoping the tm/pp and tc
     * It can be that there are still clients connected, but they will not get any data and new clients can not connect to
     * these processors anymore. Once it is closed, you can create a processor with the same name which will make it maybe a bit 
     * confusing :(
     *
     */
    @Override
    public void doStop() {
        if(quitting)return;
        log.info("Processor "+name+" quitting");
        quitting=true;
        
        instances.remove(key(yamcsInstance,name));
        
        for(ParameterProvider p:parameterProviders) {
            p.stopAsync();
        }
        if(commandReleaser!=null) commandReleaser.stopAsync();
        if(tmProcessor!=null) {
            tmProcessor.stopAsync();
        }
        if(tmPacketProvider!=null) {
            tmPacketProvider.stopAsync();
        }
        
        
        log.info("Processor "+name+" is out of business");
        listeners.forEach(l -> l.processorClosed(this));
        synchronized(this) {
            for(ProcessorClient s:connectedClients) {
                s.yProcessorQuit();
            }
        }
        if(getState() == ServiceState.RUNNING || getState() == ServiceState.STOPPING) {
            notifyStopped();
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
        return (commandingManager!=null);
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public boolean isReplay() {
        if(tmPacketProvider==null) return false;

        return tmPacketProvider.isArchiveReplay();
    }

    /**
     * valid only if isArchiveReplay returns true
     * @return
     */
    public ReplayRequest getReplayRequest() {
        return ((ArchiveTmPacketProvider)tmPacketProvider).getReplayRequest();
    }

    /**
     * valid only if isArchiveReplay returns true
     * @return
     */
    public ReplayState getReplayState() {
        return ((ArchiveTmPacketProvider)tmPacketProvider).getReplayState();
    }

    public ServiceState getState() {
        return ServiceState.valueOf(state().name());
    }

    public CommandingManager getCommandingManager() {
        return commandingManager;
    }

    @Override
    public String toString() {
        return "name: "+name+" type: "+type+" connectedClients:"+connectedClients.size();
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
     *  for realtime processors it is the mission time or simulation time
     *  for replay processors it is the replay time
     * @return 
     */
    public long getCurrentTime() {        
        if(isReplay()) {
            return ((ArchiveTmPacketProvider)tmPacketProvider).getReplayTime();
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
     * @return all processors names as a list of <instance>.<processorName> 
     */
    public static List<String> getAllProcessors() {
        List<String> l = new ArrayList<String>(instances.size());
        l.addAll(instances.keySet());
        return l;
    }
    
    public ParameterCacheConfig getPameterCacheConfig() {
        return parameterCacheConfig;
    }

    public ParameterCache getParameterCache() {
        return parameterRequestManager.getParameterCache();
    }
}
