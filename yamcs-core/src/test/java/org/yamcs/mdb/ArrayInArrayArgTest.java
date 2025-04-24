package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.xml.XtceLoadException;

public class ArrayInArrayArgTest {

    private Mdb mdb;
    private MetaCommandProcessor metaCommandProcessor;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        mdb = MdbFactory
                .createInstanceByConfig("ArrayInArrayArgCommandTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand, IOException {
        MetaCommand mc = mdb.getMetaCommand("/ArrayInArrayArgTest/cmd1");
        Map<String, Object> args = new LinkedHashMap<>();

        args.put("outer_array_length", "2");

        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("inner_array_length", 2);
        a1.put("inner_array", Arrays.asList(0xAB, 0xCD));

        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("inner_array_length", 1);
        a2.put("inner_array", Arrays.asList(0x88));
        args.put("outer_array", Arrays.asList(a1, a2));

        // test with all array lengths set
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals("00020200AB00CD010088", StringConverter.arrayToHexString(b));

        // test with the a2 length not set
        a2.remove("inner_array_length");
        b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals("00020200AB00CD010088", StringConverter.arrayToHexString(b));

        // test with the a2 length set to the wrong value
        a2.put("inner_array_length", 5);
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });

        // test with the a2 length set to the wrong type
        a2.put("inner_array_length", "s");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });

        // test with no length set
        a2.remove("inner_array_length");
        a1.remove("inner_array_length");
        args.remove("outer_array_length");
        b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals("00020200AB00CD010088", StringConverter.arrayToHexString(b));

        // test with outer length set to the wrong value
        args.put("outer_array_length", 20);
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args);
        });
    }

}
