package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.ConfigurationException;
import org.yamcs.GuardedBy;
import org.yamcs.InvalidCommandId;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterValue;
import org.yamcs.Privilege;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ComparisonProcessor;

import com.google.common.util.concurrent.AbstractService;


/**
 * @author nm
 * Implements the management of the control queues for one channel:
 *  - for each command that is sent, based on the sender it finds the queue where the command should go
 *  - depending on the queue state the command can be immediately sent, stored in the queue or rejected
 *  - when the command is immediately sent or rejected, the command queue monitor is not notified
 *  - if the command has transmissionConstraints with timeout>0, the command can sit in the queue even if the queue is not blocked
 * 
 * Note: the update of the command monitors is done in the same thread. That means that if the connection to one 
 *  of the monitors is lost, there may be a delay of a few seconds. As the monitoring clients will be priviledged users
 *  most likely connected in the same LAN, I don't consider this to be an issue. 
 */
@ThreadSafe
public class CommandQueueManager extends AbstractService implements ParameterConsumer {
    @GuardedBy("this")
    private HashMap<String, CommandQueue> queues=new HashMap<String, CommandQueue>();
    CommandReleaser commandReleaser;
    CommandHistory commandHistoryListener;
    CommandingManager commandingManager;
    ConcurrentLinkedQueue<CommandQueueListener> monitoringClients=new ConcurrentLinkedQueue<CommandQueueListener>();
    private final Logger log;
    
    private Set<TransmissionConstraintChecker> pendingTcCheckers = new HashSet<TransmissionConstraintChecker>();
    
    private final String instance,yprocName;

    ParameterValueList pvList = new ParameterValueList(); 
    
    YProcessor yproc;
    int paramSubscriptionRequestId = -1;
    
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    /**
     * Constructs a Command Queue Manager having the given history manager and tc uplinker.
     *  The parameters have to be not null.
     * @param commandHistoryListener
     * @param commandReleaser
     * @throws ConfigurationException in case there is an error in the configuration file. 
     *         Note: if the configuration file doesn't exist, this exception is not thrown.
     */
    public CommandQueueManager(CommandingManager commandingManager) throws ConfigurationException {
        this.commandingManager=commandingManager;

        yproc=commandingManager.getChannel();
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+yproc.getName()+"]");
        this.commandHistoryListener = yproc.getCommandHistoryListener();
        this.commandReleaser = yproc.getCommandReleaser();
        this.instance = yproc.getInstance();
        this.yprocName = yproc.getName();

        
        CommandQueue cq=new CommandQueue(yproc, "default");
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
                queues.put(qn,new CommandQueue(yproc, qn));
            }
            CommandQueue q=queues.get(qn);
            String state=config.getString(qn, "state");
            q.state=CommandQueueManager.stringToQueueState(state);
            q.roles=config.getList(qn, "roles");
        }
    }

    /**
     * called at channel startup, subscribe all parameters required for checking command constraints
     */
    public void doStart() {
        XtceDb xtcedb = yproc.getXtceDb();
        Set<Parameter> paramsToSubscribe = new HashSet<Parameter>();
        for(MetaCommand mc: xtcedb.getMetaCommands()) {
            if(mc.hasTransmissionConstraints()) {
                List<TransmissionConstraint> tcList = mc.getTransmissionConstraintList();
                for(TransmissionConstraint tc: tcList) {
                    paramsToSubscribe.addAll(tc.getMatchCriteria().getDependentParameters());
                }
            }
        }
        if(!paramsToSubscribe.isEmpty()) {
            ParameterRequestManagerImpl prm = yproc.getParameterRequestManager();
            try {
                paramSubscriptionRequestId = prm.addRequest(new ArrayList<Parameter>(paramsToSubscribe), this);
            } catch (InvalidIdentification e) {
                log.warn("Got Invalid identification when subscribing to parameters for command constraint check",e);
                notifyFailed(e);
                return;
            }
        } else {
            log.debug("No parameter required for post transmission contraint check");
        }
        notifyStarted();
    }

    public void doStop() {
        if(paramSubscriptionRequestId!=-1) {
            ParameterRequestManagerImpl prm = yproc.getParameterRequestManager();
            prm.removeRequest(paramSubscriptionRequestId);
        }
        notifyStopped();
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
     * Depending on the status of the queue, the command is rejected by setting the CommandFailed in the command history 
     *  added to the queue or directly sent using the uplinker
     * 
     * @param pc
     */
    public synchronized void addCommand(PreparedCommand pc) {
        commandHistoryListener.addCommand(pc);

        CommandQueue q=getQueue(pc);
        if(q.state==QueueState.DISABLED) {
            failedCommand(q, pc, "Commanding Queue disabled", false);
        } else if(q.state==QueueState.BLOCKED) {
            q.commands.add(pc);
            notifyAdded(q, pc);
        } else if(q.state==QueueState.ENABLED) {
            if(pc.getMetaCommand().hasTransmissionConstraints()) {
                startTransmissionConstraintChecker(q, pc);
            } else {
                releaseCommand(q, pc, false, false);
            }
        }	
    }



    private void startTransmissionConstraintChecker(CommandQueue q, PreparedCommand pc) {
        q.commands.add(pc);
        notifyAdded(q, pc);
        TransmissionConstraintChecker constraintChecker = new TransmissionConstraintChecker(q, pc);
        pendingTcCheckers.add(constraintChecker);
        scheduleImmediateCheck(constraintChecker);
    }

    private void onTransmissionContraintCheckFinished(TransmissionConstraintChecker tcChecker) {
        PreparedCommand pc = tcChecker.pc;
        CommandQueue q = tcChecker.queue;
        TCStatus status = tcChecker.aggregateStatus;
        log.info("transmission constraint finished for "+pc.getCmdName()+" status: "+status);
        
        pendingTcCheckers.remove(tcChecker);
        

        if(q.getState()==QueueState.BLOCKED) {
            log.debug("Command queue for command "+pc+" is blocked, leaving command in the queue");
            return;
        }
        if(q.getState()==QueueState.DISABLED) {
            log.debug("Command queue for command "+pc+" is disabled, dropping command");
            q.remove(pc);
        }

        if(!q.remove(pc)) {
            return; //command has been removed in the meanwhile
        }
        if(status==TCStatus.OK) {
            addToCommandHistory(pc, CommandHistory.TransmissionContraints_KEY, "OK");
            releaseCommand(q, pc, true, false);
        } else if(status == TCStatus.TIMED_OUT) { 
            addToCommandHistory(pc, CommandHistory.TransmissionContraints_KEY, "NOK");
            failedCommand(q, pc, "Transmission constraints check failed",true);    
        }
    }

    //	Notify the monitoring clients
    private void notifyAdded(CommandQueue q, PreparedCommand pc) {
        for(CommandQueueListener m:monitoringClients) {
            try {
                m.commandAdded(q, pc);
            } catch (Exception e) {
                e.printStackTrace();
                log.warn("got exception "+e+" when notifying a monitor, removing it from the list");
                monitoringClients.remove(m);
            }
        }
    }

    private void addToCommandHistory(PreparedCommand pc, String key, String value) {
        try {
            commandHistoryListener.updateStringKey(pc.getCommandId(), key, value);
        } catch (InvalidCommandId e1) {
            log.warn("got invalid commandId when nacking command "+pc.getCommandId());
        }
    }
    /**
     * send a negative ack for a command.
     * @param pc the prepared command for which the negative ack is sent
     * @param notify notify or not the monitoring clients.
     */
    private void failedCommand(CommandQueue cq, PreparedCommand pc, String reason, boolean notify) {
        addToCommandHistory(pc, CommandHistory.CommandFailed_KEY, reason);
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

    private void releaseCommand(CommandQueue q, PreparedCommand pc, boolean notify, boolean rebuild) {
        if(rebuild) {
            /*		try {
				pc=commandingManager.buildCommand(pc.source, pc.getCommandId().toBuilder());
			} catch (YamcsException e) {
				log.warn("Got Exception for a command already in the queue: ", e);
				return;
			}*/
        }
        commandReleaser.releaseCommand(pc);
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
    public synchronized PreparedCommand rejectCommand(CommandId commandId, String username) {
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
            failedCommand(queue, pc, "Commmand rejected by "+username, true);
            queue.commands.remove(pc);
        } else {
            log.warn("command not found in any queue");
        }
        return pc;
    }

    /**
     * Called from external client to release a command from the queue
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
            releaseCommand(queue, command, true, rebuild);
        }
        return command;
    }

    /**
     * Called from external clients to change the state of the queue
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

        if(queue.state == newState) return queue;

        queue.state=newState;
        if(queue.state==QueueState.ENABLED) {
            for(PreparedCommand pc:queue.commands) {
                if(pc.getMetaCommand().hasTransmissionConstraints()) {
                    startTransmissionConstraintChecker(queue, pc);
                } else {
                    releaseCommand(queue, pc, true, false);
                }
            }
            queue.commands.clear();
        }
        if(queue.state==QueueState.DISABLED) {
            for(PreparedCommand pc:queue.commands) {
                failedCommand(queue, pc, "Commanding Queue disabled", true);
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
        return yprocName;
    }

    private void doUpdateItems(final List<ParameterValue> items) {
        pvList.addAll(items);
        //remove old parameter values
        for(ParameterValue pv:items) {
            Parameter p = pv.getParameter();
            int c = pvList.count(p);
            for(int i=0; i<c-1;i++) {
                pvList.removeFirst(p);
            }
        }
        for(TransmissionConstraintChecker tcc: pendingTcCheckers) {
            scheduleImmediateCheck(tcc);
        }
    }
    
    private void scheduleImmediateCheck(final TransmissionConstraintChecker tcc) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                tcc.check();
            }
        });
    }
    
    private void scheduleCheck(final TransmissionConstraintChecker tcc, long millisec) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                tcc.check();
            }
        }, millisec, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void updateItems(int subscriptionId, final List<ParameterValue> items) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                doUpdateItems(items);
            }});
    }


    static enum TCStatus {INIT, PENDING, OK, TIMED_OUT};

    class TransmissionConstraintChecker {
        List<TransmissionConstraintStatus> tcsList = new ArrayList<TransmissionConstraintStatus>();
        final PreparedCommand pc;
        final CommandQueue queue;
        TCStatus aggregateStatus=TCStatus.INIT;
        
        public TransmissionConstraintChecker(CommandQueue queue, PreparedCommand pc) {
            this.pc = pc;
            this.queue = queue;
            List<TransmissionConstraint> constraints = pc.getMetaCommand().getTransmissionConstraintList();
            for(TransmissionConstraint tc: constraints) {
                TransmissionConstraintStatus tcs = new TransmissionConstraintStatus(tc);
                tcsList.add(tcs);
            }
        }

        public void check() {
            long now = System.currentTimeMillis();
            if(aggregateStatus==TCStatus.INIT) {
                //make sure that if timeout=0, the first check will not appear to be too late
                for(TransmissionConstraintStatus tcs: tcsList) {
                    tcs.expirationTime = now + tcs.constraint.getTimeout();
                }
                aggregateStatus = TCStatus.PENDING;
            }
            
            if(aggregateStatus!=TCStatus.PENDING) return;
            ArrayList<ParameterValue> plist2 = new ArrayList<ParameterValue>(pvList);
            
           
            ComparisonProcessor cproc = new ComparisonProcessor(pvList);
            aggregateStatus = TCStatus.OK;
            long scheduleNextCheck = Long.MAX_VALUE;

            for(TransmissionConstraintStatus tcs: tcsList) {
                if(tcs.status == TCStatus.OK) continue;

                if(tcs.status == TCStatus.PENDING) {
                    long timeRemaining = tcs.expirationTime - now;
                    if(timeRemaining < 0) {
                        tcs.status = TCStatus.TIMED_OUT;
                        aggregateStatus = TCStatus.TIMED_OUT;
                        break;
                    } else {
                        MatchCriteria mc = tcs.constraint.getMatchCriteria();
                        try {
                        if(!cproc.matches(mc)) {
                            if(timeRemaining > 0) {
                                aggregateStatus = TCStatus.PENDING;
                                if(timeRemaining <scheduleNextCheck) {
                                    scheduleNextCheck = timeRemaining;
                                }
                            } else {
                                aggregateStatus = TCStatus.TIMED_OUT;
                                break;
                            }
                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        }
                    
                }
            }
            if(aggregateStatus == TCStatus.PENDING) {
               scheduleCheck(this, scheduleNextCheck);
            } else {
                onTransmissionContraintCheckFinished(this);
            }
        }
    }

    static class TransmissionConstraintStatus {
        TransmissionConstraint constraint;
        TCStatus status;
        long expirationTime;

        public TransmissionConstraintStatus(TransmissionConstraint tc) {
            this.constraint = tc;
            status = TCStatus.PENDING;
        }
    }


}