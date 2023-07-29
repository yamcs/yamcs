package org.yamcs.client;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.SubscribeCommandsRequest;

/**
 * Subscription for tracking issued commands, their attributes and acknowledgment status.
 */
public class CommandSubscription extends AbstractSubscription<SubscribeCommandsRequest, CommandHistoryEntry> {

    // Concurrency only between consumers and update mechanism.
    // We are not expecting (nor supporting) parallel updates.
    private Map<String, Command> commands = new ConcurrentHashMap<>();
    private Set<CommandListener> commandListeners = new CopyOnWriteArraySet<>();

    public CommandSubscription(MethodHandler methodHandler) {
        super(methodHandler, "commands", CommandHistoryEntry.class);
        addMessageListener(new MessageListener<CommandHistoryEntry>() {

            @Override
            public void onMessage(CommandHistoryEntry entry) {
                Instant generationTime = Helpers.toInstant(entry.getGenerationTime());
                Command command = commands.computeIfAbsent(entry.getId(), id -> new Command(entry.getId(),
                        entry.getCommandName(), entry.getAliasesMap(), entry.getAssignmentsList(), entry.getOrigin(),
                        entry.getSequenceNumber(), generationTime));
                command.merge(entry);
                commandListeners.forEach(l -> {
                    l.onUpdate(command);
                    l.onUpdate(command, entry);
                });
            }

            @Override
            public void onError(Throwable t) {
                commandListeners.forEach(l -> l.onError(t));
            }
        });
    }

    public void addListener(CommandListener listener) {
        commandListeners.add(listener);
    }

    public void removeListener(CommandListener listener) {
        commandListeners.remove(listener);
    }

    public void clear() {
        commands.clear();
    }

    public Command getCommand(String id) {
        return commands.get(id);
    }
}
