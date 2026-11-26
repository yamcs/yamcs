package org.yamcs.activities.triggers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Processor;
import org.yamcs.ProcessorListener;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.activities.Activity;
import org.yamcs.activities.ActivityListener;
import org.yamcs.activities.ActivityService;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.memento.MementoDb;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.activities.ActivityOrigin;
import org.yamcs.protobuf.activities.TriggerFiringOutcome;
import org.yamcs.protobuf.activities.TriggerInfo;
import org.yamcs.protobuf.activities.TriggerPolicy.Concurrency;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

/**
 * Starts activities automatically when an alarm occurs, or when a condition on parameters becomes true.
 * <p>
 * Triggers are declared in the service configuration. All trigger logic runs on a single thread owned by this
 * service. Alarm and parameter callbacks only hand over work to that thread.
 */
public class TriggerService extends AbstractYamcsService implements ActivityListener, ProcessorListener {

    public static final String ORIGIN_TYPE = "TRIGGER";
    public static final String EVENT_SOURCE = "ActivityTrigger";

    private static final String MEMENTO_ENABLED = "activityTriggers.enabled";
    private static final long HOUR = 3_600_000;

    private ActivityService activityService;
    private TriggerDb triggerDb;
    private EventProducer eventProducer;

    private boolean defaultEnabled;
    private int firingRetentionDays;
    private List<TriggerInfo> configTriggers = new ArrayList<>();

    private volatile Thread executorThread;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
        var thread = new Thread(r, "TriggerService");
        thread.setDaemon(true);
        executorThread = thread;
        return thread;
    });

    // Only modified on the executor thread
    private final Map<UUID, TriggerState> triggers = new ConcurrentHashMap<>();
    private final Map<UUID, PendingFiring> pendingFirings = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> triggerIdByActivityId = new HashMap<>();
    private volatile boolean globalEnabled;

    private final AtomicInteger firingSeq = new AtomicInteger();
    private final List<Consumer<TriggerFiring>> firingListeners = new CopyOnWriteArrayList<>();

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption("enabled", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Whether triggers are evaluated when the server starts for the first time. "
                        + "Later changes made through the API are remembered across restarts.");
        spec.addOption("firingRetention", OptionType.INTEGER).withDefault(0)
                .withDescription("Number of days to keep trigger firing records. 0 keeps them forever");
        spec.addOption("triggers", OptionType.LIST).withElementType(OptionType.MAP)
                .withSpec(TriggerDefinitions.getConfigSpec());
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        defaultEnabled = config.getBoolean("enabled");
        firingRetentionDays = config.getInt("firingRetention");

        activityService = YamcsServer.getServer().getInstance(yamcsInstance).getActivityService();
        var mdb = MdbFactory.getInstance(yamcsInstance);

        var names = new HashSet<String>();
        if (config.containsKey("triggers")) {
            for (var triggerConfig : config.getConfigList("triggers")) {
                try {
                    var trigger = TriggerDefinitions.fromConfig(triggerConfig);
                    var errors = new ArrayList<>(TriggerDefinitions.validate(trigger));
                    errors.addAll(TriggerDefinitions.validateReferences(trigger, mdb, this::isKnownActivityType));
                    if (!errors.isEmpty()) {
                        throw new InitException("Invalid trigger '" + trigger.getName() + "': "
                                + String.join(", ", errors));
                    }
                    if (!names.add(trigger.getName())) {
                        throw new InitException("Duplicate trigger name '" + trigger.getName() + "'");
                    }
                    configTriggers.add(withActivityDescription(trigger));
                } catch (ValidationException e) {
                    throw new InitException(e.getMessage(), e);
                }
            }
        }

        triggerDb = new TriggerDb(yamcsInstance);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, EVENT_SOURCE, 10000);
    }

    /**
     * Adds the executor's description of the activity, for display. The arguments may still contain placeholders.
     */
    private TriggerInfo withActivityDescription(TriggerInfo trigger) {
        var executor = activityService.getExecutor(trigger.getActivity().getType());
        if (executor == null) {
            return trigger;
        }
        try {
            var description = executor.describeActivity(TriggerDefinitions.getActivityArgs(trigger));
            if (description != null) {
                var b = trigger.toBuilder();
                b.getActivityBuilder().setDescription(description);
                return b.build();
            }
        } catch (RuntimeException e) {
            log.debug("Cannot describe activity of trigger '{}'", trigger.getName(), e);
        }
        return trigger;
    }

    private boolean isKnownActivityType(String type) {
        return ActivityService.ACTIVITY_TYPE_MANUAL.equals(type) || activityService.getExecutor(type) != null;
    }

    @Override
    protected void doStart() {
        try {
            callSync(() -> {
                globalEnabled = MementoDb.getInstance(yamcsInstance).getBoolean(MEMENTO_ENABLED)
                        .orElse(defaultEnabled);
                expireStalePendingFirings();
                loadTriggers();
                activityService.addActivityListener(this);
                Processor.addProcessorListener(this);
                triggers.values().forEach(this::refreshMonitor);
                return null;
            });
        } catch (RuntimeException e) {
            notifyFailed(e);
            return;
        }

        if (firingRetentionDays > 0) {
            executor.scheduleWithFixedDelay(() -> {
                var limit = TimeEncoding.getWallclockTime() - firingRetentionDays * 24 * HOUR;
                triggerDb.deleteFiringsBefore(limit);
            }, 1, 24 * 60, TimeUnit.MINUTES);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        Processor.removeProcessorListener(this);
        activityService.removeActivityListener(this);
        try {
            callSync(() -> {
                triggers.values().forEach(TriggerState::stopMonitor);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Failed to stop trigger monitors", e);
        }
        executor.shutdownNow();
        notifyStopped();
    }

    private void loadTriggers() {
        for (var trigger : configTriggers) {
            var state = new TriggerState(trigger);
            triggers.put(state.id, state);
        }
        log.debug("Loaded {} triggers", triggers.size());
    }

    private void expireStalePendingFirings() {
        var stale = triggerDb.listFirings(null, List.of(TriggerFiringOutcome.PENDING_CONFIRMATION),
                null, null, Integer.MAX_VALUE);
        for (var firing : stale) {
            firing.setOutcome(TriggerFiringOutcome.EXPIRED);
            firing.setMessage("Expired because of server restart");
            triggerDb.saveFiring(firing);
        }
    }

    /**
     * Starts or stops the monitor of a trigger, depending on the global switch, the trigger's enabled state, and the
     * availability of the processor.
     */
    private void refreshMonitor(TriggerState state) {
        state.stopMonitor();
        if (!globalEnabled || !state.definition.getEnabled()) {
            return;
        }

        var processorName = state.definition.getProcessor();
        var processor = YamcsServer.getServer().getProcessor(yamcsInstance, processorName);
        if (processor == null) {
            log.debug("Trigger '{}' waits for processor '{}'", state.definition.getName(), processorName);
            return;
        }
        if (processor.isReplay()) {
            log.warn("Trigger '{}' is not started, because processor '{}' is a replay processor",
                    state.definition.getName(), processorName);
            return;
        }

        TriggerMonitor monitor;
        if (state.definition.hasAlarmCondition()) {
            monitor = new AlarmTriggerMonitor(this, state, processor);
        } else {
            monitor = new ParameterTriggerMonitor(this, state, processor, executor);
        }
        try {
            monitor.start();
            state.monitor = monitor;
            state.monitorError = null;
        } catch (Exception e) {
            monitor.stop();
            state.monitorError = e.getMessage();
            log.warn("Cannot start trigger '{}': {}", state.definition.getName(), e.getMessage());
            eventProducer.sendWarning("TRIGGER_INVALID",
                    "Trigger '" + state.definition.getName() + "' cannot be started: " + e.getMessage());
        }
    }

    /**
     * Called by monitors (from any thread) when the condition of a trigger is met.
     */
    void onConditionMet(TriggerState state, Map<String, Object> conditionContext) {
        executor.execute(() -> {
            try {
                processFiring(state, conditionContext);
            } catch (Exception e) {
                log.error("Error while processing firing of trigger '{}'", state.definition.getName(), e);
            }
        });
    }

    private void processFiring(TriggerState state, Map<String, Object> conditionContext) {
        if (!globalEnabled || state.monitor == null || triggers.get(state.id) != state) {
            return; // Stale notification
        }

        var definition = state.definition;
        var policy = definition.getPolicy();
        var now = TimeEncoding.getWallclockTime();

        var context = new LinkedHashMap<String, Object>();
        context.put("trigger.id", state.id.toString());
        context.put("trigger.name", definition.getName());
        context.putAll(conditionContext);

        var firing = new TriggerFiring(UUID.randomUUID(), now, firingSeq.getAndIncrement(), state.id,
                definition.getName(), context);

        while (!state.recentFirings.isEmpty() && state.recentFirings.peekFirst() < now - HOUR) {
            state.recentFirings.removeFirst();
        }

        if (policy.getMaxFiringsPerHour() > 0 && state.recentFirings.size() >= policy.getMaxFiringsPerHour()) {
            firing.setOutcome(TriggerFiringOutcome.SUPPRESSED_RATE);
            firing.setMessage("Limit of " + policy.getMaxFiringsPerHour() + " firings per hour reached");
            saveFiring(firing);
            return;
        }
        if (state.lastFired != TimeEncoding.INVALID_INSTANT && now - state.lastFired < policy.getCooldown()) {
            firing.setOutcome(TriggerFiringOutcome.SUPPRESSED_COOLDOWN);
            saveFiring(firing);
            return;
        }

        var busy = !state.runningActivities.isEmpty() || state.pendingFiring != null;
        if (busy) {
            if (policy.getConcurrency() == Concurrency.SKIP) {
                firing.setOutcome(TriggerFiringOutcome.SUPPRESSED_CONCURRENCY);
                firing.setMessage(state.pendingFiring != null
                        ? "A previous firing is waiting for confirmation"
                        : "The previous activity is still running");
                saveFiring(firing);
                return;
            } else if (policy.getConcurrency() == Concurrency.CANCEL_PREVIOUS) {
                cancelPrevious(state);
            }
        }

        state.lastFired = now;
        state.recentFirings.addLast(now);
        state.firingCount++;

        if (policy.getRequireConfirmation()) {
            firing.setOutcome(TriggerFiringOutcome.PENDING_CONFIRMATION);
            var pending = new PendingFiring(state, firing);
            pending.expiry = executor.schedule(() -> expire(pending),
                    policy.getConfirmationTimeout(), TimeUnit.MILLISECONDS);
            pendingFirings.put(firing.getId(), pending);
            state.pendingFiring = pending;
            saveFiring(firing);
            eventProducer.sendWarning("TRIGGER_PENDING", "Trigger '" + definition.getName()
                    + "' is waiting for confirmation: " + describeContext(context));
            return;
        }

        User user;
        try {
            user = resolveRunAsUser(definition);
        } catch (IllegalStateException e) {
            failFiring(state, firing, e.getMessage());
            return;
        }
        startActivity(state, firing, user);
    }

    private void cancelPrevious(TriggerState state) {
        var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
        for (var activityId : new ArrayList<>(state.runningActivities)) {
            activityService.cancelActivity(activityId, systemUser);
        }
        if (state.pendingFiring != null) {
            var pending = state.pendingFiring;
            pending.expiry.cancel(false);
            pendingFirings.remove(pending.firing.getId());
            state.pendingFiring = null;
            pending.firing.setOutcome(TriggerFiringOutcome.EXPIRED);
            pending.firing.setMessage("Replaced by a newer firing");
            saveFiring(pending.firing);
        }
    }

    private void startActivity(TriggerState state, TriggerFiring firing, User user) {
        var definition = state.definition;
        try {
            var args = resolveArgs(definition, firing.getContext());
            var label = definition.getActivity().hasLabel()
                    ? definition.getActivity().getLabel()
                    : definition.getName();

            var activity = activityService.prepareActivity(definition.getActivity().getType(), args, user, label);
            activity.setOrigin(ActivityOrigin.newBuilder()
                    .setType(ORIGIN_TYPE)
                    .setRef(state.id.toString())
                    .setName(definition.getName())
                    .build());
            activityService.startActivity(activity, user);
            activityService.logServiceInfo(activity, "Triggered by '" + definition.getName() + "': "
                    + describeContext(firing.getContext()));

            triggerIdByActivityId.put(activity.getId(), state.id);
            state.runningActivities.add(activity.getId());

            firing.setOutcome(TriggerFiringOutcome.STARTED);
            firing.setActivityId(activity.getId());
            saveFiring(firing);
            eventProducer.sendInfo("TRIGGER_STARTED", "Trigger '" + definition.getName()
                    + "' started activity " + activity.getDetail() + ": " + describeContext(firing.getContext()));
        } catch (Exception e) {
            log.warn("Trigger '{}' failed to start activity", definition.getName(), e);
            var message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            failFiring(state, firing, message);
        }
    }

    private void failFiring(TriggerState state, TriggerFiring firing, String message) {
        firing.setOutcome(TriggerFiringOutcome.FAILED_TO_START);
        firing.setMessage(message);
        saveFiring(firing);
        eventProducer.sendWarning("TRIGGER_FAILED",
                "Trigger '" + state.definition.getName() + "' failed to start activity: " + message);
    }

    private void expire(PendingFiring pending) {
        if (pendingFirings.remove(pending.firing.getId()) == null) {
            return;
        }
        if (pending.state.pendingFiring == pending) {
            pending.state.pendingFiring = null;
        }
        pending.firing.setOutcome(TriggerFiringOutcome.EXPIRED);
        pending.firing.setMessage("Not confirmed in time");
        saveFiring(pending.firing);
        eventProducer.sendWarning("TRIGGER_EXPIRED", "Firing of trigger '" + pending.state.definition.getName()
                + "' was not confirmed in time");
    }

    private Map<String, Object> resolveArgs(TriggerInfo definition, Map<String, Object> context) {
        var templates = new ArgumentTemplates(context, name -> lookupParameterValue(definition, name));
        return templates.resolve(TriggerDefinitions.getActivityArgs(definition));
    }

    private Object lookupParameterValue(TriggerInfo definition, String name) {
        var processor = YamcsServer.getServer().getProcessor(yamcsInstance, definition.getProcessor());
        if (processor == null) {
            return null;
        }
        var parameter = processor.getMdb().getParameter(name);
        if (parameter == null) {
            return null;
        }
        var pv = processor.getLastValueCache().getValue(parameter);
        if (pv == null || pv.getEngValue() == null) {
            return null;
        }
        return toContextValue(pv.getEngValue());
    }

    private User resolveRunAsUser(TriggerInfo definition) {
        var securityStore = YamcsServer.getServer().getSecurityStore();
        if (!definition.hasRunAs()) {
            return securityStore.getSystemUser();
        }
        var username = definition.getRunAs();
        if (username.equals(securityStore.getGuestUser().getName())) {
            return securityStore.getGuestUser(); // Security disabled
        }
        var user = securityStore.getDirectory().getUser(username);
        if (user == null) {
            throw new IllegalStateException("Unknown user '" + username + "'");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("User '" + username + "' is not active");
        }
        if (!user.hasSystemPrivilege(SystemPrivilege.ControlActivities)) {
            throw new IllegalStateException("User '" + username + "' may not start activities");
        }
        return user;
    }

    private void saveFiring(TriggerFiring firing) {
        log.debug("Trigger '{}': {}", firing.getTriggerName(), firing.getOutcome());
        triggerDb.saveFiring(firing);
        firingListeners.forEach(l -> l.accept(firing));
    }

    @Override
    public void onActivityUpdated(Activity activity) {
        if (!activity.isStopped()) {
            return;
        }
        var activityId = activity.getId();
        executor.execute(() -> {
            var triggerId = triggerIdByActivityId.remove(activityId);
            if (triggerId != null) {
                var state = triggers.get(triggerId);
                if (state != null) {
                    state.runningActivities.remove(activityId);
                }
            }
        });
    }

    @Override
    public void processorAdded(Processor processor) {
        if (yamcsInstance.equals(processor.getInstance())) {
            executor.execute(() -> refreshMonitors(processor.getName()));
        }
    }

    @Override
    public void processorClosed(Processor processor) {
        if (yamcsInstance.equals(processor.getInstance())) {
            executor.execute(() -> {
                for (var state : triggers.values()) {
                    if (state.definition.getProcessor().equals(processor.getName())) {
                        state.stopMonitor();
                    }
                }
            });
        }
    }

    @Override
    public void processorStateChanged(Processor processor) {
        // Ignore
    }

    private void refreshMonitors(String processorName) {
        for (var state : triggers.values()) {
            if (state.definition.getProcessor().equals(processorName)) {
                refreshMonitor(state);
            }
        }
    }

    /*
     * Public API. These methods may be called from any thread.
     */

    public List<TriggerInfo> listTriggers() {
        return callSync(() -> triggers.values().stream()
                .map(TriggerState::toInfo)
                .sorted(Comparator.comparing(TriggerInfo::getName))
                .toList());
    }

    /**
     * Find a trigger by identifier or name.
     *
     * @return the trigger, or null if not found
     */
    public TriggerInfo getTrigger(String idOrName) {
        return callSync(() -> {
            var state = findState(idOrName);
            return state != null ? state.toInfo() : null;
        });
    }

    /**
     * Enable or disable a trigger. The change lasts until the next restart.
     *
     * @return the updated trigger, or null if not found
     */
    public TriggerInfo setTriggerEnabled(String idOrName, boolean enabled, User user) {
        return callSync(() -> {
            var state = findState(idOrName);
            if (state == null) {
                return null;
            }
            state.definition = state.definition.toBuilder().setEnabled(enabled).build();
            refreshMonitor(state);
            log.info("Trigger '{}' {} by {}", state.definition.getName(), enabled ? "enabled" : "disabled",
                    user.getName());
            return state.toInfo();
        });
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    /**
     * Enable or disable evaluation of all triggers. The state is remembered across restarts.
     */
    public void setGlobalEnabled(boolean enabled, User user) {
        callSync(() -> {
            globalEnabled = enabled;
            MementoDb.getInstance(yamcsInstance).putBoolean(MEMENTO_ENABLED, enabled);
            triggers.values().forEach(this::refreshMonitor);
            var message = "All triggers " + (enabled ? "enabled" : "disabled") + " by " + user.getName();
            log.info(message);
            if (enabled) {
                eventProducer.sendInfo("TRIGGERS_ENABLED", message);
            } else {
                eventProducer.sendWarning("TRIGGERS_DISABLED", message);
            }
            return null;
        });
    }

    public int getPendingConfirmationCount() {
        return pendingFirings.size();
    }

    public List<TriggerFiring> listFirings(String triggerIdOrName, Collection<TriggerFiringOutcome> outcomes,
            Long start, Long stop, int limit) {
        UUID triggerId = null;
        if (triggerIdOrName != null) {
            var trigger = getTrigger(triggerIdOrName);
            if (trigger == null) {
                return List.of();
            }
            triggerId = UUID.fromString(trigger.getId());
        }
        return triggerDb.listFirings(triggerId, outcomes, start, stop, limit);
    }

    /**
     * Confirms a pending firing. The activity is started on behalf of the confirming user.
     *
     * @return the updated firing, or null if no pending firing with this identifier exists
     */
    public TriggerFiring confirmFiring(UUID firingId, User user) {
        return callSync(() -> {
            var pending = takePending(firingId);
            if (pending == null) {
                return null;
            }
            pending.firing.setDecidedBy(user.getName());
            startActivity(pending.state, pending.firing, user);
            return pending.firing;
        });
    }

    /**
     * Rejects a pending firing.
     *
     * @return the updated firing, or null if no pending firing with this identifier exists
     */
    public TriggerFiring rejectFiring(UUID firingId, User user) {
        return callSync(() -> {
            var pending = takePending(firingId);
            if (pending == null) {
                return null;
            }
            pending.firing.setDecidedBy(user.getName());
            pending.firing.setOutcome(TriggerFiringOutcome.REJECTED);
            saveFiring(pending.firing);
            eventProducer.sendInfo("TRIGGER_REJECTED", "Firing of trigger '"
                    + pending.state.definition.getName() + "' rejected by " + user.getName());
            return pending.firing;
        });
    }

    public void addFiringListener(Consumer<TriggerFiring> listener) {
        firingListeners.add(listener);
    }

    public void removeFiringListener(Consumer<TriggerFiring> listener) {
        firingListeners.remove(listener);
    }

    private PendingFiring takePending(UUID firingId) {
        var pending = pendingFirings.remove(firingId);
        if (pending == null) {
            return null;
        }
        pending.expiry.cancel(false);
        if (pending.state.pendingFiring == pending) {
            pending.state.pendingFiring = null;
        }
        return pending;
    }

    private TriggerState findState(String idOrName) {
        try {
            var state = triggers.get(UUID.fromString(idOrName));
            if (state != null) {
                return state;
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID
        }
        for (var state : triggers.values()) {
            if (state.definition.getName().equals(idOrName)) {
                return state;
            }
        }
        return null;
    }

    private <T> T callSync(Callable<T> callable) {
        if (Thread.currentThread() == executorThread) {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            return executor.submit(callable).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timeout while waiting for trigger service", e);
        }
    }

    private static String describeContext(Map<String, Object> context) {
        if (context.containsKey("alarm.subject")) {
            var sb = new StringBuilder();
            sb.append(context.get("alarm.subject"));
            if (context.containsKey("value")) {
                sb.append(" = ").append(context.get("value"));
            }
            sb.append(" (").append(context.get("alarm.severity"))
                    .append(", ").append(context.get("alarm.notification")).append(")");
            return sb.toString();
        } else if (context.containsKey("condition.expression")) {
            var sb = new StringBuilder();
            sb.append(context.get("condition.expression"));
            sb.append(" became ").append("RISING".equals(context.get("condition.edge")) ? "true" : "false");
            var values = context.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(ArgumentTemplates.PARAM_PREFIX))
                    .map(e -> e.getKey().substring(ArgumentTemplates.PARAM_PREFIX.length()) + " = " + e.getValue())
                    .collect(Collectors.joining(", "));
            if (!values.isEmpty()) {
                sb.append(" (").append(values).append(")");
            }
            return sb.toString();
        } else {
            return context.toString();
        }
    }

    /**
     * Converts a parameter value to a type that can be stored in the firing context.
     */
    static Object toContextValue(Value value) {
        return switch (value.getType()) {
        case BINARY -> StringConverter.arrayToHexString(value.getBinaryValue());
        case TIMESTAMP -> TimeEncoding.toString(value.getTimestampValue());
        case AGGREGATE, ARRAY, NONE -> value.toString();
        default -> ValueUtility.getYarchValue(value);
        };
    }

    /**
     * Runtime state of a trigger. Only accessed on the trigger service thread.
     */
    static class TriggerState {
        final UUID id;
        volatile TriggerInfo definition;
        TriggerMonitor monitor;
        String monitorError;

        long lastFired = TimeEncoding.INVALID_INSTANT;
        int firingCount;
        final ArrayDeque<Long> recentFirings = new ArrayDeque<>();
        final Set<UUID> runningActivities = new HashSet<>();
        PendingFiring pendingFiring;

        TriggerState(TriggerInfo definition) {
            this.id = UUID.fromString(definition.getId());
            this.definition = definition;
        }

        TriggerInfo getDefinition() {
            return definition;
        }

        void stopMonitor() {
            if (monitor != null) {
                monitor.stop();
                monitor = null;
            }
        }

        TriggerInfo toInfo() {
            var b = definition.toBuilder().setFiringCount(firingCount);
            if (lastFired != TimeEncoding.INVALID_INSTANT) {
                b.setLastFired(TimeEncoding.toProtobufTimestamp(lastFired));
            }
            return b.build();
        }
    }

    private static class PendingFiring {
        final TriggerState state;
        final TriggerFiring firing;
        ScheduledFuture<?> expiry;

        PendingFiring(TriggerState state, TriggerFiring firing) {
            this.state = state;
            this.firing = firing;
        }
    }
}
