package org.yamcs.mdb;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.MetaCommandProcessor;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

public class EnumArgTest {

    private XtceDb db;
    private MetaCommandProcessor metaCommandProcessor;

    @Before
    public void setup() throws DatabaseLoadException {
        YConfiguration.setupTest(null);
        db = XtceDbFactory.createInstanceByConfig("EnumArgCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", "test", db, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand {
        MetaCommand mc = db.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", "ASCENT");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("02", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testCommandEncoding_stateNumber() throws ErrorInCommand {
        MetaCommand mc = db.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", 2);
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();

        assertEquals("02", StringConverter.arrayToHexString(b));
    }

    /*
     * Not allowed because there is no string state "2", and it is not safe to assume that numeric value is intended.
     */
    @Test(expected = ErrorInCommand.class)
    public void testCommandEncoding_stringStateNumber() throws ErrorInCommand {
        MetaCommand mc = db.getMetaCommand("/EnumArgTest/cmd1");
        Map<String, Object> args = new HashMap<>();

        args.put("phase", "2");
        metaCommandProcessor.buildCommand(mc, args);
    }
}
