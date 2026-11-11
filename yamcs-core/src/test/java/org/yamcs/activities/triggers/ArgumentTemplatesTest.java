package org.yamcs.activities.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ArgumentTemplatesTest {

    private final Map<String, Object> context = Map.of(
            "trigger.name", "low-voltage",
            "alarm.severity", "CRITICAL",
            "value", 9.5,
            "param:/a/b", 3);

    private final ArgumentTemplates templates = new ArgumentTemplates(context,
            name -> name.equals("/x/y") ? "live" : null);

    @Test
    public void testSinglePlaceholderKeepsType() {
        var result = templates.resolve(args("v", "${value}"));
        assertEquals(9.5, result.get("v"));
    }

    @Test
    public void testInterpolation() {
        var result = templates.resolve(args("msg", "${trigger.name}: ${alarm.severity} (${value})"));
        assertEquals("low-voltage: CRITICAL (9.5)", result.get("msg"));
    }

    @Test
    public void testNested() {
        var nested = new LinkedHashMap<String, Object>();
        nested.put("voltage", "${value}");
        var result = templates.resolve(args("args", nested, "list", List.of("--severity", "${alarm.severity}")));
        assertEquals(Map.of("voltage", 9.5), result.get("args"));
        assertEquals(List.of("--severity", "CRITICAL"), result.get("list"));
    }

    @Test
    public void testParameterLookup() {
        var result = templates.resolve(args("a", "${param:/a/b}", "b", "${param:/x/y}"));
        assertEquals(3, result.get("a"));
        assertEquals("live", result.get("b"));
        assertThrows(IllegalArgumentException.class, () -> templates.resolve(args("c", "${param:/nope}")));
    }

    @Test
    public void testEscapeAndPassThrough() {
        var result = templates.resolve(args("a", "cost: $${value}", "b", 42, "c", "plain $ text"));
        assertEquals("cost: ${value}", result.get("a"));
        assertEquals(42, result.get("b"));
        assertEquals("plain $ text", result.get("c"));
    }

    @Test
    public void testUnknownPlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> templates.resolve(args("a", "${nope}")));
    }

    private static Map<String, Object> args(Object... keyValues) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
