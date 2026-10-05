package org.yamcs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class YamcsServerInstanceSpecTest {

    @Test
    public void testDataLinkCompositionIsStructurallyOptional() throws ValidationException {
        Spec spec = YamcsServerInstance.getSpec();
        Spec.Option option = spec.getOption("dataLinkComposition");

        assertNotNull(option);
        assertFalse(option.isRequired());
        assertTrue(option.getSpec().getOption("selectedMode").isRequired());
        assertTrue(option.getSpec().getOption("components").isRequired());
        assertTrue(option.getSpec().getOption("modes").isRequired());

        spec.validate(Map.of());
    }

    @Test
    public void testRejectsEveryPartialDataLinkComposition() {
        Spec spec = YamcsServerInstance.getSpec();
        List<Map<String, Object>> partialSections = List.of(
                Map.of(),
                Map.of("selectedMode", "nominal"),
                Map.of("components", Map.of()),
                Map.of("modes", Map.of()),
                Map.of("selectedMode", "nominal", "components", Map.of()),
                Map.of("selectedMode", "nominal", "modes", Map.of()),
                Map.of("components", Map.of(), "modes", Map.of()));

        for (var partialSection : partialSections) {
            assertThrows(ValidationException.class,
                    () -> spec.validate(Map.of("dataLinkComposition", partialSection)));
        }
    }

    @Test
    public void testAcceptsCompleteDataLinkComposition() throws ValidationException {
        YamcsServerInstance.getSpec().validate(Map.of("dataLinkComposition", Map.of(
                "selectedMode", "${env.MCS_DATALINK_MODE:nominal}",
                "components", Map.of("protocol", Map.of("class", "example.Protocol")),
                "modes", Map.of("nominal", Map.of()))));
    }

    @Test
    public void testRejectsFormerTopLevelOptions() {
        Spec spec = YamcsServerInstance.getSpec();

        assertThrows(ValidationException.class, () -> spec.validate(Map.of("dataLinkMode", "nominal")));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("dataLinkComponents", Map.of())));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("dataLinkModes", Map.of())));
    }
}
