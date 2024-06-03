package org.yamcs;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.Spec.OptionType;
import org.yamcs.tctm.ccsds.TcManagedParameters.PriorityScheme;

public class SpecTest {

    @Test
    public void testOptional() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING).withRequired(false);
        spec.validate(of(/* no value */));
    }

    @Test
    public void testRequired() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING).withRequired(true);
        spec.validate(of("bla", "a value"));
    }

    @Test
    public void testRequiredButMissing() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.addOption("bla", OptionType.STRING).withRequired(true);
            spec.validate(of());
        });
    }

    @Test
    public void testUnknownOption() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.validate(of("bla", "a value"));
        });
    }

    @Test
    public void testAny() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("whatever", OptionType.ANY);

        Map<String, Object> result = spec.validate(of("whatever", "a string"));
        assertEquals("a string", result.get("whatever"));

        result = spec.validate(of("whatever", 123));
        assertEquals(123, result.get("whatever"));
    }

    @Test
    public void testWrongType() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.addOption("bla", OptionType.STRING);
            spec.validate(of("bla", 123));
        });
    }

    @Test
    public void testChoices() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other");
        spec.validate(of("bla", "valid"));
    }

    @Test
    public void testInvalidChoice() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.addOption("bla", OptionType.STRING)
                    .withChoices("valid", "other");
            spec.validate(of("bla", "this is wrong"));
        });
    }

    @Test
    public void testElementType() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.validate(of("bla", asList(123, 456)));
    }

    @Test
    public void testWrongElementType() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
            spec.validate(of("bla", asList("str1", "str2")));
        });
    }

    @Test
    public void testMap() throws ValidationException {
        Spec blaSpec = new Spec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
        spec.validate(of("bla", of("subkey", 123)));
    }

    @Test
    public void testSubMapValidation() {
        assertThrows(ValidationException.class, () -> {
            Spec blaSpec = new Spec();
            blaSpec.addOption("subkey", OptionType.INTEGER);

            Spec spec = new Spec();
            spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
            spec.validate(of("bla", of("wrongKey", 123)));
        });
    }

    @Test
    public void testListOfMap() throws ValidationException {
        Spec blaSpec = new Spec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(blaSpec);

        spec.validate(of("bla", asList(of("subkey", 123))));
    }

    @Test
    public void testListofMapValidation() {
        assertThrows(ValidationException.class, () -> {
            Spec blaSpec = new Spec();
            blaSpec.addOption("subkey", OptionType.INTEGER);

            Spec spec = new Spec();
            spec.addOption("bla", OptionType.LIST)
                    .withElementType(OptionType.MAP)
                    .withSpec(blaSpec);

            spec.validate(of("bla", asList(of("wrong", 123))));
        });
    }

    @Test
    public void testLong() throws ValidationException {
        var spec = new Spec();
        spec.addOption("large1", OptionType.INTEGER);
        spec.addOption("large2", OptionType.INTEGER).withDefault(123L);
        spec.addOption("large3", OptionType.INTEGER).withDefault(123);

        var result = spec.validate(of());
        assertEquals(null, result.get("large1"));
        assertEquals(123L, result.get("large2"));
        assertEquals(123, result.get("large3"));

        result = spec.validate(of("large1", 5368709120L));
        assertEquals(5368709120L, result.get("large1"));
        result = spec.validate(of("large2", 5368709120L));
        assertEquals(5368709120L, result.get("large2"));
        result = spec.validate(of("large3", 5368709120L));
        assertEquals(5368709120L, result.get("large3"));
    }

    @Test
    public void testDefaultValue() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla1", OptionType.INTEGER);
        spec.addOption("bla2", OptionType.INTEGER).withDefault(123);

        Map<String, Object> result = spec.validate(of());
        assertEquals(null, result.get("bla1"));
        assertEquals(123, result.get("bla2"));

        result = spec.validate(of("bla1", 456, "bla2", 456));
        assertEquals(456, result.get("bla1"));
        assertEquals(456, result.get("bla2"));
    }

    @Test
    public void testDefaultValidation() throws ValidationException {
        var spec = new Spec();
        spec.addOption("bla1", OptionType.INTEGER);
        assertThrows(ValidationException.class, () -> {
            spec.validate(of("bla1", "text"));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testApplyDefaults() throws ValidationException {
        Spec subSpec = new Spec();
        subSpec.addOption("subkey", OptionType.INTEGER).withDefault(123);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.MAP)
                .withSpec(subSpec)
                .withApplySpecDefaults(true);

        Map<String, Object> result = spec.validate(of());
        assertTrue(result.containsKey("bla"));
        Map<String, Object> blaArg = (Map<String, Object>) result.get("bla");
        assertEquals(123, blaArg.get("subkey"));
    }

    @Test
    public void testEnum() throws ValidationException {
        var spec = new Spec();
        spec.addOption("bla1", OptionType.STRING)
                .withChoices(PriorityScheme.class)
                .withDefault("FIFO");
        spec.addOption("bla2", OptionType.STRING)
                .withChoices(PriorityScheme.class)
                .withDefault(PriorityScheme.FIFO);
        Map<String, Object> result = spec.validate(of());
        assertEquals("FIFO", result.get("bla1"));
        assertEquals("FIFO", result.get("bla2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSecret() throws ValidationException {
        Spec subSpec = new Spec();
        subSpec.addOption("subkey1", OptionType.INTEGER);
        subSpec.addOption("subkey2", OptionType.INTEGER).withSecret(true);

        Spec spec = new Spec();
        spec.addOption("bla1", OptionType.STRING);
        spec.addOption("bla2", OptionType.STRING).withSecret(true);
        spec.addOption("bloe", OptionType.MAP)
                .withSpec(subSpec);

        Map<String, Object> args = of("bla1", "abc", "bla2", "def",
                "bloe", of("subkey1", 123, "subkey2", 456));

        Map<String, Object> unsafeResult = spec.validate(args);
        Map<String, Object> safeResult = spec.removeSecrets(unsafeResult);

        assertTrue(unsafeResult.containsKey("bla1"));
        assertTrue(safeResult.containsKey("bla1"));

        assertTrue(unsafeResult.containsKey("bla2"));
        assertFalse(safeResult.containsKey("bla2"));

        Map<String, Object> unsafeSubResult = (Map<String, Object>) unsafeResult.get("bloe");
        Map<String, Object> safeSubResult = (Map<String, Object>) safeResult.get("bloe");

        assertTrue(unsafeSubResult.containsKey("subkey1"));
        assertTrue(safeSubResult.containsKey("subkey1"));

        assertTrue(unsafeSubResult.containsKey("subkey2"));
        assertFalse(safeSubResult.containsKey("subkey2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListOfMapSecrets() throws ValidationException {
        Spec subSpec = new Spec();
        subSpec.addOption("subkey1", OptionType.INTEGER);
        subSpec.addOption("subkey2", OptionType.INTEGER).withSecret(true);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(subSpec);

        Map<String, Object> args = of("bla", asList(of("subkey1", 123, "subkey2", 456)));

        Map<String, Object> unsafeResult = spec.validate(args);
        Map<String, Object> safeResult = spec.removeSecrets(unsafeResult);

        List<Object> unsafeSubResult = (List<Object>) unsafeResult.get("bla");
        List<Object> safeSubResult = (List<Object>) safeResult.get("bla");

        Map<String, Object> unsafeEl = (Map<String, Object>) unsafeSubResult.get(0);
        Map<String, Object> safeEl = (Map<String, Object>) safeSubResult.get(0);

        assertTrue(unsafeEl.containsKey("subkey1"));
        assertTrue(safeEl.containsKey("subkey1"));

        assertTrue(unsafeEl.containsKey("subkey2"));
        assertFalse(safeEl.containsKey("subkey2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMaskListOfMapSecrets() throws ValidationException {
        Spec subSpec = new Spec();
        subSpec.addOption("subkey1", OptionType.INTEGER);
        subSpec.addOption("subkey2", OptionType.INTEGER).withSecret(true);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(subSpec);

        Map<String, Object> args = of("bla", asList(of("subkey1", 123, "subkey2", 456)));

        Map<String, Object> unsafeResult = spec.validate(args);
        Map<String, Object> safeResult = spec.maskSecrets(unsafeResult);

        List<Object> unsafeSubResult = (List<Object>) unsafeResult.get("bla");
        List<Object> safeSubResult = (List<Object>) safeResult.get("bla");

        Map<String, Object> unsafeEl = (Map<String, Object>) unsafeSubResult.get(0);
        Map<String, Object> safeEl = (Map<String, Object>) safeSubResult.get(0);

        assertEquals(123, unsafeEl.get("subkey1"));
        assertEquals(123, safeEl.get("subkey1"));

        assertEquals(456, unsafeEl.get("subkey2"));
        assertEquals("*****", safeEl.get("subkey2"));
    }

    @Test
    public void testListOrElement() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("command", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.INTEGER);

        Map<String, Object> result1 = spec.validate(of("command", asList(123)));
        Map<String, Object> result2 = spec.validate(of("command", 123));
        assertEquals(result1, result2);
        assertEquals(1, result1.size());
        assertEquals(123, ((List<?>) result1.get("command")).get(0));
    }

    @Test
    public void testListOrElementWithDefault() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("command", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.INTEGER)
                .withDefault(123);

        Map<String, Object> result1 = spec.validate(of());
        assertEquals(1, result1.size());
        assertEquals(123, ((List<?>) result1.get("command")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAnySpec() throws ValidationException {
        // This is similar to OptionType.ANY, but allows to enforce that
        // something the upper element is a LIST or MAP, rather than ANY.
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.MAP).withSpec(Spec.ANY);

        Map<String, Object> result1 = spec.validate(of("bla", of("anything", 123)));
        Map<String, Object> blaEl = (Map<String, Object>) result1.get("bla");
        assertEquals(123, blaEl.get("anything"));
    }

    @Test
    public void testAlias() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.ANY).withAliases("bloe");

        Map<String, Object> result1 = spec.validate(of("bla", 123));
        assertEquals(123, result1.get("bla"));
        Map<String, Object> result2 = spec.validate(of("bloe", 123));
        assertEquals(123, result2.get("bla"));
    }

    @Test
    public void testAliasExists() {
        assertThrows(ValidationException.class, () -> {
            Spec spec = new Spec();
            spec.addOption("bla", OptionType.ANY).withAliases("bloe");

            spec.validate(of("bla", 123, "bloe", 456));
        });
    }

    @Test
    public void testAliasWithDefault() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.ANY).withDefault(555).withAliases("bloe");

        Map<String, Object> result2 = spec.validate(of("bloe", 123));
        assertEquals(123, result2.get("bla"));
    }

    @Test
    public void testListOfMapsWithNoValue() throws ValidationException {
        Map<String, Object> modulesConfig = new HashMap<>();
        modulesConfig.put("modules1", null);
        modulesConfig.put("modules2", null);

        Spec spec = new Spec();
        spec.addOption("modules1", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(new Spec());

        spec.addOption("modules2", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(new Spec())
                .withDefault(new ArrayList<>());

        Map<String, Object> result = spec.validate(modulesConfig);
        assertNull(result.get("modules1"));
        assertNull(result.get("modules2")); // The "key" is specified, so default does not apply
    }

    @Test
    public void testRemoveOption() {
        var spec = new Spec();
        assertFalse(spec.containsOption("bla"));
        spec.addOption("bla", OptionType.STRING);
        assertTrue(spec.containsOption("bla"));
        spec.removeOption("bla");
        assertFalse(spec.containsOption("bla"));
    }

    /**
     * Conversions from string are allowed, because they are useful when using property expansions of the form on
     * BOOLEAN options.
     * <p>
     * The values tested here, match the definition in both {@link YConfiguration} and {@link Spec}
     */
    @Test
    public void testBooleanStrings() throws ValidationException {
        var spec = new Spec();
        spec.addOption("foo", OptionType.BOOLEAN);

        var result = spec.validate(of("foo", "true"));
        assertEquals(true, result.get("foo"));

        result = spec.validate(of("foo", "yes"));
        assertEquals(true, result.get("foo"));

        result = spec.validate(of("foo", "on"));
        assertEquals(true, result.get("foo"));

        result = spec.validate(of("foo", "false"));
        assertEquals(false, result.get("foo"));

        result = spec.validate(of("foo", "no"));
        assertEquals(false, result.get("foo"));

        result = spec.validate(of("foo", "off"));
        assertEquals(false, result.get("foo"));
    }

    @Test
    public void testIntegerStrings() throws ValidationException {
        var spec = new Spec();
        spec.addOption("foo", OptionType.INTEGER);

        var result = spec.validate(of("foo", "123"));
        assertEquals(123, result.get("foo"));

        result = spec.validate(of("foo", "12345678901"));
        assertEquals(12345678901L, result.get("foo"));
    }

    /**
     * Tolerate doubles representing whole numbers. It happens when dealing with GPB Structs.
     */
    @Test
    public void testIntegerFloats() throws ValidationException {
        var spec = new Spec();
        spec.addOption("foo", OptionType.INTEGER);

        var result = spec.validate(of("foo", 123.0f));
        assertEquals(123, result.get("foo"));

        result = spec.validate(of("foo", 123.0d));
        assertEquals(123, result.get("foo"));

        assertThrows(ValidationException.class, () -> {
            spec.validate(of("foo", 123.1));
        });
    }

    @Test
    public void testFloatStrings() throws ValidationException {
        var spec = new Spec();
        spec.addOption("foo", OptionType.FLOAT);

        var result = spec.validate(of("foo", "123.45"));
        assertEquals(123.45, result.get("foo"));
    }
}
