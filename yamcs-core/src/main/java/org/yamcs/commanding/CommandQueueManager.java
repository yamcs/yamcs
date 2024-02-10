package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.GuardedBy;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ThreadSafe;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.logging.Log;
import org.yamcs.mdb.MatchCriteriaEvaluator;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.mdb.MatchCriteriaEvaluatorFactory;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.memento.MementoDb;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.security.User;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.TransmissionConstraint;

import com.google.common.util.concurrent.AbstractService;

/**
 * Implements the management of the control queues for one processor:
 * <ul>
 * <li>for each command that is sent, based on the sender it finds the queue where the command should go
 * <li>depending on the queue state the command can be immediately sent, stored in the queue or rejected
 * <li>when the command is immediately sent or rejected, the command queue monitor is not notified
 * <li>if the command has transmissionConstraints with timeout &gt; 0, the command can sit in the queue even if the
 * queue is not on hold
 * </ul>
 * Note: the update of the command monitors is done in the same thread. That means that if the connection to one of the
 * monitors is lost, there may be a delay of a few seconds. As the monitoring clients will be priviledged users most
 * likely connected in the same LAN, I don't consider this to be an issue.
 */
@ThreadSafe
public class CommandQueueManager extends AbstractService implements ParameterProcessor, SystemParametersProducer {
    private static final String MEMENTO_KEY = "yamcs.queues";

    @GuardedBy("this")
    private HashMap<String, CommandQueue> queues = new LinkedHashMap<>();

    CommandHistoryPublisher commandHistoryPublisher;
    CommandingManager commandingManager;
    ConcurrentLinkedQueue<CommandQueueListener> monitoringClients = new ConcurrentLinkedQueue<>();
    private final Log log;

    private Set<TransmissionConstraintChecker> pendingTcCheckers = new HashSet<>();

    private final String instance;
    private final String processorName;

    Processor processor;

    private final ScheduledThreadPoolExecutor timer;

    private TimeService timeService;

    /**
     * Constructs a Command Queue Manager.
     * 
     * @param commandingManager
     *
     * @throws ConfigurationException
     *             When there is an error in the configuration file. Note: if the configuration file doesn't exist, this
     *             exception is not thrown.
     * @throws ValidationException
     *             When configuration file is incorrect.
     */
    public CommandQueueManager(CommandingManager commandingManager) throws ConfigurationException, ValidationException {
        this.commandingManager = commandingManager;

        processor = commandingManager.getProcessor();
        log = new Log(this.getClass(), processor.getInstance());
        log.setContext(processor.getName());
        this.commandHistoryPublisher = processor.getCommandHistoryPublisher();

        this.instance = processor.getInstance();
        this.processorName = processor.getName();
        this.timer = processor.getTimer();
        timeService = YamcsServer.getTimeService(processor.getInstance());

        var mementoDb = MementoDb.getInstance(instance);
        var memento = mementoDb.getObject(MEMENTO_KEY, CommandQueueMemento.class)
                .orElse(new CommandQueueMemento());

        if (YConfiguration.isDefined("command-queue")) {
            Spec queueSpec = getQueueSpec();
            YConfiguration config = YConfiguration.getConfiguration("command-queue");
            for (String queueName : config.getKeys()) {
                YConfiguration queueConfig = config.getConfigOrEmpty(queueName);
                queueConfig = queueSpec.validate(queueConfig);

                var state = computeInitialState(queueName, queueConfig, memento);
                CommandQueue q = new CommandQueue(processor, queueName, state);

                if (queueConfig.containsKey("users")) {
                    q.addUsers(queueConfig.getList("users"));
                }
                if (queueConfig.containsKey("groups")) {
                    q.addGroups(queueConfig.getList("groups"));
                }
                if (queueConfig.containsKey("minLevel")) {
                    Levels minLevel = Levels.valueOf(queueConfig.getString("minLevel").toUpperCase());
                    q.setMinLevel(minLevel);
                }
                if (queueConfig.containsKey("tcPatterns")) {
                    var regexes = queueConfig.<String> getList("tcPatterns");
                    var patterns = regexes.stream().map(Pattern::compile).collect(Collectors.toList());
                    q.addTcPatterns(patterns);
                }
                queues.put(queueName, q);
            }
        } else {
            var defaultQueueName = "default";
            var state = computeInitialState(defaultQueueName, YConfiguration.emptyConfig(), memento);
            var queue = new CommandQueue(processor, defaultQueueName, state);
            queues.put(queue.getName(), queue);
        }
    }

    /**
     * Determines the initial state for a specific queue.
     * 
     * If an explicit state is configured, always use that. Else restore state from a previous run, defaulting to
     * {@link QueueState#ENABLED}.
     */
    private QueueState computeInitialState(String queueName, YConfiguration queueConfig, CommandQueueMemento memento) {
        // If an explicit state is configured, use that.
        // Else restore state from a previous run, defaulting to ENABLED.
        var state = QueueState.ENABLED;
        if (queueConfig.containsKey("state")) {
            var stateString = queueConfig.getString("state");
            state = stringToQueueState(stateString);
        } else {
            var queueState = memento.getCommandQueueState(queueName);
            if (queueState != null) {
                state = queueState.getState();
            }
        }
        return state;
    }

    private Spec getQueueSpec() {
        Spec spec = new Spec();
        spec.addOption("state", OptionType.STRING).withChoices("enabled", "blocked", "disabled");
        spec.addOption("minLevel", OptionType.STRING);
        spec.addOption("users", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("groups", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("tcPatterns", OptionType.LIST).withElementType(OptionType.STRING);
        return spec;
    }

    /**
     * called at processor startup
     */
    @Override
    public void doStart() {
        var sysParamCollector = SystemParametersService.getInstance(processor.getInstance());
        if (sysParamCollector != null) {
            for (CommandQueue cq : queues.values()) {
                cq.setupSysParameters();
            }
            sysParamCollector.registerProducer(this);
        }

        notifyStarted();
    }

    @Override
    public void doStop() {
        var sysParamCollector = SystemParametersService.getInstance(processor.getInstance());
        if (sysParamCollector != null) {
            sysParamCollector.unregisterProducer(this);
        }
        notifyStopped();
    }

    private static QueueState stringToQueueState(String state) throws ConfigurationException {
        if ("enabled".equalsIgnoreCase(state)) {
            return QueueState.ENABLED;
        }
        if ("disabled".equalsIgnoreCase(state)) {
            return QueueState.DISABLED;
        }
        if ("blocked".equalsIgnoreCase(state)) {
            return QueueState.BLOCKED;
        }
        throw new ConfigurationException(
                "'" + state + "' is not a valid queue state. Use one of enabled, disabled or blocked");
    }

    public List<CommandQueue> getQueues() {
        return new ArrayList<>(queues.values());
    }

    public CommandQueue getQueue(String name) {
        return queues.get(name);
    }

    /**
     * Called from the CommandingImpl to add a command to the queue.
     * <p>
     * First the command is added to the command history. Depending on the status of the queue, the command is rejected
     * by setting the CommandFailed in the command history added to the queue or directly sent using the command
     * releaser.
     * 
     * @param user
     * @param activeCommand
     * @return the queue the command was added to
     */
    public synchronized CommandQueue addCommand(User user, ActiveCommand activeCommand) {
        commandHistoryPublisher.addCommand(activeCommand.getPreparedCommand());

        long missionTime = timeService.getMissionTime();

        CommandQueue q = getQueue(user, activeCommand.getPreparedCommand());
        if (q == null) {
            log.warn("No queue available for command {}", activeCommand.getLoggingId());
            commandHistoryPublisher.publishAck(activeCommand.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeQueued_KEY,
                    missionTime, AckStatus.NOK, "No queue available");
            unhandledCommand(activeCommand);
            return null;
        }
        log.debug("Adding command {} to queue {}; queue state: {}", activeCommand.getLoggingId(), q.getName(),
                q.getState());
        q.add(activeCommand);
        notifyAdded(q, activeCommand);

        commandHistoryPublisher.publish(activeCommand.getCommandId(), CommandHistoryPublisher.Queue_KEY, q.getName());

        if (q.state == QueueState.DISABLED) {
            q.remove(activeCommand, false);
            commandHistoryPublisher.publishAck(activeCommand.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeQueued_KEY,
                    missionTime, AckStatus.NOK, "Queue disabled");
            failedCommand(q, activeCommand, "Queue disabled", true);
            notifyUpdateQueue(q);
        } else if (q.state == QueueState.BLOCKED) {
            commandHistoryPublisher.publishAck(activeCommand.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeQueued_KEY,
                    missionTime, AckStatus.OK);
            // notifyAdded(q, pc);
        } else if (q.state == QueueState.ENABLED) {
            commandHistoryPublisher.publishAck(activeCommand.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeQueued_KEY,
                    missionTime, AckStatus.OK);
            preReleaseCommand(q, activeCommand);

        }

        return q;
    }

    // if there are transmission constraints, start the checker;
    // if not just release the command
    private void preReleaseCommand(CommandQueue q, ActiveCommand pc) {
        long missionTime = timeService.getMissionTime();
        if (pc.getMetaCommand().hasTransmissionConstraints() && !pc.disableTransmissionConstraints()) {
            startTransmissionConstraintChecker(q, pc);
        } else {
            commandHistoryPublisher.publishAck(pc.getCommandId(),
                    CommandHistoryPublisher.TransmissionConstraints_KEY, missionTime, AckStatus.NA);
            q.remove(pc, true);
            releaseCommand(q, pc, true);
            commandHistoryPublisher.publishAck(pc.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeReleased_KEY, missionTime, AckStatus.OK);
        }
    }

    private void startTransmissionConstraintChecker(CommandQueue q, ActiveCommand pc) {
        TransmissionConstraintChecker constraintChecker = new TransmissionConstraintChecker(q, pc);
        pendingTcCheckers.add(constraintChecker);
        constraintChecker.checkImmediate();
    }

    private void onTransmissionConstraintCheckPending(TransmissionConstraintChecker tcChecker) {
        tcChecker.activeCommand.setPendingTransmissionConstraints(true);
        notifyUpdated(tcChecker.queue, tcChecker.activeCommand);
        commandHistoryPublisher.publishAck(tcChecker.activeCommand.getCommandId(),
                CommandHistoryPublisher.TransmissionConstraints_KEY, timeService.getMissionTime(), AckStatus.PENDING);
    }

    private void onTransmissionConstraintCheckFinished(TransmissionConstraintChecker tcChecker) {
        ActiveCommand pc = tcChecker.activeCommand;
        pc.setPendingTransmissionConstraints(false);
        CommandQueue q = tcChecker.queue;
        TCStatus status = tcChecker.aggregateStatus;
        log.info("transmission constraint finished for {} status: {}", pc.getCmdName(), status);
        long missionTime = timeService.getMissionTime();
        tcChecker.unsubscribe();

        pendingTcCheckers.remove(tcChecker);

        if (status == TCStatus.OK) {
            q.remove(pc, true);
            commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.TransmissionConstraints_KEY,
                    missionTime, AckStatus.OK);
            releaseCommand(q, pc, true);
            commandHistoryPublisher.publishAck(pc.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeReleased_KEY, missionTime, AckStatus.OK);
        } else if (status == TCStatus.TIMED_OUT) {
            q.remove(pc, false);
            commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.TransmissionConstraints_KEY,
                    missionTime, AckStatus.NOK);
            commandHistoryPublisher.publishAck(pc.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeReleased_KEY, missionTime, AckStatus.NOK,
                    "Transmission constraints check failed");
            failedCommand(q, pc, "Transmission constraints check failed", true);
        }
    }

    // Notify the monitoring clients
    private void notifyAdded(CommandQueue q, ActiveCommand activeCommand) {
        for (CommandQueueListener m : monitoringClients) {
            try {
                m.commandAdded(q, activeCommand);
            } catch (Exception e) {
                log.warn("got exception when notifying a monitor, removing it from the list", e);
                monitoringClients.remove(m);
            }
        }
        notifyUpdateQueue(q);
    }

    private void notifyUpdated(CommandQueue q, ActiveCommand activeCommand) {
        for (CommandQueueListener m : monitoringClients) {
            try {
                m.commandUpdated(q, activeCommand);
            } catch (Exception e) {
                log.warn("got exception when notifying a monitor, removing it from the list", e);
                monitoringClients.remove(m);
            }
        }
    }

    // Notify the monitoring clients
    private void notifySent(CommandQueue q, ActiveCommand activeCommand) {
        for (CommandQueueListener m : monitoringClients) {
            try {
                m.commandSent(q, activeCommand);
            } catch (Exception e) {
                log.warn("got exception when notifying a monitor, removing it from the list", e);
                monitoringClients.remove(m);
            }
        }
        notifyUpdateQueue(q);
    }

    private void notifyUpdateQueue(CommandQueue q) {
        for (CommandQueueListener m : monitoringClients) {
            try {
                m.updateQueue(q);
            } catch (Exception e) {
                log.warn("got exception when notifying a monitor, removing it from the list", e);
                monitoringClients.remove(m);
            }
        }
    }

    public void addToCommandHistory(CommandId commandId, CommandHistoryAttribute attribute) {
        Value value = attribute.getValue();
        switch (value.getType()) {
        case STRING:
            commandHistoryPublisher.publish(commandId, attribute.getName(), value.getStringValue());
            break;
        default:
            throw new IllegalStateException("Unexpected value type '" + value.getType() + "'");
        }
    }

    /**
     * send a negative ack for a command.
     * 
     * @param activeCommand
     *            the prepared command for which the negative ack is sent
     * @param notify
     *            notify or not the monitoring clients.
     */
    private void failedCommand(CommandQueue cq, ActiveCommand activeCommand, String reason, boolean notify) {
        commandHistoryPublisher.commandFailed(activeCommand.getCommandId(), timeService.getMissionTime(), reason);
        commandingManager.failedCommand(activeCommand);
        // Notify the monitoring clients
        if (notify) {
            for (CommandQueueListener m : monitoringClients) {
                try {
                    m.commandRejected(cq, activeCommand);
                } catch (Exception e) {
                    log.warn("got exception when notifying a monitor, removing it from the list", e);
                    monitoringClients.remove(m);
                }
            }
        }
    }

    private void releaseCommand(CommandQueue q, ActiveCommand activeCommand, boolean notify) {
        commandingManager.releaseCommand(activeCommand);
        // Notify the monitoring clients
        if (notify) {
            notifySent(q, activeCommand);
        }
    }

    private void unhandledCommand(ActiveCommand activeCommand) {
        commandHistoryPublisher.commandFailed(activeCommand.getCommandId(), timeService.getMissionTime(),
                "No matching queue");
        CommandHistoryAttribute attr = CommandHistoryAttribute.newBuilder()
                .setName(CommandHistoryPublisher.CommandComplete_KEY)
                .setValue(Value.newBuilder().setStringValue("NOK"))
                .build();
        addToCommandHistory(activeCommand.getCommandId(), attr);
        for (CommandQueueListener m : monitoringClients) {
            try {
                m.commandUnhandled(activeCommand);
            } catch (Exception e) {
                log.warn("got exception when notifying a monitor, removing it from the list", e);
                monitoringClients.remove(m);
            }
        }
    }

    /**
     * @param user
     * @param pc
     * @return the queue where the command should be placed.
     */
    public CommandQueue getQueue(User user, PreparedCommand pc) {
        for (CommandQueue cq : queues.values()) {
            if (cq.matches(user, pc.getMetaCommand())) {
                return cq;
            }
        }

        return null;
    }

    /**
     * Called by external clients to remove a command from the queue
     * 
     * @param commandId
     * @param username
     *            the username rejecting the command
     * @return the command removed from the queeu
     */
    public synchronized PreparedCommand rejectCommand(CommandId commandId, String username) {
        log.info("called to remove command: {}", commandId);
        ActiveCommand activeCommand = null;
        CommandQueue queue = null;
        for (CommandQueue q : queues.values()) {
            activeCommand = q.getcommand(commandId);
            if (activeCommand != null) {
                queue = q;
                break;
            }

        }
        if (activeCommand != null) {
            queue.remove(activeCommand, false);
            long missionTime = timeService.getMissionTime();
            commandHistoryPublisher.publishAck(activeCommand.getCommandId(),
                    CommandHistoryPublisher.AcknowledgeReleased_KEY, missionTime, AckStatus.NOK,
                    "Rejected by " + username);
            failedCommand(queue, activeCommand, "Rejected by " + username, true);
            notifyUpdateQueue(queue);
            return activeCommand.getPreparedCommand();
        } else {
            log.warn("command not found in any queue");
            return null;
        }
    }

    public synchronized PreparedCommand rejectCommand(String commandId, String username) {
        for (CommandQueue q : queues.values()) {
            ActiveCommand activeCommand = q.getcommand(commandId);
            if (activeCommand != null) {
                return rejectCommand(activeCommand.getCommandId(), username);
            }
        }
        log.warn("no active command found for id {}", commandId);
        return null;
    }

    /**
     * Called from external client to release a command from the queue
     * 
     * @param commandId
     *            - if to rebuild the command binary from the source
     * @return the prepared command sent
     */
    public synchronized PreparedCommand sendCommand(CommandId commandId) {
        ActiveCommand command = null;
        CommandQueue queue = null;
        for (CommandQueue q : queues.values()) {
            command = q.getcommand(commandId);
            if (command != null) {
                queue = q;
                break;
            }
        }
        if (command != null) {
            preReleaseCommand(queue, command);
            return command.getPreparedCommand();
        } else {
            return null;
        }

    }

    public synchronized PreparedCommand sendCommand(String commandId) {
        for (CommandQueue q : queues.values()) {
            ActiveCommand activeCommand = q.getcommand(commandId);
            if (activeCommand != null) {
                return sendCommand(activeCommand.getCommandId());
            }
        }
        log.warn("no prepared command found for id {}", commandId);
        return null;
    }

    /**
     * Called from external clients to change the state of the queue
     * 
     * @param queueName
     *            the queue whose state has to be set
     * @param newState
     *            the new state of the queue
     * @return the queue whose state has been changed or null if no queue by the name exists
     */
    public synchronized CommandQueue setQueueState(String queueName, QueueState newState/* , boolean rebuild */) {
        CommandQueue queue = null;
        for (CommandQueue q : queues.values()) {
            if (q.getName().equals(queueName)) {
                queue = q;
                break;
            }
        }
        if (queue == null) {
            return null;
        }

        if (queue.state == newState) {
            return queue;
        }

        queue.state = newState;
        if (queue.state == QueueState.ENABLED) {
            for (ActiveCommand pc : queue.getCommands()) {
                preReleaseCommand(queue, pc);
            }
        }
        if (queue.state == QueueState.DISABLED) {
            long missionTime = timeService.getMissionTime();
            for (ActiveCommand pc : queue.getCommands()) {
                commandHistoryPublisher.publishAck(pc.getCommandId(),
                        CommandHistoryPublisher.AcknowledgeReleased_KEY, missionTime, AckStatus.NOK, "Queue disabled");
                failedCommand(queue, pc, "Queue disabled", true);
            }
            queue.clear(false);
        }

        // Notify the monitoring clients
        notifyUpdateQueue(queue);
        saveMemento();
        return queue;
    }

    private void saveMemento() {
        var memento = new CommandQueueMemento();
        for (var queue : queues.values()) {
            var state = CommandQueueState.forQueue(queue);
            memento.addCommandQueueState(queue.getName(), state);
        }
        var mementoDb = MementoDb.getInstance(instance);
        mementoDb.putObject(MEMENTO_KEY, memento);
    }

    /**
     * Called from a queue monitor to register itself in order to be notified when new commands are added/removed from
     * the queue.
     * 
     * @param cqm
     *            the callback which will be called with updates
     */
    public void registerListener(CommandQueueListener cqm) {
        monitoringClients.add(cqm);
    }

    public boolean removeListener(CommandQueueListener cqm) {
        return monitoringClients.remove(cqm);
    }

    public String getInstance() {
        return instance;
    }

    public String getChannelName() {
        return processorName;
    }

    /**
     * Called from PRM when new telemetry data is available
     */
    @Override
    public void process(ProcessingData tmData) {
        for (TransmissionConstraintChecker tcc : pendingTcCheckers) {
            tcc.checkWithTm(tmData);
        }
    }

    private void scheduleCheck(final TransmissionConstraintChecker tcc, long millisec) {
        timer.schedule(tcc::checkImmediate, millisec, TimeUnit.MILLISECONDS);
    }

    enum TCStatus {
        INIT, PENDING, OK, TIMED_OUT
    }

    class TransmissionConstraintChecker {
        volatile TCStatus aggregateStatus = TCStatus.INIT;
        final List<TransmissionConstraintStatus> tcsList = new ArrayList<>();
        final ActiveCommand activeCommand;
        final CommandQueue queue;
        int ppmSubscriptionId = -1;

        public TransmissionConstraintChecker(CommandQueue queue, ActiveCommand activeCommand) {
            this.activeCommand = activeCommand;
            this.queue = queue;
            List<TransmissionConstraint> constraints = activeCommand.getMetaCommand().getTransmissionConstraintList();
            log.debug("Starting transmission constrant checker with {} checks for command {}, ", constraints.size(),
                    activeCommand.getLoggingId());
            for (TransmissionConstraint tc : constraints) {
                TransmissionConstraintStatus tcs = new TransmissionConstraintStatus(tc);
                tcsList.add(tcs);
            }
            Set<Parameter> pset = activeCommand.getMetaCommand().getTransmissionConstraintList()
                    .stream()
                    .flatMap(tcs -> tcs.getMatchCriteria().getDependentParameters().stream())
                    .filter(p -> !p.isCommandParameter())
                    .collect(Collectors.toSet());

            if (!pset.isEmpty()) {
                ParameterProcessorManager ppm = processor.getParameterProcessorManager();
                ppmSubscriptionId = ppm.subscribe(pset, tmData -> checkWithTm(tmData));
            }

        }

        /**
         * This may be called on multiple threads in parallel.
         * <p>
         * We cannot move the processing data on a different thread, so we do the check here and use the result in the
         * timer thread.
         */
        public void checkWithTm(ProcessingData tmData) {
            if (aggregateStatus != TCStatus.PENDING) {
                return;
            }
            ProcessingData cmdData = ProcessingData.cloneForCommanding(tmData, activeCommand.getArguments(),
                    activeCommand.getCmdParamCache());

            check(System.currentTimeMillis(), cmdData);
        }

        public void checkImmediate() {
            long now = System.currentTimeMillis();
            if (aggregateStatus == TCStatus.INIT) {
                // make sure that if timeout=0, the first check will not appear to be too late
                for (TransmissionConstraintStatus tcs : tcsList) {
                    tcs.expirationTime = now + tcs.constraint.getTimeout();
                }
                aggregateStatus = TCStatus.PENDING;
            }

            if (aggregateStatus != TCStatus.PENDING) {
                return;
            }
            ProcessingData cmdData = ProcessingData.createInitial(processor.getLastValueCache(),
                    activeCommand.getArguments(), activeCommand.getCmdParamCache());
            check(now, cmdData);
        }

        private void check(long now, ProcessingData data) {
            TcsUpdate tcsUpdate = new TcsUpdate();
            tcsUpdate.aggrStatus = TCStatus.OK;
            tcsUpdate.scheduleNextCheck = Long.MAX_VALUE;

            for (TransmissionConstraintStatus tcs : tcsList) {
                if (tcs.status == TCStatus.PENDING) {
                    long timeRemaining = tcs.expirationTime - now;
                    if (timeRemaining < 0) {
                        tcsUpdate.tcs = tcs;
                        tcsUpdate.tcsStatus = TCStatus.TIMED_OUT;
                        tcsUpdate.aggrStatus = TCStatus.TIMED_OUT;
                        break;
                    } else {
                        if (tcs.evaluator.evaluate(data) != MatchResult.OK) {
                            if (timeRemaining > 0) {
                                tcsUpdate.aggrStatus = TCStatus.PENDING;
                                if (timeRemaining < tcsUpdate.scheduleNextCheck) {
                                    tcsUpdate.scheduleNextCheck = timeRemaining;
                                }
                            } else {
                                tcsUpdate.aggrStatus = TCStatus.TIMED_OUT;
                                break;
                            }
                        }
                    }
                }
            }

            timer.submit(() -> {
                if (aggregateStatus != TCStatus.PENDING) {
                    return;
                }
                aggregateStatus = tcsUpdate.aggrStatus;

                if (aggregateStatus == TCStatus.PENDING) {
                    onTransmissionConstraintCheckPending(this);
                    scheduleCheck(this, tcsUpdate.scheduleNextCheck);
                } else {
                    onTransmissionConstraintCheckFinished(this);
                }
            });

        }

        void unsubscribe() {
            if (ppmSubscriptionId != -1) {
                processor.getParameterProcessorManager().unsubscribe(ppmSubscriptionId);
            }
        }
    }

    static class TcsUpdate {
        TransmissionConstraintStatus tcs;
        TCStatus tcsStatus;
        TCStatus aggrStatus;
        long scheduleNextCheck;
    }

    static class TransmissionConstraintStatus {
        TransmissionConstraint constraint;
        TCStatus status;
        long expirationTime;
        MatchCriteriaEvaluator evaluator;

        public TransmissionConstraintStatus(TransmissionConstraint tc) {
            this.constraint = tc;
            status = TCStatus.PENDING;
            evaluator = MatchCriteriaEvaluatorFactory.getEvaluator(tc.getMatchCriteria());
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long time) {
        List<ParameterValue> pvlist = new ArrayList<>();
        for (CommandQueue cq : queues.values()) {
            cq.fillInSystemParameters(pvlist, time);
        }
        return pvlist;
    }
}
