package org.yamcs;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;

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
