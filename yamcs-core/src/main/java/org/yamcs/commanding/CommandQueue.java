package org.yamcs.commanding;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
import org.yamcs.security.User;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.SystemParameter;

public class CommandQueue {

    private String name;
    private Set<String> users = new HashSet<>();
    private Set<String> groups = new HashSet<>();
    private Levels minLevel = Levels.none;

    private ConcurrentLinkedQueue<ActiveCommand> commands = new ConcurrentLinkedQueue<>();
    QueueState defaultState;
    QueueState state;
    Processor processor;

    int nbSentCommands = 0;
    int nbRejectedCommands = 0;
    int stateExpirationTimeS = 0;

    int stateExpirationRemainingS = -1;
    ScheduledFuture<?> stateExpirationJob = null;

    SystemParameter spQueueState, spNumSentCommands, spNumRejectedCommands, spNumCommands;

    CommandQueue(Processor channel, String name, QueueState state) {
        this.processor = channel;
        this.name = name;
        this.state = state;
        this.defaultState = state;
    }

    void setupSysParameters() {
        SystemParametersService sps = SystemParametersService.getInstance(processor.getInstance());
        spQueueState = sps.createSystemParameter("cmdQueue/" + name + "/state", Type.ENUMERATED);
        spNumCommands = sps.createSystemParameter("cmdQueue/" + name + "/numCommands", Type.SINT32);
        spNumSentCommands = sps.createSystemParameter("cmdQueue/" + name + "/numSentCommands", Type.UINT32);
        spNumRejectedCommands = sps.createSystemParameter("cmdQueue/" + name + "/numRejectedCommands", Type.UINT32);
    }

    public String getName() {
        return name;
    }

    public Set<String> getUsers() {
        return users;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public Levels getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(Levels minLevel) {
        this.minLevel = minLevel;
    }

    public void addUsers(Collection<String> users) {
        this.users.addAll(users);
    }

    public void addGroups(Collection<String> groups) {
        this.groups.addAll(groups);
    }

    public boolean matches(User user, MetaCommand metaCmd) {
        if (!isUserMatched(user)) {
            return false;
        }

        Levels level = Levels.none;
        if (metaCmd.getDefaultSignificance() != null) {
            level = metaCmd.getDefaultSignificance().getConsequenceLevel();
        }
        if (!isLevelMatched(level)) {
            return false;
        }

        return true;
    }

    private boolean isUserMatched(User user) {
        if (users.isEmpty() && groups.isEmpty()) {
            return true;
        }

        if (users.contains(user.getName())) {
            return true;
        }

        Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();
        for (Group group : directory.getGroups(user)) {
            if (groups.contains(group.getName())) {
                return true;
            }
        }

        return false;
    }

    private boolean isLevelMatched(Levels level) {
        return minLevel == level || level.isMoreSevere(minLevel);
    }

    public QueueState getState() {
        return state;
    }

    public Processor getProcessor() {
        return processor;
    }

    public PreparedCommand[] getCommandArray() {
        return commands.toArray(new PreparedCommand[0]);
    }

    public int getCommandCount() {
        return commands.size();
    }

    public void add(ActiveCommand pc) {
        commands.add(pc);
    }

    /**
     * remove the command from the queue and return true if it has been removed
     * 
     * @param pc
     * @param isSent:
     *            true if the command has been sent, false if the command has been rejected
     * @return
     */
    public boolean remove(ActiveCommand pc, boolean isSent) {
        boolean removed = commands.remove(pc);
        if (removed) {
            if (isSent) {
                nbSentCommands++;
            } else {
                nbRejectedCommands++;
            }
        }
        return removed;
    }

    public void clear(boolean areSent) {
        int nbCommands = commands.size();
        commands.clear();
        if (areSent) {
            nbSentCommands += nbCommands;
        } else {
            nbRejectedCommands += nbCommands;
        }
    }

    public int getNbRejectedCommands() {
        return nbRejectedCommands;
    }

    public int getStateExpirationRemainingS() {
        return stateExpirationRemainingS;
    }

    public int getNbSentCommands() {
        return nbSentCommands;
    }

    void fillInSystemParameters(List<ParameterValue> params, long time) {
        params.add(SystemParametersService.getPV(spQueueState, time, state.name()));
        params.add(SystemParametersService.getPV(spNumCommands, time, commands.size()));
        params.add(SystemParametersService.getPV(spNumSentCommands, time, nbSentCommands));
        params.add(SystemParametersService.getPV(spNumRejectedCommands, time, nbRejectedCommands));
    }

    public ActiveCommand getcommand(CommandId commandId) {
        for (ActiveCommand c : commands) {
            if (c.getCommandId().equals(commandId)) {
                return c;
            }
        }
        return null;
    }

    public ActiveCommand getcommand(UUID uuid) {
        for (ActiveCommand c : commands) {
            if (c.preparedCommand.getUUID().equals(uuid)) {
                return c;
            }
        }
        return null;
    }

    public ConcurrentLinkedQueue<ActiveCommand> getCommands() {
        return commands;
    }
}
