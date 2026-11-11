package org.yamcs.activities.triggers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.Spec.ValidationContext;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.activities.ActivityDefinitionInfo;
import org.yamcs.protobuf.activities.AlarmCondition;
import org.yamcs.protobuf.activities.AlarmCondition.AlarmType;
import org.yamcs.protobuf.activities.ParameterCondition;
import org.yamcs.protobuf.activities.ParameterCondition.Edge;
import org.yamcs.protobuf.activities.TriggerInfo;
import org.yamcs.protobuf.activities.TriggerPolicy;
import org.yamcs.protobuf.activities.TriggerPolicy.Concurrency;
import org.yamcs.protobuf.activities.TriggerSource;

/**
 * Conversion, defaults and validation of trigger definitions.
 * <p>
 * Definitions are represented by {@link TriggerInfo} messages, both in the API and in storage. Triggers declared in
 * the service configuration are converted to the same representation.
 */
public class TriggerDefinitions {

    public static final String DEFAULT_PROCESSOR = "realtime";
    public static final long DEFAULT_COOLDOWN = 60_000;
    public static final long DEFAULT_CONFIRMATION_TIMEOUT = 300_000;

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h|d)?");

    /**
     * Spec of a single trigger in the service configuration
     */
    public static Spec getConfigSpec() {
        var alarmSpec = new Spec();
        alarmSpec.addOption("type", OptionType.STRING)
                .withChoices(AlarmType.class)
                .withDefault(AlarmType.PARAMETER.name());
        alarmSpec.addOption("subjects", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING);
        alarmSpec.addOption("notifications", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING);
        alarmSpec.addOption("minSeverity", OptionType.STRING)
                .withChoices(AlarmSeverity.class);
        alarmSpec.addOption("includeShelved", OptionType.BOOLEAN);

        var parameterSpec = new Spec();
        parameterSpec.addOption("expression", OptionType.STRING).withRequired(true);
        parameterSpec.addOption("edge", OptionType.STRING).withChoices(Edge.class);
        parameterSpec.addOption("holdTime", OptionType.ANY);
        parameterSpec.addOption("invalidAsFalse", OptionType.BOOLEAN);

        var conditionSpec = new Spec();
        conditionSpec.addOption("alarm", OptionType.MAP).withSpec(alarmSpec);
        conditionSpec.addOption("parameter", OptionType.MAP).withSpec(parameterSpec);
        conditionSpec.requireOneOf("alarm", "parameter");
        conditionSpec.mutuallyExclusive("alarm", "parameter");

        var policySpec = new Spec();
        policySpec.addOption("cooldown", OptionType.ANY);
        policySpec.addOption("concurrency", OptionType.STRING).withChoices(Concurrency.class);
        policySpec.addOption("requireConfirmation", OptionType.BOOLEAN);
        policySpec.addOption("confirmationTimeout", OptionType.ANY);
        policySpec.addOption("maxFiringsPerHour", OptionType.INTEGER);

        var activitySpec = new Spec();
        activitySpec.addOption("type", OptionType.STRING).withRequired(true);
        activitySpec.addOption("args", OptionType.MAP).withSpec(Spec.ANY);
        activitySpec.addOption("label", OptionType.STRING);

        var spec = new Spec();
        spec.addOption("name", OptionType.STRING).withRequired(true);
        spec.addOption("description", OptionType.STRING);
        spec.addOption("enabled", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("processor", OptionType.STRING);
        spec.addOption("runAs", OptionType.STRING);
        spec.addOption("condition", OptionType.MAP).withSpec(conditionSpec).withRequired(true);
        spec.addOption("policy", OptionType.MAP).withSpec(policySpec);
        spec.addOption("activity", OptionType.MAP).withSpec(activitySpec).withRequired(true);
        return spec;
    }

    /**
     * Converts a trigger declared in the service configuration. The resulting trigger has a stable identifier
     * derived from its name.
     */
    public static TriggerInfo fromConfig(YConfiguration config) throws ValidationException {
        var b = TriggerInfo.newBuilder()
                .setName(config.getString("name"))
                .setEnabled(config.getBoolean("enabled", true))
                .setSource(TriggerSource.CONFIG);
        b.setId(UUID.nameUUIDFromBytes(("config:" + b.getName()).getBytes(StandardCharsets.UTF_8)).toString());
        if (config.containsKey("description")) {
            b.setDescription(config.getString("description"));
        }
        if (config.containsKey("processor")) {
            b.setProcessor(config.getString("processor"));
        }
        if (config.containsKey("runAs")) {
            b.setRunAs(config.getString("runAs"));
        }

        var conditionConfig = config.getConfig("condition");
        if (conditionConfig.containsKey("alarm")) {
            var alarmConfig = conditionConfig.getConfig("alarm");
            var ab = AlarmCondition.newBuilder()
                    .setAlarmType(AlarmType.valueOf(alarmConfig.getString("type", AlarmType.PARAMETER.name())));
            if (alarmConfig.containsKey("subjects")) {
                ab.addAllSubjects(alarmConfig.getList("subjects"));
            }
            if (alarmConfig.containsKey("notifications")) {
                for (String notification : alarmConfig.<String> getList("notifications")) {
                    try {
                        ab.addNotifications(AlarmNotificationType.valueOf(notification));
                    } catch (IllegalArgumentException e) {
                        throw new ValidationException(new ValidationContext(config.getPath()),
                                "Unknown alarm notification '" + notification + "'");
                    }
                }
            }
            if (alarmConfig.containsKey("minSeverity")) {
                ab.setMinSeverity(AlarmSeverity.valueOf(alarmConfig.getString("minSeverity")));
            }
            if (alarmConfig.containsKey("includeShelved")) {
                ab.setIncludeShelved(alarmConfig.getBoolean("includeShelved"));
            }
            b.setAlarmCondition(ab);
        } else {
            var parameterConfig = conditionConfig.getConfig("parameter");
            var pb = ParameterCondition.newBuilder()
                    .setExpression(parameterConfig.getString("expression"));
            if (parameterConfig.containsKey("edge")) {
                pb.setEdge(Edge.valueOf(parameterConfig.getString("edge")));
            }
            if (parameterConfig.containsKey("holdTime")) {
                pb.setHoldTime(parseDuration(parameterConfig.getRoot().get("holdTime"), config.getPath()));
            }
            if (parameterConfig.containsKey("invalidAsFalse")) {
                pb.setInvalidAsFalse(parameterConfig.getBoolean("invalidAsFalse"));
            }
            b.setParameterCondition(pb);
        }

        if (config.containsKey("policy")) {
            var policyConfig = config.getConfig("policy");
            var pb = TriggerPolicy.newBuilder();
            if (policyConfig.containsKey("cooldown")) {
                pb.setCooldown(parseDuration(policyConfig.getRoot().get("cooldown"), config.getPath()));
            }
            if (policyConfig.containsKey("concurrency")) {
                pb.setConcurrency(Concurrency.valueOf(policyConfig.getString("concurrency")));
            }
            if (policyConfig.containsKey("requireConfirmation")) {
                pb.setRequireConfirmation(policyConfig.getBoolean("requireConfirmation"));
            }
            if (policyConfig.containsKey("confirmationTimeout")) {
                pb.setConfirmationTimeout(
                        parseDuration(policyConfig.getRoot().get("confirmationTimeout"), config.getPath()));
            }
            if (policyConfig.containsKey("maxFiringsPerHour")) {
                pb.setMaxFiringsPerHour(policyConfig.getInt("maxFiringsPerHour"));
            }
            b.setPolicy(pb);
        }

        var activityConfig = config.getConfig("activity");
        var activityb = ActivityDefinitionInfo.newBuilder()
                .setType(activityConfig.getString("type"));
        if (activityConfig.containsKey("args")) {
            activityb.setArgs(WellKnownTypes.toStruct(activityConfig.<String, Object> getMap("args")));
        }
        if (activityConfig.containsKey("label")) {
            activityb.setLabel(activityConfig.getString("label"));
        }
        b.setActivity(activityb);

        return withDefaults(b.build());
    }

    /**
     * Returns a copy of the trigger where all unset optional fields have their default value.
     */
    public static TriggerInfo withDefaults(TriggerInfo trigger) {
        var b = trigger.toBuilder();
        if (!b.hasEnabled()) {
            b.setEnabled(true);
        }
        if (!b.hasProcessor()) {
            b.setProcessor(DEFAULT_PROCESSOR);
        }
        if (!b.hasSource()) {
            b.setSource(TriggerSource.DATABASE);
        }
        if (b.hasAlarmCondition()) {
            var ab = b.getAlarmConditionBuilder();
            if (!ab.hasAlarmType()) {
                ab.setAlarmType(AlarmType.PARAMETER);
            }
            if (ab.getNotificationsCount() == 0) {
                ab.addNotifications(AlarmNotificationType.TRIGGERED);
                ab.addNotifications(AlarmNotificationType.SEVERITY_INCREASED);
            }
            if (!ab.hasMinSeverity()) {
                ab.setMinSeverity(AlarmSeverity.WATCH);
            }
            if (!ab.hasIncludeShelved()) {
                ab.setIncludeShelved(false);
            }
        }
        if (b.hasParameterCondition()) {
            var pb = b.getParameterConditionBuilder();
            if (!pb.hasEdge()) {
                pb.setEdge(Edge.RISING);
            }
            if (!pb.hasHoldTime()) {
                pb.setHoldTime(0);
            }
            if (!pb.hasInvalidAsFalse()) {
                pb.setInvalidAsFalse(true);
            }
        }
        var pb = b.getPolicyBuilder();
        if (!pb.hasCooldown()) {
            pb.setCooldown(DEFAULT_COOLDOWN);
        }
        if (!pb.hasConcurrency()) {
            pb.setConcurrency(Concurrency.SKIP);
        }
        if (!pb.hasRequireConfirmation()) {
            pb.setRequireConfirmation(false);
        }
        if (!pb.hasConfirmationTimeout()) {
            pb.setConfirmationTimeout(DEFAULT_CONFIRMATION_TIMEOUT);
        }
        if (!pb.hasMaxFiringsPerHour()) {
            pb.setMaxFiringsPerHour(0);
        }
        return b.build();
    }

    /**
     * Checks the parts of a definition that do not depend on the Mission Database or on the available activity
     * types.
     *
     * @return a list of problems, empty if the definition is valid
     */
    public static List<String> validate(TriggerInfo trigger) {
        var errors = new ArrayList<String>();
        if (!trigger.hasName() || trigger.getName().isBlank()) {
            errors.add("Name is required");
        }
        if (trigger.hasAlarmCondition() == trigger.hasParameterCondition()) {
            errors.add("Specify exactly one of alarmCondition or parameterCondition");
        }
        if (trigger.hasParameterCondition()) {
            var condition = trigger.getParameterCondition();
            if (!condition.hasExpression() || condition.getExpression().isBlank()) {
                errors.add("Parameter condition requires an expression");
            }
            if (condition.getHoldTime() < 0) {
                errors.add("holdTime cannot be negative");
            }
        }
        if (!trigger.hasActivity() || !trigger.getActivity().hasType()) {
            errors.add("Activity type is required");
        }
        var policy = trigger.getPolicy();
        if (policy.getCooldown() < 0) {
            errors.add("cooldown cannot be negative");
        }
        if (policy.getConfirmationTimeout() < 0) {
            errors.add("confirmationTimeout cannot be negative");
        }
        if (policy.getMaxFiringsPerHour() < 0) {
            errors.add("maxFiringsPerHour cannot be negative");
        }
        return errors;
    }

    /**
     * Parses a duration in milliseconds. Numbers are interpreted as seconds. Strings may have a unit suffix:
     * {@code ms}, {@code s}, {@code m}, {@code h} or {@code d} (for example {@code 10m}). Strings without suffix are
     * interpreted as seconds.
     */
    public static long parseDuration(Object value, String path) throws ValidationException {
        if (value instanceof Number n) {
            return Math.round(n.doubleValue() * 1000);
        } else if (value instanceof String s) {
            var m = DURATION_PATTERN.matcher(s.trim());
            if (m.matches()) {
                var amount = Double.parseDouble(m.group(1));
                var unit = m.group(2) != null ? m.group(2) : "s";
                var factor = switch (unit) {
                case "ms" -> 1;
                case "s" -> 1000;
                case "m" -> 60_000;
                case "h" -> 3_600_000;
                case "d" -> 86_400_000;
                default -> throw new IllegalStateException();
                };
                return Math.round(amount * factor);
            }
        }
        throw new ValidationException(new ValidationContext(path), "Invalid duration '" + value + "'. Use for example 500ms, 30s, 10m, 1h");
    }

    /**
     * Returns the activity arguments as a Java map
     */
    static Map<String, Object> getActivityArgs(TriggerInfo trigger) {
        var activity = trigger.getActivity();
        return activity.hasArgs()
                ? org.yamcs.http.api.GpbWellKnownHelper.toJava(activity.getArgs())
                : new java.util.LinkedHashMap<>();
    }
}
