package org.yamcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.tctm.ArchiveTmPacketProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;


/**
 * This class helps keeping track of the different objects used in a channel - i.e. all the 
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
public class Channel {
    static private Map<String,Channel>instances=Collections.synchronizedMap(new HashMap<String,Channel>());
    private ParameterRequestManager parameterRequestManager;
    private ContainerRequestManager containerRequestManager;
    private CommandHistory commandHistoryPublisher;

    private CommandHistoryRequestManager commandHistoryRequestManager;

    private CommandingManager commandingManager;


    private TmPacketProvider tmPacketProvider;
    private CommandReleaser commandReleaser;
    private List<ParameterProvider> parameterProviders = new ArrayList<ParameterProvider>();

    private XtceDb xtcedb;

    private String name;
    private String type;
    private final String yamcsInstance;


    private String creator="system";
    private boolean persistent=false;

    static Logger log=LoggerFactory.getLogger(Channel.class.getName());
    static List<ChannelListener> listeners=new CopyOnWriteArrayList<ChannelListener>(); //send notifications for added and removed channels to this

    private boolean quitting;
    //a synchronous channel waits for all the clients to deliver tm packets and parameters
    private boolean synchronous=false;
    XtceTmProcessor tmProcessor;

    @GuardedBy("this")
    HashSet<ChannelClient> connectedClients= new HashSet<ChannelClient>();




    public Channel(String yamcsInstance, String name, String type, String creator) throws ChannelException {
	if((name==null) || "".equals(name)) {
	    throw new ChannelException("The channel name can not be empty");
	}
	log.info("creating a new channel name="+name+" type="+type);
	this.yamcsInstance=yamcsInstance;
	this.name=name; 
	this.creator=creator;
	this.type=type;
    }

    void init(TcTmService tctms) throws ChannelException, ConfigurationException {
	xtcedb=XtceDbFactory.getInstance(yamcsInstance);

	synchronized(instances) {
	    if(instances.containsKey(key(yamcsInstance,name))) throw new ChannelException("A channel named '"+name+"' already exists in instance "+yamcsInstance);

	    this.tmPacketProvider=tctms.getTmPacketProvider();
	    this.commandReleaser=tctms.getCommandReleaser();
	    List<ParameterProvider> providers = tctms.getParameterProviders();
	    if(providers!=null) {
		this.parameterProviders.addAll(providers);
	    }
	    
	    synchronous = tctms.isSynchronous();


	    // Shared between prm and crm
	    tmProcessor = new XtceTmProcessor(this);
	    tmPacketProvider.setTmProcessor(tmProcessor);
	    containerRequestManager=new ContainerRequestManager(this, tmProcessor);
	    parameterRequestManager=new ParameterRequestManager(this, tmProcessor);

	    containerRequestManager.setPacketProvider(tmPacketProvider);

	    for(ParameterProvider pprov: parameterProviders) {
		pprov.init(this);
		parameterRequestManager.addParameterProvider(pprov);
	    }

	    if(commandReleaser!=null) { 
		try {
		    this.commandHistoryPublisher=new YarchCommandHistoryAdapter(yamcsInstance);
		} catch (Exception e) {
		    throw new ConfigurationException("Cannot create command history" , e);
		}
		commandingManager=new CommandingManager(this);
		commandReleaser.setCommandHistory(commandHistoryPublisher);
		commandHistoryRequestManager = new CommandHistoryRequestManager(yamcsInstance);
	    } else {
		commandingManager=null;
	    }

	    instances.put(key(yamcsInstance,name),this);
	    for(int i=0; i<listeners.size(); i++) {
		listeners.get(i).channelAdded(this);
	    }
	    ManagementService.getInstance().registerChannel(this);
	}
    }



    private static String key(String instance, String name) {
	return instance+"."+name;
    }

    public CommandHistory getCommandHistoryListener() {
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
     * starts processing by invoking the start method for all the associated processors
     *
     */
    public void start() {
	tmPacketProvider.startAsync();
	if(commandReleaser!=null) {
	    commandReleaser.startAsync();
	    commandHistoryRequestManager.startAsync();
	}
	for(ParameterProvider pprov: parameterProviders) {
	    pprov.startAsync();
	}

	tmPacketProvider.awaitRunning();

	propagateChannelStateChange();
    }

    public void pause() {
	((ArchiveTmPacketProvider)tmPacketProvider).pause();
	propagateChannelStateChange();
    }

    public void resume() {
	((ArchiveTmPacketProvider)tmPacketProvider).resume();
	propagateChannelStateChange();
    }

    private void propagateChannelStateChange() {
	for(int i=0; i<listeners.size(); i++) {
	    listeners.get(i).channelStateChanged(this);
	}
    }
    public void seek(long instant) {
	getTmProcessor().resetStatistics();
	((ArchiveTmPacketProvider)tmPacketProvider).seek(instant);
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

    public static Channel getInstance(String yamcsInstance, String name) {
	return instances.get(key(yamcsInstance,name));
    }
    /**
     * Increase with one the number of connected clients to the named channel and return the channel.
     * @param name
     * @return the channel where with the given name
     * @throws ChannelException
     */
    public static Channel connect(String yamcsInstance, String name, ChannelClient s) throws ChannelException {
	Channel ds=instances.get(key(yamcsInstance,name));
	if(ds==null) throw new ChannelException("There is no channel named '"+name+"'");
	ds.connect(s);
	return ds;
    }

    /**
     * Increase with one the number of connected clients
     */
    public synchronized void connect(ChannelClient s) throws ChannelException {
	log.debug("Session "+name+" has one more user: " +s);
	if(quitting) throw new ChannelException("This channel has been closed");
	connectedClients.add(s);
    }

    /**
     * Disconnects a client from this channel. If the channel has no more clients, quit.
     *
     */
    public void disconnect(ChannelClient s) {
	if(quitting) return;
	boolean hasToQuit=false;
	synchronized(this) {
	    connectedClients.remove(s);
	    log.info("channel "+name+" has one less user: connectedUsers: "+connectedClients.size());
	    if((connectedClients.isEmpty())&&(!persistent)) {
		hasToQuit=true;
	    }
	}
	if(hasToQuit) quit();
    }


    public static Collection<Channel> getChannels() {
	return instances.values();
    }


    /**
     * Closes the channel by stoping the tm/pp and tc
     * It can be that there are still clients connected, but they will not get any data and new clients can not connect to
     * these channels anymore. Once it is close, you can create a channel with the same name which will make it maybe a bit 
     * confusing :(
     *
     */
    public void quit() {
	if(quitting)return;
	log.info("Channel "+name+" quitting");
	quitting=true;
	instances.remove(key(yamcsInstance,name));
	for(ParameterProvider p:parameterProviders) {
	    p.stopAsync();
	}
	//if(commandHistoryListener!=null) commandHistoryListener.channelStopped();
	if(commandReleaser!=null) commandReleaser.stopAsync();
	log.info("Channel "+name+" is out of business");
	for(int i=0; i<listeners.size(); i++) {
	    listeners.get(i).channelClosed(this);
	}
	ManagementService.getInstance().unregisterChannel(this);
	synchronized(this) {
	    for(ChannelClient s:connectedClients) {
		s.channelQuit();
	    }
	}
    }


    public static void addChannelListener(ChannelListener channelListener) {
	listeners.add(channelListener);
    }
    public static void removeChannelListner(ChannelListener channelListener) {
	listeners.remove(channelListener);
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
	return ServiceState.valueOf(tmPacketProvider.state().name());
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
     * @return the yamcs instance this channel is part of
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


}
