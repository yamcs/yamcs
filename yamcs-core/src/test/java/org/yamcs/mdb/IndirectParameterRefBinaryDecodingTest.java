package org.yamcs.mdb;

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
 * Tests that a packet containing indirect parameter entry can be decoded correctly.
 */
public class IndirectParameterRefBinaryDecodingTest {

    private static final String ID_QN = "/Example/id";
    private static final String[] PARAM_QNs = {
        "/Example/example_param1",
        "/Example/example_param2",
    };

    private Mdb mdb;

    @BeforeEach
    public void setup() throws URISyntaxException, XtceLoadException,
            XMLStreamException, IOException {

        YConfiguration.setupTest(null);
        mdb = MdbFactory
                .createInstanceByConfig("indirect-param-ref");

        TimeEncoding.setUp();
    }

    @Test
    public void testProcessPacket() throws IOException {
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();

        float[] paramValues = {1.23F, 4.56F};
        long now = TimeEncoding.getWallclockTime();

        for (int i = 0; i < 2; i++) {
            byte[] packet = createPacket(i + 1, paramValues[i]);
            ParameterValueList result = 
                extractor.processPacket(packet, now, now, 0).getParameterResult();

            assertEquals(2, result.getSize());
            assertEquals(i + 1, result.get(mdb.getParameter(ID_QN), 0).getEngValue().toLong());
            assertEquals(paramValues[i], 
                result.get(mdb.getParameter(PARAM_QNs[i]), 0).getEngValue().toDouble());

        }
    }

    private byte[] createPacket(int id, float value) throws IOException {
        ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(arrayStream);

        out.writeInt(id);
        out.writeFloat(value);

        return arrayStream.toByteArray();
    }
}
