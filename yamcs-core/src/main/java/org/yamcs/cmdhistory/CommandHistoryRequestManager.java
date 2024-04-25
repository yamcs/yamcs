package org.yamcs.cmdhistory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.commanding.InvalidCommandId;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.Stream;

import com.google.common.util.concurrent.AbstractService;

/**
 * Part of processors: handles filtered requests for command history.
 * 
 * We handle two kind of subscriptions:
 * <ul>
 * <li>subscription to specific commands
 * <li>subscription to all the commands but filtered on source and time.
 * </ul>
 * 
 * It receives commands from the cmd_history stream
 * 
 * @author nm
 *
 */
public class CommandHistoryRequestManager extends AbstractService {
    private ConcurrentHashMap<CommandId, CommandHistoryEntry> activeCommands = new ConcurrentHashMap<>();
    private ConcurrentHashMap<CommandId, ConcurrentLinkedQueue<CommandHistoryConsumer>> cmdSubcriptions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<CommandHistoryFilter, CommandHistoryConsumer> historySubcriptions = new ConcurrentHashMap<>();

    // once the command becomes inactive, remove it from the activeCommands map after this number of seconds
    static final int REMOVAL_TIME = 30;

    Stream realtimeCmdHistoryStream;

    static AtomicInteger subscriptionIdGenerator = new AtomicInteger();
    final Log log;
    AtomicInteger extendedId = new AtomicInteger();
    final String instance;
    final Processor processor;

    public CommandHistoryRequestManager(Processor processor) throws ConfigurationException {
        this.processor = processor;
        this.instance = processor.getInstance();
        log = new Log(this.getClass(), instance);
        log.setContext(processor.getName());
    }

    /**
     * Add a consumer to the subscriber list for a command
     * 
     * @param cmdId
     * @param consumer
     * @return all the entries existing so far for the command
     * @throws InvalidCommandId
     */
    public org.yamcs.protobuf.Commanding.CommandHistoryEntry subscribeCommand(CommandId cmdId,
            CommandHistoryConsumer consumer) throws InvalidCommandId {
        CommandHistoryEntry che = activeCommands.get(cmdId);
        if (che != null) {
            cmdSubcriptions.putIfAbsent(cmdId, new ConcurrentLinkedQueue<CommandHistoryConsumer>());
            cmdSubcriptions.get(cmdId).add(consumer);
            return che.toProto();
        }
        log.warn("Received subscribe command for a command not in my active list: ({})", cmdId);
        throw new InvalidCommandId("command " + cmdId + " is not in the list of active commands", cmdId);
    }

    /**
     * removes a consumer from the subscribers for a command (if existing).
     * 
     * @param cmdId
     * @param consumer
     */
    public void unsubscribeCommand(CommandId cmdId, CommandHistoryConsumer consumer) {
        ConcurrentLinkedQueue<CommandHistoryConsumer> l = cmdSubcriptions.get(cmdId);
        if (l != null) {
            l.remove(consumer);
        }
    }

    /**
     * Called by the CommandHistory consumers when they want to receive all updates corresponding to a command.
     */
    public CommandHistoryFilter subscribeCommandHistory(String commandsOrigin, long commandsSince,
            CommandHistoryConsumer consumer) {
        log.debug("commandsOrigin={}", commandsOrigin);
        CommandHistoryFilter filter = new CommandHistoryFilter(subscriptionIdGenerator.getAndIncrement(),
                commandsOrigin, commandsSince);
        historySubcriptions.put(filter, consumer);
        return filter;
    }

    /**
     * Called by the CommandHistory consumers to remove the subscription
     * 
     * @param id
     */
    public CommandHistoryFilter unsubscribeCommandHistory(int id) {
        for (CommandHistoryFilter f : historySubcriptions.keySet()) {
            if (f.subscriptionId == id) {
                historySubcriptions.remove(f);
                return f;
            }
        }
        return null;
    }

    /**
     * Called by the CommandHistoryImpl to move the subscription from another command history manager to this one
     * 
     * @param filter
     */
    public void addSubscription(CommandHistoryFilter filter, CommandHistoryConsumer consumer) {
        historySubcriptions.put(filter, consumer);

    }

    /**
     * Called when a new command has to be added to the command history (i.e. when a users sends a telecommand)
     */
    public void addCommand(PreparedCommand pc) {
        if (activeCommands.containsKey(pc.getCommandId())) {
            // this happens since Yamcs 5.4.4 - the StreamCommandHistoryProvider will send the command here but also
            // comes directly from the command queue manager
            return;
        }
        log.debug("addCommand cmdId={}", pc);
        CommandHistoryEntry che = new CommandHistoryEntry(pc.getCommandId());

        // deliver to clients
        for (Iterator<CommandHistoryFilter> it = historySubcriptions.keySet().iterator(); it.hasNext();) {
            CommandHistoryFilter filter = it.next();
            if (filter.matches(che)) {
                historySubcriptions.get(filter).addedCommand(pc);
            }
        }

        activeCommands.put(pc.getCommandId(), che);
    }

    /**
     * send updates.
     * 
     * @param cmdId
     * @param attrs
     * 
     */
    public void updateCommand(CommandId cmdId, List<Attribute> attrs)  {
        log.debug("updateCommand cmdId: {} attrs: {}", attrs);
        CommandHistoryEntry che = activeCommands.get(cmdId);
        if (che == null) {
            // If the commandId is valid, add the command in the active list, this case happens if an old command
            // history is updated.
            che = new CommandHistoryEntry(cmdId);
            activeCommands.put(cmdId, che);
        }
        che.updateCommand(attrs);


        long changeDate = processor.getCurrentTime();
        for (Iterator<CommandHistoryFilter> it = historySubcriptions.keySet().iterator(); it.hasNext();) {
            CommandHistoryFilter filter = it.next();
            if (filter.matches(che)) {
                historySubcriptions.get(filter).updatedCommand(cmdId, changeDate, attrs);
            }
        }
        ConcurrentLinkedQueue<CommandHistoryConsumer> consumers = cmdSubcriptions.get(cmdId);

        if (consumers != null) {
            for (Iterator<CommandHistoryConsumer> it = consumers.iterator(); it.hasNext();) {
                it.next().updatedCommand(cmdId, changeDate, attrs);
            }
        }
    }

    /**
     * Called when there can be no more events for this command.
     * <p>
     * We remove it from the active commands only after a few seconds because some tools may subscribe to it after
     * being sent and if there was no verifier, the command would immediately disappear so the subscription would fail.
     */
    public void commandFinished(CommandId cmdId) {
        processor.getTimer().schedule(() -> {
            activeCommands.remove(cmdId);
            cmdSubcriptions.remove(cmdId);
        }, REMOVAL_TIME, TimeUnit.SECONDS);
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

    static class CommandHistoryEntry {
        final CommandId cmdId;
        Map<String, Value> attributes = new HashMap<>();

        public CommandHistoryEntry(CommandId cmdId) {
            this.cmdId = cmdId;
        }

        public synchronized void updateCommand(List<Attribute> attrs) {
            for (var a : attrs) {
                attributes.put(a.key, a.value);
            }
        }

        public synchronized org.yamcs.protobuf.Commanding.CommandHistoryEntry toProto() {
            var cheb = org.yamcs.protobuf.Commanding.CommandHistoryEntry.newBuilder().setCommandId(cmdId);

            for (var me : attributes.entrySet()) {
                CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(me.getKey())
                        .setValue(ValueUtility.toGbp(me.getValue())).build();
                cheb.addAttr(cha).build();
            }
            return cheb.build();
        }

        public CommandId getCommandId() {
            return cmdId;
        }
    }
}
