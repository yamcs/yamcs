package org.yamcs.mdb;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MetaCommandProcessor;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.xml.XtceLoadException;

/**
 * Tests that a command containing a variable-length binary argument can be encoded correctly.
 */
public class VariableBinaryCommandEncodingTest {

    private XtceDb db;
    private MetaCommandProcessor metaCommandProcessor;

    @Before
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        db = XtceDbFactory.createInstanceByConfig("VariableBinaryTest");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", "test", db, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/VariableBinaryTest/Command");
        Map<String, Object> args = new HashMap<>();

        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        args.put("size", Integer.toString(data.length));
        args.put("data", StringConverter.arrayToHexString(data));
        args.put("value", "3.14");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = createPacket(data, 3.14F, true);

        assertArrayEquals(expected, b);
    }

    @Test
    public void testCommandEncodingWithoutSize() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/VariableBinaryTest/Command1");
        Map<String, Object> args = new HashMap<>();

        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        StringBuilder builder = new StringBuilder();
        for (byte b : data) {
            builder.append(String.format("%02X", b));
        }
        args.put("data", builder.toString());
        args.put("value", "3.14");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        byte[] expected = createPacket(data, 3.14F, false);

        assertArrayEquals(expected, b);
    }

    @Test(expected = ErrorInCommand.class)
    public void testCommandEncodingWithoutSizeTooSmall() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/VariableBinaryTest/Command1");
        Map<String, Object> args = new HashMap<>();
        args.put("data", "01");
        args.put("value", "3.14");
        metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
    }

    @Test(expected = ErrorInCommand.class)
    public void testCommandEncodingWithoutSizeTooLong() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/VariableBinaryTest/Command1");
        Map<String, Object> args = new HashMap<>();
        args.put("data", "01020304050607");
        args.put("value", "3.14");
        metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
    }

    private byte[] createPacket(byte[] data, float value, boolean withSize) throws IOException {
        ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(arrayStream);
        if (withSize) {
            out.writeShort(data.length);
        }
        out.write(data);
        out.writeInt(Float.floatToIntBits(value));

        return arrayStream.toByteArray();
    }
}
