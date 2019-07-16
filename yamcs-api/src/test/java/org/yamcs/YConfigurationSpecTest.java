package org.yamcs;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.yamcs.YConfigurationSpec.OptionType;

public class YConfigurationSpecTest {

    @Test
    public void testOptional() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING).withRequired(false);
        spec.validate(of(/* no value */));
    }

    @Test
    public void testRequired() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING).withRequired(true);
        spec.validate(of("bla", "a value"));
    }

    @Test(expected = ValidationException.class)
    public void testRequiredButMissing() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING).withRequired(true);
        spec.validate(of());
    }

    @Test(expected = ValidationException.class)
    public void testUnknownOption() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.validate(of("bla", "a value"));
    }

    @Test(expected = ValidationException.class)
    public void testWrongType() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING);
        spec.validate(of("bla", 123));
    }

    @Test
    public void testChoices() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other");
        spec.validate(of("bla", "valid"));
    }

    @Test(expected = ValidationException.class)
    public void testInvalidChoice() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other");
        spec.validate(of("bla", "this is wrong"));
    }

    @Test
    public void testElementType() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.validate(of("bla", asList(123, 456)));
    }

    @Test(expected = ValidationException.class)
    public void testWrongElementType() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.validate(of("bla", asList("str1", "str2")));
    }

    @Test
    public void testMap() throws ValidationException {
        YConfigurationSpec blaSpec = new YConfigurationSpec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
        spec.validate(of("bla", of("subkey", 123)));
    }

    @Test(expected = ValidationException.class)
    public void testSubMapValidation() throws ValidationException {
        YConfigurationSpec blaSpec = new YConfigurationSpec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.MAP).withSpec(blaSpec);
        spec.validate(of("bla", of("wrongKey", 123)));
    }

    @Test
    public void testListofMap() throws ValidationException {
        YConfigurationSpec blaSpec = new YConfigurationSpec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(blaSpec);

        spec.validate(of("bla", asList(of("subkey", 123))));
    }

    @Test(expected = ValidationException.class)
    public void testListofMapValidation() throws ValidationException {
        YConfigurationSpec blaSpec = new YConfigurationSpec();
        blaSpec.addOption("subkey", OptionType.INTEGER);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(blaSpec);

        spec.validate(of("bla", asList(of("wrong", 123))));
    }

    @Test
    public void testDefaultValue() throws ValidationException {
        YConfigurationSpec spec = new YConfigurationSpec();
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
        YConfigurationSpec subSpec = new YConfigurationSpec();
        subSpec.addOption("subkey", OptionType.INTEGER).withDefault(123);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.MAP)
                .withSpec(subSpec)
                .withApplySpecDefaults(true);

        Map<String, Object> result = spec.validate(of());
        assertTrue(result.containsKey("bla"));
        Map<String, Object> blaArg = (Map<String, Object>) result.get("bla");
        assertEquals(123, blaArg.get("subkey"));
    }

    @Test
    public void testChoicesDefault() throws ValidationException {
        YConfigurationSpec subSpec = new YConfigurationSpec();
        subSpec.addOption("subkey", OptionType.INTEGER).withDefault(33);

        YConfigurationSpec spec = new YConfigurationSpec();
        spec.addOption("bla", OptionType.STRING)
                .withChoices("valid", "other")
                .withDefault("other");
        spec.addOption("bloe", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(subSpec)
                .withRequired(true)
                .withDeprecationMessage("gone with the wind");
        Map<String, Object> result = spec.validate(of("bloe", asList(of(), of())));
        System.out.println(result);
    }
}
