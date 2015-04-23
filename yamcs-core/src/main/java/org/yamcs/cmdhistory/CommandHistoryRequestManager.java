package org.yamcs.cmdhistory;


import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidCommandId;
import org.yamcs.archive.TcUplinkerAdapter;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Handles filtered requests for command history.
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
public class CommandHistoryRequestManager extends AbstractService implements StreamSubscriber {
    private ConcurrentHashMap<CommandId, CommandHistoryEntry> activeCommands=new ConcurrentHashMap<CommandId,CommandHistoryEntry>();
    private ConcurrentHashMap<CommandId, ConcurrentLinkedQueue<CommandHistoryConsumer>> cmdSubcriptions = new ConcurrentHashMap<CommandId ,ConcurrentLinkedQueue<CommandHistoryConsumer>>();
    private ConcurrentHashMap<CommandHistoryFilter,CommandHistoryConsumer> historySubcriptions = new ConcurrentHashMap<CommandHistoryFilter,CommandHistoryConsumer>();

    Stream realtimeCmdHistoryStream; 

    static AtomicInteger subscriptionIdGenerator=new AtomicInteger();
    final Logger log;
    //maps strings are requested in the getCommandHistory to strings as they appear in the commnad history
    AtomicInteger extendedId=new AtomicInteger();
    final String instance;


    public CommandHistoryRequestManager(String instance) throws ConfigurationException {
        this.instance=instance;
        if(instance!=null) {
            log=LoggerFactory.getLogger(this.getClass().getName()+"["+instance+"]");
        } else {
            log=LoggerFactory.getLogger(this.getClass().getName());
        }
    }



    /**
     * Add a consumer to the subscriber list for a command
     * @param cmdId
     * @param consumer
     * @return all the entries existing so far for the command
     * @throws InvalidCommandID
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
        CommandHistoryFilter filter=new CommandHistoryFilter(subscriptionIdGenerator.getAndIncrement(), commandsOrigin, commandsSince);
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
    private void addCommand(PreparedCommand pc) {
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
     * @throws InvalidCommandID the command does not appear in the activeCommands hash
     *  
     */
    private void updateCommand(CommandId cmdId, String key, Value value) throws InvalidCommandId {
        log.debug("updateCommand cmdId={} key={} value={}", new Object[]{cmdId, key, value});
        CommandHistoryEntry che=activeCommands.get(cmdId);
        if(che==null) {
            log.error("received an update for a command not in my active list: "+cmdId);
            throw new InvalidCommandId("received an update for a command not in my active list: "+cmdId      );
        }

        CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(key).setValue(value).build();
        CommandHistoryEntry che1 = CommandHistoryEntry.newBuilder(che).addAttr(cha).build();
        activeCommands.put(cmdId, che1);


        long changeDate=TimeEncoding.currentInstant();
        for(Iterator<CommandHistoryFilter> it=historySubcriptions.keySet().iterator();it.hasNext();) {
            CommandHistoryFilter filter=it.next();
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
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        Stream realtimeStream = ydb.getStream(YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
        if(realtimeStream == null) {
            String msg ="Cannot find stream '"+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+" in instance "+instance; 
            log.error(msg);
            notifyFailed(new ConfigurationException(msg));
        } else {
            realtimeStream.addSubscriber(this);
            notifyStarted();
        }


    }



    @Override
    protected void doStop() {
        realtimeCmdHistoryStream.removeSubscriber(this);
        notifyStopped();
    }

    @Override
    public void onTuple(Stream s, Tuple tuple) {
        if(tuple.hasColumn("source")) {
            PreparedCommand pc=PreparedCommand.fromTuple(tuple);
            addCommand(pc);
        } else {
            int i=TcUplinkerAdapter.TC_TUPLE_DEFINITION.getColumnDefinitions().size();
            CommandId cmdId=PreparedCommand.getCommandId(tuple);
            List<ColumnDefinition> columns=tuple.getDefinition().getColumnDefinitions();
            while(i<columns.size()) {
                ColumnDefinition cd=columns.get(i++);
                String key=cd.getName();
                try {
                    Value v = ValueUtility.getColumnValue(cd, tuple.getColumn(key));
                    updateCommand(cmdId, key, v);
                } catch (InvalidCommandId e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void streamClosed(Stream s) {
    }
}
