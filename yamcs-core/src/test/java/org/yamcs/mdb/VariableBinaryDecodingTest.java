package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.xml.XtceLoadException;

/**
 * Tests that a packet containing a binary data type with variable size can be unpacked correctly.
 */
public class VariableBinaryDecodingTest {

    private static final String SIZE_QN = "/VariableBinaryTest/size";
    private static final String DATA_QN = "/VariableBinaryTest/data";
    private static final String VALUE_QN = "/VariableBinaryTest/value";

    private Mdb mdb;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        mdb = MdbFactory
                .createInstanceByConfig("VariableBinaryTest");

        TimeEncoding.setUp();
    }

    @Test
    public void testProcessPacket() throws IOException {
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();

        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        byte[] b = createPacket(data, 3.14F);
        long now = TimeEncoding.getWallclockTime();
        ContainerProcessingResult result = extractor.processPacket(b, now,
                now, 0);

        ParameterValueList pvl = result.getParameterResult();
        assertEquals(1, pvl.count(mdb.getParameter(SIZE_QN)));
        assertEquals(1, pvl.count(mdb.getParameter(DATA_QN)));
        assertEquals(1, pvl.count(mdb.getParameter(VALUE_QN)));
        pvl.forEach(pv -> {
            if (pv.getParameterQualifiedName().equals(SIZE_QN)) {
                assertEquals(data.length * 8,
                        pv.getEngValue().getSint32Value());
            } else if (pv.getParameterQualifiedName().equals(DATA_QN)) {
                assertArrayEquals(data, pv.getEngValue().getBinaryValue());
            } else if (pv.getParameterQualifiedName().equals(VALUE_QN)) {
                assertEquals(3.14F, pv.getEngValue().getFloatValue(), 1E-6F);
            }
        });
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
