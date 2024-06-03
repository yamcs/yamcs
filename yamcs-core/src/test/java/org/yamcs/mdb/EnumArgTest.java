package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;

public class EnumArgTest {

    private Mdb mdb;
    private MetaCommandProcessor metaCommandProcessor;

    @BeforeEach
    public void setup() throws DatabaseLoadException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("EnumArgCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", "ASCENT");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("02", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testCommandEncoding_stateNumber() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", 2);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("02", StringConverter.arrayToHexString(b));
    }

    /*
     * Not allowed because there is no string state "2", and it is not safe to assume that numeric value is intended.
     */
    @Test
    public void testCommandEncoding_stringStateNumber() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", "2");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });
    }
}
