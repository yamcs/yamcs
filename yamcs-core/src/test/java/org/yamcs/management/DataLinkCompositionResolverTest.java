package org.yamcs.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class DataLinkCompositionResolverTest {

    private static final YConfiguration NAMED_LINK = YConfiguration.wrap(Map.of(
            "name", "TM_IN",
            "class", "example.ComposedLink",
            "composition", "spacecraft-tm"));

    @Test
    public void testResolvesSelectedMode() {
        var resolver = resolver("checkout", Map.of(
                "protocol", definition("example.Protocol", Map.of("spacecraftId", 1)),
                "transport", definition("example.Transport", Map.of("port", 1000))),
                Map.of("checkout", Map.of("spacecraft-tm", Map.of(
                        "protocol", "protocol",
                        "transport", "transport"))));

        YConfiguration resolved = resolver.resolve(NAMED_LINK);

        assertEquals("example.Protocol", resolved.getConfig("components").getConfig("protocol").getString("class"));
        assertEquals("example.Transport",
                resolved.getConfig("components").getConfig("transport").getString("class"));
        assertEquals("spacecraft-tm", resolved.getString("composition"));
    }

    @Test
    public void testLegacyAndInlineComposedLinksPassWithoutGlobalSection() {
        var resolver = new DataLinkCompositionResolver(YConfiguration.emptyConfig());
        var legacyLink = YConfiguration.wrap(Map.of("name", "legacy", "class", "example.Legacy"));
        var inlineLink = YConfiguration.wrap(Map.of(
                "name", "inline",
                "class", "example.ComposedLink",
                "components", Map.of(
                        "protocol", definition("example.Protocol"),
                        "transport", definition("example.Transport"))));

        assertSame(legacyLink, resolver.resolve(legacyLink));
        assertSame(inlineLink, resolver.resolve(inlineLink));
    }

    @Test
    public void testNamedCompositionRequiresGlobalSection() {
        assertConfigurationError(new DataLinkCompositionResolver(YConfiguration.emptyConfig()), NAMED_LINK,
                "Data link 'TM_IN' requires dataLinkComposition at instance level");
    }

    @Test
    public void testRejectsCompositionAndInlineComponentsTogether() {
        var link = YConfiguration.wrap(Map.of(
                "name", "TM_IN",
                "class", "example.ComposedLink",
                "composition", "spacecraft-tm",
                "components", Map.of()));

        assertConfigurationError(new DataLinkCompositionResolver(YConfiguration.emptyConfig()), link,
                "cannot specify both composition and inline components");
    }

    @Test
    public void testRejectsUnknownModeAndComposition() {
        var unknownMode = resolver("missing", Map.of(), Map.of());
        assertConfigurationError(unknownMode, NAMED_LINK, "dataLinkComposition.modes.missing");

        var unknownComposition = resolver("checkout", Map.of(), Map.of("checkout", Map.of()));
        assertConfigurationError(unknownComposition, NAMED_LINK,
                "dataLinkComposition.modes.checkout.spacecraft-tm");
    }

    @Test
    public void testRejectsUnknownComponentAndSlot() {
        var unknownComponent = resolver("checkout",
                Map.of("protocol", definition("example.Protocol")),
                Map.of("checkout", Map.of("spacecraft-tm", Map.of(
                        "protocol", "protocol", "transport", "missing"))));
        assertConfigurationError(unknownComponent, NAMED_LINK, "references unknown component 'missing'");

        var unknownSlot = resolver("checkout", Map.of(
                "protocol", definition("example.Protocol"),
                "transport", definition("example.Transport"),
                "extra", definition("example.Extra")),
                Map.of("checkout", Map.of("spacecraft-tm", Map.of(
                        "protocol", "protocol", "transport", "transport", "extra", "extra"))));
        assertConfigurationError(unknownSlot, NAMED_LINK, "uses unsupported slot 'extra'");
    }

    @Test
    public void testRejectsMissingMandatorySlots() {
        Map<String, Object> components = Map.of(
                "protocol", definition("example.Protocol"),
                "transport", definition("example.Transport"));

        var missingProtocol = resolver("checkout", components,
                Map.of("checkout", Map.of("spacecraft-tm", Map.of("transport", "transport"))));
        assertConfigurationError(missingProtocol, NAMED_LINK, "requires protocol and transport slots");

        var missingTransport = resolver("checkout", components,
                Map.of("checkout", Map.of("spacecraft-tm", Map.of("protocol", "protocol"))));
        assertConfigurationError(missingTransport, NAMED_LINK, "requires protocol and transport slots");
    }

    @Test
    public void testRejectsInvalidComponentDefinitionAndReference() {
        var missingClass = resolver("checkout", Map.of(
                "protocol", Map.of("args", Map.of()),
                "transport", definition("example.Transport")),
                Map.of("checkout", Map.of("spacecraft-tm", Map.of(
                        "protocol", "protocol", "transport", "transport"))));
        assertConfigurationError(missingClass, NAMED_LINK, "component 'protocol' is missing class");

        var nonStringReference = resolver("checkout", Map.of(
                "protocol", definition("example.Protocol"),
                "transport", definition("example.Transport")),
                Map.of("checkout", Map.of("spacecraft-tm", Map.of(
                        "protocol", "protocol", "transport", Map.of()))));
        assertConfigurationError(nonStringReference, NAMED_LINK,
                "component reference at spacecraft-tm.transport must be a string");
    }

    private static DataLinkCompositionResolver resolver(String selectedMode, Map<String, Object> components,
            Map<String, Object> modes) {
        return new DataLinkCompositionResolver(YConfiguration.wrap(Map.of(
                "dataLinkComposition", Map.of(
                        "selectedMode", selectedMode,
                        "components", components,
                        "modes", modes))));
    }

    private static Map<String, Object> definition(String className) {
        return definition(className, Map.of());
    }

    private static Map<String, Object> definition(String className, Map<String, Object> args) {
        return Map.of("class", className, "args", args);
    }

    private static void assertConfigurationError(DataLinkCompositionResolver resolver, YConfiguration link,
            String expectedMessage) {
        var e = assertThrows(ConfigurationException.class, () -> resolver.resolve(link));
        assertTrue(e.getMessage().contains(expectedMessage), e.getMessage());
    }
}
