package org.yamcs.xtceproc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import org.junit.Before;
import org.junit.Test;
import org.yamcs.ErrorInCommand;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.xml.XtceLoadException;

import static org.junit.Assert.assertArrayEquals;

/**
 * Tests that a command containing a variable-length binary argument can be
 * encoded correctly.
 */
public class VariableBinaryCommandEncodingTest {

    private static final String SIZE_QN = "/VariableBinaryTest/size";
    private static final String DATA_QN = "/VariableBinaryTest/data";
    private static final String VALUE_QN = "/VariableBinaryTest/value";

    private XtceDb db;
    private MetaCommandProcessor metaCommandProcessor;

    @Before
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        db = XtceDbFactory
                .createInstanceByConfig("VariableBinaryTest");
        metaCommandProcessor = new MetaCommandProcessor(
                new ProcessorData("test", "test", db, new ProcessorConfig()));
    }

    @Test
    public void testCommandEncoding() throws ErrorInCommand, IOException {
        MetaCommand mc = db.getMetaCommand("/VariableBinaryTest/Command");
        List<ArgumentAssignment> arguments = new LinkedList<>();

        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        StringBuilder builder = new StringBuilder();
        for (byte b : data) {
            builder.append(String.format("%02X", b));
        }
        arguments.add(new ArgumentAssignment("size",
                Integer.toString(data.length * 8)));
        arguments.add(new ArgumentAssignment("data", builder.toString()));
        arguments.add(new ArgumentAssignment("value", "3.14"));
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments)
                .getCmdPacket();
        byte[] expected = createPacket(data, 3.14F);

        assertArrayEquals(expected, b);
    }

    private byte[] createPacket(byte[] data, float value) throws IOException {
        ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(arrayStream);

        out.writeShort(data.length * 8);
        out.write(data);
        out.writeInt(Float.floatToIntBits(value));

        return arrayStream.toByteArray();
    }
}