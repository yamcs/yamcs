package org.yamcs;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.tctm.ArchiveTmPacketProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.util.concurrent.Service.State;

/**
 * This class helps keeping track of the different objects used in a channel - i.e. all the 
 *  objects required to have a TM/TC processing chain (either realtime or playback).
 *  
 * There are two ways in which parameter and packet delivery is performed:
 *  asynchronous - In this mode the parameters are put into a queue in order to not block the processing thread. The queue
 *                is flushed by the deliver method called from the sessionImpl own thread. This is the mode normally used 
 *                for realtime and playbacks at close to realtime speed.
 *  synchronous -  In this mode the paraemter are delivered in the processing queue, blocking thus the extraction if the client
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
	private CommandHistory commandHistoryListener;
	private CommandingManager commandingManager;
	private TmPacketProvider tmPacketProvider;
	private TcUplinker tcUplinker;
	private ParameterProvider paramProvider;
	
	public XtceDb xtcedb;
	
	private String name;
	private String type;
	private String spec;
	private final String yamcsInstance;
	
	
	private String creator="system";
	private boolean persistent=false;
	
	static Logger log=LoggerFactory.getLogger(Channel.class.getName());
	static List<ChannelListener> listeners=new CopyOnWriteArrayList<ChannelListener>(); //send notifications for added and removed channels to this
	
	private boolean quitting;
	//a synchronous channel waits for all the clients to deliver tm packets and parameters
	private boolean synchronous=false;
	
	
	@GuardedBy("this")
	HashSet<ChannelClient> connectedClients= new HashSet<ChannelClient>();

	
	public Channel(String yamcsInstance, String name, String type, String spec, String creator) throws ChannelException {
	    if((name==null) || "".equals(name)) 
			throw new ChannelException("The channel name can not be empty");
		log.info("creating a new channel name="+name+" type="+type+" spec="+spec);
		this.yamcsInstance=yamcsInstance;
		this.name=name; 
		this.creator=creator;
		this.type=type;
		this.spec=spec;
	}
	
	void init(TcTmService tctms, CommandHistory cmdHistListener) throws ChannelException, ConfigurationException {
        xtcedb=XtceDbFactory.getInstance(yamcsInstance);
        
        synchronized(instances) {
            if(instances.containsKey(key(yamcsInstance,name))) throw new ChannelException("An channel named '"+name+"' already exists in instance "+yamcsInstance);
           
            this.tmPacketProvider=tctms.getTmPacketProvider();
            this.tcUplinker=tctms.getTcUplinker();
            this.paramProvider=tctms.getParameterProvider();
            this.commandHistoryListener=cmdHistListener;
            
            if(tmPacketProvider instanceof ArchiveTmPacketProvider) {
                ReplaySpeed speed=((ArchiveTmPacketProvider)tmPacketProvider).getSpeed();
                if(speed.getType()==ReplaySpeedType.AFAP) synchronous=true;
            }
            
            parameterRequestManager=new ParameterRequestManager(this);
            parameterRequestManager.setPacketProvider(tmPacketProvider);
            parameterRequestManager.setProcessedParameterProvider(paramProvider);
            
            containerRequestManager = new ContainerRequestManager(this, parameterRequestManager.getTmProcessor());
            containerRequestManager.setPacketProvider(tmPacketProvider);
            
            if(tcUplinker!=null) { 
                commandingManager=new CommandingManager(this);
                if(commandHistoryListener!=null) tcUplinker.setCommandHistoryListener(commandHistoryListener);
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
    /**
	 * Used only for unit tests!
	 * @param hasCommanding
	 */
	public Channel (boolean hasCommanding) throws ConfigurationException {
	    this.yamcsInstance=null;
	    xtcedb=XtceDbFactory.getInstance(null);
		if(hasCommanding) {
			commandingManager=new CommandingManager(this);
		} else {
			commandingManager=null;
		}
		tmPacketProvider = null;
		paramProvider=null;
		tcUplinker=null;
		parameterRequestManager=null;
		containerRequestManager=null;
		commandHistoryListener=null;
	}
	
	
	public CommandHistory getCommandHistoryListener() {
		return commandHistoryListener;
	}

	public ParameterRequestManager getParameterRequestManager() {
		return parameterRequestManager;
	}
	
	public ContainerRequestManager getContainerRequestManager() {
	    return containerRequestManager;
	}

	public XtceTmProcessor getTmProcessor() {
		return (parameterRequestManager==null)?null:parameterRequestManager.getTmProcessor();
	}

	
	public ParameterProvider getParameterProvider() {
		return paramProvider;
	}
	
	/**
	 * starts processing by invoking the start method for all the associated processors
	 *
	 */
	public void start() {
	    Future<State> f=tmPacketProvider.start();
	    if(tcUplinker!=null)tcUplinker.start();
	    if(paramProvider!=null) paramProvider.start();
		if(parameterRequestManager!=null) parameterRequestManager.start();
		if(containerRequestManager!=null) containerRequestManager.start();
		try {
            f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
	public TcUplinker getTcUplinker() {
		return tcUplinker;
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
	 * @return the spec
	 */
	public String getSpec() {
		return spec;
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
		parameterRequestManager.quit();
		containerRequestManager.quit();
		//if(commandHistoryListener!=null) commandHistoryListener.channelStopped();
		if(tcUplinker!=null) tcUplinker.stop();
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
        return "name: "+name+" type: "+type+" spec:"+spec+" connectedClients:"+connectedClients.size();
    }
	
	/**
	 * 
	 * @return the yamcs instance this channel is part of
	 */
	public String getInstance() {
	    return yamcsInstance;
	}
}
