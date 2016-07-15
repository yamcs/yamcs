package org.yamcs.cmdhistory;


import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YProcessor;
import org.yamcs.commanding.InvalidCommandId;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.Stream;

import com.google.common.util.concurrent.AbstractService;

/**
 * Part of processors: handles filtered requests for command history.
 * 
 *  We handle two kind of subscriptions:
 *   - subscription to specific commands
 *   - subscription to all the commands but filtered on source and time.
 *   
 *   TODO 1.0: when/how the commands should be removed from the activelist? Also clean the cmdSubcriptions
 *   
 *   It receives commands from the cmd_history stream
 *   
 * @author nm
 *
 */
public class CommandHistoryRequestManager extends AbstractService {
    private ConcurrentHashMap<CommandId, CommandHistoryEntry> activeCommands=new ConcurrentHashMap<CommandId,CommandHistoryEntry>();
    private ConcurrentHashMap<CommandId, ConcurrentLinkedQueue<CommandHistoryConsumer>> cmdSubcriptions = new ConcurrentHashMap<CommandId ,ConcurrentLinkedQueue<CommandHistoryConsumer>>();
    private ConcurrentHashMap<CommandHistoryFilter,CommandHistoryConsumer> historySubcriptions = new ConcurrentHashMap<CommandHistoryFilter,CommandHistoryConsumer>();

    Stream realtimeCmdHistoryStream; 

    static AtomicInteger subscriptionIdGenerator=new AtomicInteger();
    final Logger log;
    //maps strings are requested in the getCommandHistory to strings as they appear in the commnad history
    AtomicInteger extendedId=new AtomicInteger();
    final String instance;
    final YProcessor processor;

    public CommandHistoryRequestManager(YProcessor processor) throws ConfigurationException {
        this.processor = processor;
        this.instance = processor.getInstance();
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+processor.getName()+"]");
    }

    /**
     * Add a consumer to the subscriber list for a command
     * @param cmdId
     * @param consumer
     * @return all the entries existing so far for the command
     * @throws InvalidCommandId
     */
    public CommandHistoryEntry subscribeCommand(CommandId cmdId, CommandHistoryConsumer consumer) throws InvalidCommandId {
        CommandHistoryEntry che=activeCommands.get(cmdId);
        if(che!=null) {
            cmdSubcriptions.putIfAbsent(cmdId, new ConcurrentLinkedQueue<CommandHistoryConsumer>());
            cmdSubcriptions.get(cmdId).add(consumer);
            return che;
        }
        log.warn("Received subscribe command for a command not in my active list: ("+cmdId+")");
        throw new InvalidCommandId("command "+cmdId+" is not in the list of active commands",cmdId);
        
    }


    /**
     * removes a consumer from the subscribers for a command (if existing).
     * @param cmdId
     * @param consumer
     */
    public void unsubscribeCommand(CommandId cmdId, CommandHistoryConsumer consumer) {
        CommandHistoryEntry che=activeCommands.get(cmdId);
        if(che!=null) {
            ConcurrentLinkedQueue<CommandHistoryConsumer> l=cmdSubcriptions.get(che);
            if(l!=null)
                l.remove(consumer);
        }
    }

    /**
     * Called by the CommandHistory consumers when they want to receive all updates corresponding
     *  to a command.
     * @param commandsOrigin
     * @param commandsSince
     * @param consumer
     * @return
     */
    public int subscribeCommandHistory(String commandsOrigin, long commandsSince, CommandHistoryConsumer consumer) {
        log.debug("commandsOrigin={}", commandsOrigin);
        CommandHistoryFilter filter = new CommandHistoryFilter(subscriptionIdGenerator.getAndIncrement(), commandsOrigin, commandsSince);
        historySubcriptions.put(filter,consumer);
        return filter.subscriptionId;
    }
    /**
     * Called by the CommandHistory consumers to remove the subscription
     * @param id
     */
    public CommandHistoryFilter unsubscribeCommandHistory(int id) {
        for (CommandHistoryFilter f:historySubcriptions.keySet()) {
            if(f.subscriptionId==id) {
                historySubcriptions.remove(f);
                return f;
            }
        }
        return null;
    }

    /**
     * Called by the CommandHistoryImpl to move the subscription from another command history manager to this one
     * @param filter
     */
    public void addSubscription(CommandHistoryFilter filter, CommandHistoryConsumer consumer) {
        historySubcriptions.put(filter, consumer);

    }



    /**
     * Called when a new command has to be added to the command history 
     *  (i.e. when a users sends a telecommand)
     */
    public void addCommand(PreparedCommand pc) {
        log.debug("addCommand cmdId="+pc);
        CommandHistoryEntry che = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId()).build();
        
        //deliver to clients
        for(Iterator<CommandHistoryFilter> it=historySubcriptions.keySet().iterator(); it.hasNext();) {
            CommandHistoryFilter filter=it.next();
            if(filter.matches(che)){
                historySubcriptions.get(filter).addedCommand(pc);
            }
        }
        
        activeCommands.put(pc.getCommandId(), che);
    }



    /**
     * send updates.
     * @param cmdId 
     * @param key 
     * @param value 
     * @throws InvalidCommandId the command does not appear in the activeCommands hash
     *  
     */
    public void updateCommand(CommandId cmdId, String key, Value value) throws InvalidCommandId {
        log.debug("updateCommand cmdId={} key={} value={}", new Object[]{cmdId, key, value});
        CommandHistoryEntry che=activeCommands.get(cmdId);
        if(che==null) {
            // If the commandId is valid, add the command in the active list, this case happens if an old command history is updated.
            che = CommandHistoryEntry.newBuilder().setCommandId(cmdId).build();
        }

        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(key).setValue(ValueUtility.toGbp(value)).build();
        CommandHistoryEntry che1 = CommandHistoryEntry.newBuilder(che).addAttr(cha).build();
        activeCommands.put(cmdId, che1);


        long changeDate = processor.getCurrentTime();
        for(Iterator<CommandHistoryFilter> it=historySubcriptions.keySet().iterator();it.hasNext();) {
            CommandHistoryFilter filter = it.next();
            if(filter.matches(che)){
                historySubcriptions.get(filter).updatedCommand(cmdId, changeDate, key, value);
            }
        }
        ConcurrentLinkedQueue<CommandHistoryConsumer> consumers=cmdSubcriptions.get(cmdId);

        if(consumers!=null) {
            for(Iterator<CommandHistoryConsumer> it=consumers.iterator();it.hasNext();) {
                it.next().updatedCommand(cmdId, changeDate, key, value);
            }
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }



    @Override
    protected void doStop() {
        notifyStopped();
    }



    public String getInstance() {
        return instance;
    }

}
