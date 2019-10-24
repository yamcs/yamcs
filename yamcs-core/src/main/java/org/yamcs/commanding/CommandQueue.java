package org.yamcs.commanding;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
import org.yamcs.security.User;
import org.yamcs.xtce.Significance.Levels;

public class CommandQueue {

    private String name;
    private Set<String> users = new HashSet<>();
    private Set<String> groups = new HashSet<>();
    private Levels minLevel = Levels.none;

    private ConcurrentLinkedQueue<PreparedCommand> commands = new ConcurrentLinkedQueue<>();
    QueueState defaultState;
    QueueState state;
    Processor processor;

    int nbSentCommands = 0;
    int nbRejectedCommands = 0;
    int stateExpirationTimeS = 0;

    int stateExpirationRemainingS = -1;
    ScheduledFuture<?> stateExpirationJob = null;

    String spQueueState;
    String spNumSentCommands;
    String spNumRejectedCommands;
    String spNumCommands;

    CommandQueue(Processor channel, String name, QueueState state) {
        this.processor = channel;
        this.name = name;
        this.state = state;
        this.defaultState = state;
    }

    void setupSysParameters() {
        SystemParametersCollector sysParamCollector = SystemParametersCollector.getInstance(processor.getInstance());
        spQueueState = sysParamCollector.getNamespace() + "/cmdQueue/" + name + "/state";
        spNumCommands = sysParamCollector.getNamespace() + "/cmdQueue/" + name + "/numCommands";
        spNumSentCommands = sysParamCollector.getNamespace() + "/cmdQueue/" + name + "/numSentCommands";
        spNumRejectedCommands = sysParamCollector.getNamespace() + "/cmdQueue/" + name + "/numRejectedCommands";
    }

    public ConcurrentLinkedQueue<PreparedCommand> getCommands() {
        return commands;
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

    public boolean matches(User user, PreparedCommand pc) {
        if (!isUserMatched(user)) {
            return false;
        }

        Levels level = Levels.none;
        if (pc.getMetaCommand().getDefaultSignificance() != null) {
            level = pc.getMetaCommand().getDefaultSignificance().getConsequenceLevel();
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

    public boolean contains(PreparedCommand pc) {
        return commands.contains(pc);
    }

    public void add(PreparedCommand pc) {
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
    public boolean remove(PreparedCommand pc, boolean isSent) {
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
        params.add(SystemParametersCollector.getPV(spQueueState, time, state.name()));
        params.add(SystemParametersCollector.getPV(spNumCommands, time, commands.size()));
        params.add(SystemParametersCollector.getPV(spNumSentCommands, time, nbSentCommands));
        params.add(SystemParametersCollector.getPV(spNumRejectedCommands, time, nbRejectedCommands));
    }
}
