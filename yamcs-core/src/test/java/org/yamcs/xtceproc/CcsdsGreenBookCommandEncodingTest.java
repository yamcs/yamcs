package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
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
import org.yamcs.xtce.XtceDb;

public class CcsdsGreenBookCommandEncodingTest {
    static XtceDb xtcedb;
    static MetaCommandProcessor metaCommandProcessor;

    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        xtcedb = XtceDbFactory.createInstanceByConfig("ccsds-green-book");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", "test", xtcedb, new ProcessorConfig()));
    }

    @Test
    public void test1() throws ErrorInCommand {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/SpaceVehicle/PWHTMR");
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
