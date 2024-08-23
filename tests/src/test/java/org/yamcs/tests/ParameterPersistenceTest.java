package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.ValueHelper;

public class ParameterPersistenceTest extends AbstractIntegrationTest {

    @Test
    public void testSetParameter_Aggregate() throws Exception {
        var processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");

        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newUnsignedValue(10),
                "member2", ValueHelper.newUnsignedValue(1300),
                "member3", ValueHelper.newValue(3.14f));

        processorClient.setValue("/REFMDB/SUBSYS1/LocalAggregate1", v0).get();

        ParameterValue p6_initialValue = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue6").get();

        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue6", v0).get();
        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue9", v0).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        yamcs.shutDown();

        setupYamcs("IntegrationTest", false);

        before();
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");

        ParameterValue p6v_afterRestart = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue6").get();
        assertEquals(p6_initialValue.getEngValue(), p6v_afterRestart.getEngValue());

        ParameterValue p9v_afterRestart = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue9").get();
        assertEquals(v0, p9v_afterRestart.getEngValue());

    }

    public static void configureLogging(Level level) {
        Logger logger = Logger.getLogger("org.yamcs");
        logger.setLevel(level);
        ConsoleHandler ch = null;

        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                ch = (ConsoleHandler) h;
                break;
            }
        }
        if (ch == null) {
            ch = new ConsoleHandler();
            Logger.getLogger("").addHandler(ch);
        }
        ch.setLevel(level);
    }

    /**
     * use to enable logging during junit tests debugging.
     */
    public static void enableTracing() {
        configureLogging(Level.ALL);
    }
}
