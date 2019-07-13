package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.ArgumentAssignment;
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
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", "test", xtcedb, false));
    }

    @Test
    public void test1() throws ErrorInCommand {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/SpaceVehicle/PWHTMR");
        assertEquals(32, mc.getCommandContainer().getSizeInBits());
        List<ArgumentAssignment> arguments = new LinkedList<>();
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("TimerStartStop", "TIMER_START");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals(Levels.critical, mc.getDefaultSignificance().getConsequenceLevel());

        assertEquals("FF0000001E000001", StringConverter.arrayToHexString(b));

        List<CommandVerifier> vl = mc.getCommandVerifiers();

        // TODO should be two here but we do not support yet the parameter comparison verifier
        assertEquals(1, vl.size());
        CommandVerifier cv = vl.get(0);
        assertEquals(CommandVerifier.Type.CONTAINER, cv.getType());
        CheckWindow cw = cv.getCheckWindow();
        assertEquals(-1, cw.getTimeToStartChecking());
        assertEquals(600000, cw.getTimeToStopChecking());
        assertEquals(TimeWindowIsRelativeToType.LastVerifier, cw.getTimeWindowIsRelativeTo());
    }

}
