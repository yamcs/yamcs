package org.yamcs.api;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.yamcs.api.Spec.OptionType;

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

    @Test(expected = ValidationException.class)
    public void testRequiredButMissing() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING).withRequired(true);
        spec.validate(of());
    }

    @Test(expected = ValidationException.class)
    public void testUnknownOption() throws ValidationException {
        Spec spec = new Spec();
        spec.validate(of("bla", "a value"));
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

    @Test(expected = ValidationException.class)
    public void testWrongType() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING);
        spec.validate(of("bla", 123));
    }

    @Test
    public void testChoices() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other");
        spec.validate(of("bla", "valid"));
    }

    @Test(expected = ValidationException.class)
    public void testInvalidChoice() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other");
        spec.validate(of("bla", "this is wrong"));
    }

    @Test
    public void testElementType() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.validate(of("bla", asList(123, 456)));
    }

    @Test(expected = ValidationException.class)
    public void testWrongElementType() throws ValidationException {
        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.validate(of("bla", asList("str1", "str2")));
    }

    @Test
    public void testMap() throws ValidationException {
        Spec blaSpec = new Spec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
        spec.validate(of("bla", of("subkey", 123)));
    }

    @Test(expected = ValidationException.class)
    public void testSubMapValidation() throws ValidationException {
        Spec blaSpec = new Spec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
        spec.validate(of("bla", of("wrongKey", 123)));
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

    @Test(expected = ValidationException.class)
    public void testListofMapValidation() throws ValidationException {
        Spec blaSpec = new Spec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(blaSpec);

        spec.validate(of("bla", asList(of("wrong", 123))));
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
}
