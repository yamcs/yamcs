package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance.Levels;

public class CcsdsGreenBookCommandEncodingTest {
    static Mdb mdb;
    static MetaCommandProcessor metaCommandProcessor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("ccsds-green-book");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void test1() throws ErrorInCommand {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/SpaceVehicle/PWHTMR");
        assertEquals(32, mc.getCommandContainer().getSizeInBits());
        Map<String, Object> args = new HashMap<>();
        args.put("TimerStartStop", "TIMER_START");

        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(Levels.CRITICAL, mc.getDefaultSignificance().getConsequenceLevel());

        assertEquals("FF0000001E000001", StringConverter.arrayToHexString(b));

        List<CommandVerifier> vl = mc.getCommandVerifiers();

        assertEquals(2, vl.size());
        CommandVerifier cv = vl.get(0);
        assertEquals(CommandVerifier.Type.CONTAINER, cv.getType());
        CheckWindow cw = cv.getCheckWindow();
        assertEquals(-1, cw.getTimeToStartChecking());
        assertEquals(600000, cw.getTimeToStopChecking());
        assertEquals(TimeWindowIsRelativeToType.LAST_VERIFIER, cw.getTimeWindowIsRelativeTo());
    }
}
