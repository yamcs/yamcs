package org.yamcs.commanding;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.GuardedBy;
import org.yamcs.InvalidCommandId;
import org.yamcs.Privilege;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.tctm.TcUplinker;


import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.utils.TimeEncoding;


/**
 * @author nm
 * Implements the management of the control queues for one channel:
 *  - for each command that is sent, based on the sender it finds the queue where the command should go
 *  - depending on the queue state the command can be immediately sent, stored in the queue or rejected
 *  - when the command is immediately sent or rejected, the command queue monitor is not notified 
 * 
 * Note: the update of the command monitors is done in the same thread. That means that if the connection to one 
 *  of the monitors is lost, there may be a delay of a few seconds. As the monitoring clients will be priviledged users
 *  most likely connected in the same LAN, I don't consider this to be an issue. 
 */
@ThreadSafe
public class CommandQueueManager {
	@GuardedBy("this")
	private HashMap<String,CommandQueue> queues=new HashMap<String,CommandQueue>();
	TcUplinker uplinker;
	CommandHistory commandHistoryListener;
	CommandingManager commandingManager;
	ConcurrentLinkedQueue<CommandQueueListener> monitoringClients=new ConcurrentLinkedQueue<CommandQueueListener>();
	private final Logger log;
	
	private final String instance,channelName;
	
	/**
	 * Constructs a Command Queue Manager having the given history manager and tc uplinker.
	 *  The parameters have to be not null.
	 * @param commandHistoryListener
	 * @param uplinker
	 * @throws ConfigurationException in case there is an error in the configuration file. 
	 *         Note: if the configuration file doesn't exist, this exception is not thrown.
	 */
	public CommandQueueManager(CommandingManager commandingManager) throws ConfigurationException {
		this.commandingManager=commandingManager;
		
		Channel chan=commandingManager.getChannel();
		log=LoggerFactory.getLogger(this.getClass().getName()+"["+chan.getName()+"]");
		this.commandHistoryListener=chan.getCommandHistoryListener();
		this.uplinker=chan.getTcUplinker();
		this.instance=chan.getInstance();
		this.channelName=chan.getName();
		
		CommandQueue cq=new CommandQueue(chan, "default");
		queues.put("default", cq);
		
		YConfiguration config=YConfiguration.getConfiguration("command-queue");
		List<String> queueList;
		if(!config.containsKey("queueNames")) {
			log.warn("queueNames configuration variable is not set. Using just the default queue");
			return;
		}
		queueList=config.getList("queueNames");
		for(String qn:queueList) {
			if(!queues.containsKey(qn)) {
				queues.put(qn,new CommandQueue(chan, qn));
			}
			CommandQueue q=queues.get(qn);
			String state=config.getString(qn, "state");
			q.state=CommandQueueManager.stringToQueueState(state);
			q.roles=config.getList(qn, "roles");
		}
	 }


	private static QueueState stringToQueueState(String state) throws ConfigurationException {
		if("enabled".equalsIgnoreCase(state)) {
			return QueueState.ENABLED;
		}
		if("disabled".equalsIgnoreCase(state)) {
			return QueueState.DISABLED;
		}
		if("blocked".equalsIgnoreCase(state)) {
			return QueueState.BLOCKED;
		}
		throw new ConfigurationException("'"+state+"' is not a valid queue state. Use one of enabled,disabled or blocked");
	}

    public Collection<CommandQueue> getQueues() {
	    return queues.values();
	}

	/**
	 * Called from the CommandingImpl to add a command to the queue
	 * First the command is added to the command history
	 * Depending on the status of the queue, the command is rejected by setting the FSC=NACK in the command history 
	 *  added to the queue or directly sent using the uplinker
	 * 
	 * @param pc
	 */
	public synchronized void addCommand(PreparedCommand pc) {
		commandHistoryListener.addCommand(pc);

		CommandQueue q=getQueue(pc);
		if(q.state==QueueState.DISABLED) {
			nackCommand(q, pc,false);
		} else if(q.state==QueueState.BLOCKED) {
			q.commands.add(pc);
			//	Notify the monitoring clients
			System.out.println("going to notify "+monitoringClients.size()+" clients that command is added into queue"+q.name);
			for(CommandQueueListener m:monitoringClients) {
				try {
					m.commandAdded(q, pc);
				} catch (Exception e) {
					e.printStackTrace();
					log.warn("got exception "+e+" when notifying a monitor, removing it from the list");
					monitoringClients.remove(m);
				}
			}
		} else if(q.state==QueueState.ENABLED) {
			uplinkCommand(q, pc, false, false);	
		}
	}
	
	
	/**
	 * send a negative ack for a command.
	 * @param pc the prepared command for which the negative ack is sent
	 * @param notify notify or not the monitoring clients.
	 */
	private void nackCommand(CommandQueue cq, PreparedCommand pc, boolean notify) {
		try {
			commandHistoryListener.updateStringKey(pc.getCommandId(), "Acknowledge_FSC_Status","NACK: Queue disabled");
			commandHistoryListener.updateTimeKey(pc.getCommandId(), "Acknowledge_FSC_Time", TimeEncoding.currentInstant());
		} catch (InvalidCommandId e1) {
			log.warn("got invalid commandId when nacking command "+pc.getCommandId());
		}
		//Notify the monitoring clients
		if(notify) {
			for(CommandQueueListener m:monitoringClients) {
				try {
					m.commandRejected(cq, pc);
				} catch (Exception e) {
					log.warn("got exception "+e+" when notifying a monitor, removing it from the list");
					monitoringClients.remove(m);
				}
			}
		}
	}
	
	private void uplinkCommand(CommandQueue q, PreparedCommand pc, boolean notify, boolean rebuild) {
		if(rebuild) {
			try {
				pc=commandingManager.buildCommand(pc.source, pc.getCommandId().toBuilder());
			} catch (YamcsException e) {
				log.warn("Got Exception for a command already in the queue: "+e.getMessage());
				return;
			}
		}
		uplinker.sendTc(pc);
		//Notify the monitoring clients
		if(notify) {
			for(CommandQueueListener m:monitoringClients) {
				try {
					m.commandSent(q, pc);
				} catch (Exception e) {
					log.warn("got exception "+e+" when notifying a monitor, removing it from the list");
					monitoringClients.remove(m);
				}
			}
		}
	}
	
	/**
	 * @param pc 
	 * @return the queue where the command should be placed.
	 */
	private CommandQueue getQueue(PreparedCommand pc) {
		Privilege priv=Privilege.getInstance();
		if(!priv.isEnabled()) return queues.get("default");
		
		String[] roles=priv.getRoles();
		if(roles==null) return queues.get("default");
		for(String role:roles) {
			for(CommandQueue cq:queues.values()) {
				if(cq.roles==null)continue;
				for(String r1:cq.roles) {
					if(role.equals(r1)){
						return cq;
					}
				}
			}
		}
		
		return queues.get("default");
	}

	/**
	 * Called via CORBA to remove a command from the queue
	 * @param commandId 
	 * @throws CommandQueueException 
	 * @throws InsufficientPrivileges 
	 */
	public synchronized PreparedCommand rejectCommand(CommandId commandId) {
		log.info("called to remove command: "+commandId);
		PreparedCommand pc=null;
		CommandQueue queue=null;
		for(CommandQueue q:queues.values()) {
			for(PreparedCommand c:q.commands) {
				if(c.getCommandId().equals(commandId)) {
					pc=c;
					queue=q;
					break;
				}
			}
		}
		if(pc!=null) {
		    nackCommand(queue, pc, true);
		    queue.commands.remove(pc);
		} else {
		    log.warn("command not found in any queue");
		}
		return pc;
	}

	/**
	 * Send a command from the queue
	 * @param commandId 
	 * @throws CommandQueueException 
	 * @throws InsufficientPrivileges 
	 */
    public synchronized PreparedCommand sendCommand(CommandId commandId, boolean rebuild) {
		PreparedCommand command=null;
		CommandQueue queue=null;
		for(CommandQueue q:queues.values()) {
			for(PreparedCommand pc:q.commands) {
				if(pc.getCommandId().equals(commandId)) {
					command=pc;
					queue=q;
					break;
				}
			}
		}
		if(command!=null) {
		    queue.commands.remove(command);
		    uplinkCommand(queue, command, true, rebuild);
		}
		return command;
	}

	/**
	 * Called via CORBA to change the queue state
	 * @param queueName the queue whose state has to be set
	 * @param newState the new state of the queue
	 * @throws CommandQueueException thrown when there is no queue with the given name
	 */
	public synchronized CommandQueue setQueueState(String queueName, QueueState newState, boolean rebuild) {
		CommandQueue queue =null;
		for(CommandQueue q:queues.values()) {
			if(q.name.equals(queueName)) {
				queue=q;
				break;
			}
		}
		if(queue==null) return null;

		queue.state=newState;
		if(queue.state==QueueState.ENABLED) {
			for(PreparedCommand pc:queue.commands) {
				uplinkCommand(queue, pc,true, rebuild);
			}
			queue.commands.clear();
		}
		if(queue.state==QueueState.DISABLED) {
			for(PreparedCommand pc:queue.commands) {
				nackCommand(queue, pc,true);
			}
			queue.commands.clear(); 
		}
		//	Notify the monitoring clients
		for(CommandQueueListener m:monitoringClients) {
			try {
				m.updateQueue(queue);
			} catch (Exception e) {
				log.warn("got exception "+e+" when notifying a monitor, removing it from the list");
				monitoringClients.remove(m);
			}
		}
		return queue;
	}
	/**
	 * Called from a queue monitor to register itself in order to be notified when 
	 *   new commands are added/removed from the queue.
	 * @param cqm the callback which will be called with updates
	 */
	public void registerListener(CommandQueueListener cqm) {
		monitoringClients.add(cqm);
	}
	
	public String getInstance() {
		return instance;
	}

	public String getChannelName() {
		return channelName;
	}
}
