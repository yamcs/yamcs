package org.yamcs.activities.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.activities.ActivityDefinitionInfo;
import org.yamcs.protobuf.activities.AlarmCondition.AlarmType;
import org.yamcs.protobuf.activities.ParameterCondition;
import org.yamcs.protobuf.activities.ParameterCondition.Edge;
import org.yamcs.protobuf.activities.TriggerInfo;
import org.yamcs.protobuf.activities.TriggerPolicy.Concurrency;
import org.yamcs.protobuf.activities.TriggerSource;

public class TriggerDefinitionsTest {

    @Test
    public void testParseDuration() throws ValidationException {
        assertEquals(500, TriggerDefinitions.parseDuration("500ms", ""));
        assertEquals(30_000, TriggerDefinitions.parseDuration("30s", ""));
        assertEquals(30_000, TriggerDefinitions.parseDuration("30", ""));
        assertEquals(600_000, TriggerDefinitions.parseDuration("10m", ""));
        assertEquals(5_400_000, TriggerDefinitions.parseDuration("1.5h", ""));
        assertEquals(86_400_000, TriggerDefinitions.parseDuration("1d", ""));
        assertEquals(2_500, TriggerDefinitions.parseDuration(2.5, ""));
        assertThrows(ValidationException.class, () -> TriggerDefinitions.parseDuration("10 minutes", ""));
        assertThrows(ValidationException.class, () -> TriggerDefinitions.parseDuration(true, ""));
    }

    @Test
    public void testFromConfig() throws ValidationException {
        Map<String, Object> raw = Map.of(
                "name", "battery-critical",
                "condition", Map.of("alarm", Map.of(
                        "subjects", "/YSS/SIMULATOR/BatteryVoltage*",
                        "minSeverity", "CRITICAL")),
                "policy", Map.of("cooldown", "10m", "requireConfirmation", true),
                "activity", Map.of(
                        "type", "SCRIPT",
                        "args", Map.of("script", "safe_mode.py")));
        var config = YConfiguration.wrap(TriggerDefinitions.getConfigSpec().validate(raw));

        var trigger = TriggerDefinitions.fromConfig(config);
        assertEquals("battery-critical", trigger.getName());
        assertEquals(TriggerSource.CONFIG, trigger.getSource());
        assertTrue(trigger.getEnabled());
        assertEquals("realtime", trigger.getProcessor());

        var condition = trigger.getAlarmCondition();
        assertEquals(AlarmType.PARAMETER, condition.getAlarmType());
        assertEquals(List.of("/YSS/SIMULATOR/BatteryVoltage*"), condition.getSubjectsList());
        assertEquals(AlarmSeverity.CRITICAL, condition.getMinSeverity());
        assertEquals(List.of(AlarmNotificationType.TRIGGERED, AlarmNotificationType.SEVERITY_INCREASED),
                condition.getNotificationsList());

        var policy = trigger.getPolicy();
        assertEquals(600_000, policy.getCooldown());
        assertTrue(policy.getRequireConfirmation());
        assertEquals(Concurrency.SKIP, policy.getConcurrency());

        assertEquals("SCRIPT", trigger.getActivity().getType());
        assertEquals("safe_mode.py", trigger.getActivity().getArgs().getFieldsOrThrow("script").getStringValue());

        // Identifier is stable across restarts
        assertEquals(trigger.getId(), TriggerDefinitions.fromConfig(config).getId());
        assertTrue(TriggerDefinitions.validate(trigger).isEmpty());
    }

    @Test
    public void testConfigRequiresOneCondition() {
        Map<String, Object> raw = Map.of(
                "name", "x",
                "condition", Map.of(),
                "activity", Map.of("type", "MANUAL"));
        assertThrows(ValidationException.class, () -> TriggerDefinitions.getConfigSpec().validate(raw));
    }

    @Test
    public void testDefaultsAndValidation() {
        var trigger = TriggerDefinitions.withDefaults(TriggerInfo.newBuilder()
                .setName("t")
                .setParameterCondition(ParameterCondition.newBuilder().setExpression("/a < 1"))
                .setActivity(ActivityDefinitionInfo.newBuilder().setType("MANUAL"))
                .build());
        assertEquals(Edge.RISING, trigger.getParameterCondition().getEdge());
        assertTrue(trigger.getParameterCondition().getInvalidAsFalse());
        assertEquals(TriggerDefinitions.DEFAULT_COOLDOWN, trigger.getPolicy().getCooldown());
        assertTrue(TriggerDefinitions.validate(trigger).isEmpty());

        var invalid = TriggerDefinitions.withDefaults(TriggerInfo.newBuilder().build());
        var errors = TriggerDefinitions.validate(invalid);
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("Name is required"));
        assertTrue(errors.contains("Activity type is required"));
    }

    @Test
    public void testGlob() {
        var p = AlarmTriggerMonitor.globToPattern("/YSS/SIMULATOR/Battery*");
        assertTrue(p.matcher("/YSS/SIMULATOR/BatteryVoltage1").matches());
        assertFalse(p.matcher("/YSS/SIMULATOR/Mode").matches());

        p = AlarmTriggerMonitor.globToPattern("src/type?.x");
        assertTrue(p.matcher("src/type1.x").matches());
        assertFalse(p.matcher("src/type1ax").matches());
    }
}
