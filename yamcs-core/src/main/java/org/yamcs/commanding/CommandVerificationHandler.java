package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.cmdhistory.Attribute;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.Verifier.State;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.MetaCommand;

/**
 * This class implements the (post transmission) command verification.
 * <p>
 * There is one handler for all the verifiers of a command.
 * <p>
 * This handler collects all command attributes, command arguments and command history events and transforms them to
 * parameters to be given to the verifiers when they run.
 *
 */
public class CommandVerificationHandler implements CommandHistoryConsumer {
    final Processor processor;
    final ActiveCommand activeCommand;
    final ScheduledThreadPoolExecutor timer;
    final Map<Argument, ArgumentValue> cmdArguments;
    final CommandingManager commandingManager;

    private List<Verifier> verifiers = Collections.synchronizedList(new ArrayList<>());
    private final Log log;
    AlgorithmExecutionContext algorithmCtx;

    public CommandVerificationHandler(CommandingManager commandingManager, ActiveCommand pc) {
        this.commandingManager = commandingManager;
        this.processor = commandingManager.getProcessor();
        this.activeCommand = pc;
        this.timer = processor.getTimer();
        log = new Log(this.getClass(), processor.getInstance());
        this.cmdArguments = activeCommand.getArguments();
    }

    public void start() {
        MetaCommand cmd = activeCommand.getMetaCommand();
        List<CommandVerifier> cmdVerifiers = new ArrayList<>();
        collectCmdVerifiers(cmd, cmdVerifiers, activeCommand.getVerifierOverride());

        if (activeCommand.disableCommandVerifiers()) {
            log.debug("All verifiers are disabled");
            CommandHistoryPublisher cmdHistPublisher = processor.getCommandHistoryPublisher();
            cmdVerifiers.forEach(cv -> cmdHistPublisher.publishAck(activeCommand.getCommandId(), getHistKey(cv),
                    processor.getCurrentTime(), AckStatus.DISABLED));
            return;
        }

        Verifier prevVerifier = null;

        try {
            processor.getCommandHistoryManager().subscribeCommand(activeCommand.getCommandId(), this);
        } catch (InvalidCommandId e) {
            log.error("Got invalidCommand id while subscribing for command history", e);
        }

        for (CommandVerifier cv : cmdVerifiers) {
            Verifier verifier;

            switch (cv.getType()) {
            case ALGORITHM:
                if (algorithmCtx == null) {
                    createAlgorithmContext();
                }
                verifier = new AlgorithmVerifier(this, cv);
                break;
            case CONTAINER:
                verifier = new ContainerVerifier(this, cv, cv.getContainerRef());
                break;
            case MATCH_CRITERIA:
                verifier = new MatchCriteriaVerifier(this, cv);
                break;
            case PARAMETER_VALUE_CHANGE:
                verifier = new ValueChangeVerifier(this, cv);
                break;
            default:
                throw new IllegalStateException("Command verifier of type " + cv.getType() + " not implemented");
            }

            CheckWindow checkWindow = cv.getCheckWindow();
            boolean scheduleNow = true;

            if (checkWindow.getTimeWindowIsRelativeTo() == TimeWindowIsRelativeToType.LAST_VERIFIER) {
                if (prevVerifier != null) {
                    prevVerifier.nextVerifier = verifier;
                    scheduleNow = false;
                }
            }
            verifiers.add(verifier);
            if (scheduleNow) {
                scheduleVerifier(verifier, checkWindow.getTimeToStartChecking(), checkWindow.getTimeToStopChecking());
            } else {
                log.debug("Not scheduling {} because it depends on the {}", cv, prevVerifier.getStage());
            }
            prevVerifier = verifier;
        }
    }

    /**
     * collects all the required command verifiers from this command and its parents, taking care not to add two
     * verifiers for the same stage
     */
    private void collectCmdVerifiers(MetaCommand cmd, List<CommandVerifier> cmdVerifiers,
            Map<String, VerifierConfig> verifierOverride) {
        CommandHistoryPublisher cmdHistPublisher = processor.getCommandHistoryPublisher();
        MetaCommand basecmd = cmd.getBaseMetaCommand();
        if (basecmd != null) {
            collectCmdVerifiers(basecmd, cmdVerifiers, verifierOverride);
        }

        for (CommandVerifier cv : cmd.getCommandVerifiers()) {
            boolean found = false;
            for (CommandVerifier existingv : cmdVerifiers) {
                if (existingv.getStage().equals(cv.getStage())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                VerifierConfig extraOptions = verifierOverride.get(cv.getStage());
                if (extraOptions == null) {
                    cmdVerifiers.add(cv);
                } else {
                    if (extraOptions.getDisable()) {
                        cmdHistPublisher.publishAck(activeCommand.getCommandId(), getHistKey(cv),
                                processor.getCurrentTime(), AckStatus.DISABLED);
                        log.debug("skipping verifier {}", cv.getStage());
                        continue;
                    }
                    cmdVerifiers.add(overrideVerifier(cv, extraOptions));
                }
            }
        }

    }

    private CommandVerifier overrideVerifier(CommandVerifier cv, VerifierConfig extraOptions) {
        if (!extraOptions.hasCheckWindow()) { // maybe we should throw an exception here
            return cv;
        }
        VerifierConfig.CheckWindow cw = extraOptions.getCheckWindow();
        CheckWindow cw1 = new CheckWindow(cw.getTimeToStartChecking(), cw.getTimeToStopChecking(),
                cv.getCheckWindow().getTimeWindowIsRelativeTo());
        CommandVerifier cv1 = new CommandVerifier(cv);
        cv1.setCheckWindow(cw1);
        log.debug("Replacing verifier {} with {}", cv, cv1);
        return cv1;
    }

    private void createAlgorithmContext() {
        AlgorithmManager algMgr = processor.getParameterProcessorManager().getParameterProvider(AlgorithmManager.class);
        if (algMgr == null) {
            String msg = "Algorithm manager not configured for this processor, cannot run command verification based on algorithms";
            log.error(msg);
            throw new ConfigurationException(msg);
        }

        algorithmCtx = algMgr.createContext(activeCommand.getCmdName());
    }

    private void scheduleVerifier(final Verifier verifier, long windowStart, long windowStop) {
        CommandHistoryPublisher cmdHistPublisher = processor.getCommandHistoryPublisher();
        String histKey = getHistKey(verifier.cv);

        if (windowStart > 0) {
            timer.schedule(() -> {
                if (verifier.state == State.NEW) {
                    cmdHistPublisher.publishAck(activeCommand.getCommandId(), histKey, processor.getCurrentTime(),
                            AckStatus.PENDING);
                    startVerifier(verifier);
                }
            }, windowStart, TimeUnit.MILLISECONDS);

            cmdHistPublisher.publishAck(activeCommand.getCommandId(), histKey, processor.getCurrentTime(),
                    AckStatus.SCHEDULED);
        } else {
            cmdHistPublisher.publishAck(activeCommand.getCommandId(), histKey, processor.getCurrentTime(),
                    AckStatus.PENDING);
            verifier.start();
        }

        if (windowStop <= 0) {
            throw new IllegalArgumentException("The window stop has to be greater than 0");
        }

        timer.schedule(() -> {
            verifier.timeout();
        }, windowStop, TimeUnit.MILLISECONDS);
    }

    private void startVerifier(Verifier verifier) {
        log.debug("Command {} starting verifier: {}", StringConverter.toString(activeCommand.getCommandId()),
                verifier.cv);
        verifier.start();

    }

    String getHistKey(CommandVerifier cv) {
        return CommandHistoryPublisher.Verifier_KEY_PREFIX + "_" + cv.getStage();
    }

    void onVerifierFinished(Verifier v) {
        onVerifierFinished(v, null, null);
    }

    void onVerifierFinished(Verifier v, String failureReason, ParameterValue returnPv) {
        Verifier.State state = v.getState();
        log.debug("Command {} verifier finished: {} result: {}",
                StringConverter.toString(activeCommand.getCommandId()), v.cv, state);
        CommandVerifier cv = v.cv;
        CommandHistoryPublisher cmdHistPublisher = processor.getCommandHistoryPublisher();
        String histKey = CommandHistoryPublisher.Verifier_KEY_PREFIX + "_" + cv.getStage();
        cmdHistPublisher.publishAck(activeCommand.getCommandId(), histKey, processor.getCurrentTime(),
                getAckState(v.state), failureReason, returnPv);
        TerminationAction ta = null;
        switch (state) {
        case OK:
            ta = cv.getOnSuccess();
            break;
        case NOK:
            ta = cv.getOnFail();
            break;
        case TIMEOUT:
            ta = cv.getOnTimeout();
            break;
        case CANCELLED:
            break;
        default:
            log.error("Illegal state onVerifierFinished called with state: {}", state);
        }
        if (ta == TerminationAction.SUCCESS) {
            cmdHistPublisher.publishAck(activeCommand.getCommandId(), CommandHistoryPublisher.CommandComplete_KEY,
                    processor.getCurrentTime(), AckStatus.OK, null, returnPv);
            stop();
        } else if (ta == TerminationAction.FAIL) {

            if (failureReason == null && returnPv != null) {
                Value engvalue = returnPv.getEngValue();
                if (engvalue != null) {
                    failureReason = "Verifier " + cv.getStage() + " return: " + engvalue;
                }
            }

            if (failureReason == null) {
                failureReason = "Verifier " + cv.getStage() + " result: " + state;
            }
            cmdHistPublisher.commandFailed(activeCommand.getCommandId(), processor.getCurrentTime(), failureReason);
            stop();
        }

        if (v.nextVerifier != null && (state == State.OK)) {
            CheckWindow cw = v.nextVerifier.cv.getCheckWindow();
            scheduleVerifier(v.nextVerifier, cw.getTimeToStartChecking(), cw.getTimeToStopChecking());
        }
    }

    private AckStatus getAckState(State state) {
        switch (state) {
        case NEW:
        case RUNNING:
            return AckStatus.PENDING;
        case NOK:
            return AckStatus.NOK;
        case OK:
            return AckStatus.OK;
        case TIMEOUT:
            return AckStatus.TIMEOUT;
        case CANCELLED:
            return AckStatus.CANCELLED;
        case DISABLED:
            return AckStatus.DISABLED;
        default:
            throw new IllegalArgumentException("Unknown state " + state);
        }
    }

    private void stop() {
        log.debug("{} command verification finished", activeCommand);
        processor.getCommandHistoryManager().unsubscribeCommand(activeCommand.getCommandId(), this);
        commandingManager.verificatonFinished(activeCommand);
        if (algorithmCtx != null) {
            AlgorithmManager algMgr = processor.getParameterProcessorManager()
                    .getParameterProvider(AlgorithmManager.class);
            algMgr.removeContext(algorithmCtx);
        }
    }

    public Processor getProcessor() {
        return processor;
    }

    public AlgorithmExecutionContext getAlgorithmExecutionContext() {
        return algorithmCtx;
    }

    public ActiveCommand getActiveCommand() {
        return activeCommand;
    }

    public AlgorithmManager getAlgorithmManager() {
        return processor.getParameterProcessorManager().getParameterProvider(AlgorithmManager.class);
    }

    @Override
    public void updatedCommand(CommandId cmdId, long time, List<Attribute> attrs) {
        for (Attribute attr : attrs) {
            if (attr.getKey().equals(CommandHistoryPublisher.CommandComplete_KEY + "_Status")) {
                log.trace("Command completed, canceling all pending verifiers");
                for (Verifier v : verifiers) {
                    v.cancel();
                }
            }
        }
    }

    @Override
    public void addedCommand(PreparedCommand pc) {
        // this will not be called because we subscribe to only one command
    }

}
