package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

public class PusJtyuService5Test {

    private static Mdb mdb;

    @BeforeAll
    static void beforeAll() throws Exception {
        YConfiguration.setupTest(null);
        MdbFactory.reset();

        Path mdbPath = resolveSimulatorMdb();
        var config = YConfiguration.wrap(Map.of(
                "type", "xtce",
                "spec", mdbPath.toString()));
        mdb = MdbFactory.createInstance(List.of(config), false, false);
    }

    @Test
    void exposesService5EventContainersFromJtyuMdb() {
        assertContainerExists("/jTYU/informative_event_packet");
        assertContainerExists("/jTYU/low_event_packet");
        assertContainerExists("/jTYU/medium_event_packet");
        assertContainerExists("/jTYU/high_event_packet");
    }

    @Test
    void exposesEventDefinitionEnumerationFromJtyuMdb() {
        assertEventLabel(1, "IsAttitudeNominal");
        assertEventLabel(2, "MagStatus");
    }

    @Test
    void exposesService5CommandsFromJtyuMdb() {
        assertCommandExists("/jTYU/enable_event_command");
        assertCommandExists("/jTYU/disable_event_command");
        assertCommandExists("/jTYU/report_disabled_events_command");
    }

    private static void assertCommandExists(String fqn) {
        MetaCommand command = mdb.getMetaCommand(fqn);
        assertNotNull(command, "Expected MDB command " + fqn);
    }

    private static void assertContainerExists(String fqn) {
        SequenceContainer container = mdb.getSequenceContainer(fqn);
        assertNotNull(container, "Expected MDB container " + fqn);
    }

    private static void assertEventLabel(long value, String expectedLabel) {
        Parameter parameter = mdb.getParameter("/jTYU/event_definition_id");
        assertNotNull(parameter, "Expected MDB parameter /jTYU/event_definition_id");
        var ptype = (EnumeratedParameterType) parameter.getParameterType();
        assertEquals(expectedLabel, ptype.calibrate(value));
    }

    private static Path resolveSimulatorMdb() throws ConfigurationException {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("simulator/src/main/resources/jtyu_mdb.xml"),
                cwd.resolve("../simulator/src/main/resources/jtyu_mdb.xml").normalize(),
                Path.of("/data/yaams/yamcs/simulator/src/main/resources/jtyu_mdb.xml"));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new ConfigurationException("Could not locate simulator/src/main/resources/jtyu_mdb.xml");
    }
}
