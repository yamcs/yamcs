package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MetaCommandProcessor.CommandBuildResult;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.xml.XtceLoadException;

public class RepeatEntryCommandEncodingTest {

    private Mdb mdb;
    private MetaCommandProcessor metaCommandProcessor;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException, XMLStreamException, IOException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("RepeatEntryCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void testRepeatedArgumentEntryEncoding() throws ErrorInCommand {
        MetaCommand mc = mdb.getMetaCommand("/RepeatEntryCommandTest/cmd1");
        Map<String, Object> args = new HashMap<>();
        args.put("count", "3");
        args.put("value", "170");

        byte[] packet = buildCommand(mc, args).getCmdPacket();

        assertEquals("03AAAAAA", StringConverter.arrayToHexString(packet));
    }

    private CommandBuildResult buildCommand(MetaCommand mc, Map<String, Object> argAssignmentList)
            throws ErrorInCommand {
        return metaCommandProcessor.buildCommand(mc, argAssignmentList, 0);
    }
}
