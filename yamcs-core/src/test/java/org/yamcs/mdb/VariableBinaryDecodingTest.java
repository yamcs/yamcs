package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
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

    @Test
    public void testBinaryMemberUsesLengthFromEachArrayElement(@TempDir Path tempDir) throws IOException {
        Path xtce = tempDir.resolve("aggregate-binary.xml");
        Files.writeString(xtce, """
                <SpaceSystem name="AggregateBinary">
                  <TelemetryMetaData>
                    <ParameterTypeSet>
                      <IntegerParameterType name="uint8" signed="false">
                        <IntegerDataEncoding sizeInBits="8" encoding="unsigned"/>
                      </IntegerParameterType>
                      <BinaryParameterType name="binary">
                        <AncillaryDataSet>
                          <AncillaryData name="Yamcs:AggregateMember">length</AncillaryData>
                        </AncillaryDataSet>
                        <BinaryDataEncoding><SizeInBits><DynamicValue>
                          <ParameterInstanceRef parameterRef="length"/>
                          <LinearAdjustment slope="8" intercept="8"/>
                        </DynamicValue></SizeInBits></BinaryDataEncoding>
                      </BinaryParameterType>
                      <AggregateParameterType name="record">
                        <MemberList>
                          <Member name="length" typeRef="uint8"/>
                          <Member name="data" typeRef="binary"/>
                        </MemberList>
                      </AggregateParameterType>
                      <ArrayParameterType name="records" arrayTypeRef="record">
                        <DimensionList><Dimension>
                          <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
                          <EndingIndex><FixedValue>1</FixedValue></EndingIndex>
                        </Dimension></DimensionList>
                      </ArrayParameterType>
                    </ParameterTypeSet>
                    <ParameterSet><Parameter name="records" parameterTypeRef="records"/></ParameterSet>
                    <ContainerSet><SequenceContainer name="packet"><EntryList>
                      <ParameterRefEntry parameterRef="records"/>
                    </EntryList></SequenceContainer></ContainerSet>
                  </TelemetryMetaData>
                </SpaceSystem>
                """);
        var aggregateMdb = MdbFactory.createInstance(List.of(YConfiguration.wrap(
                Map.of("type", "xtce", "args", Map.of("file", xtce.toString())))), false, false);
        var extractor = new XtceTmExtractor(aggregateMdb);
        extractor.provideAll();
        long now = TimeEncoding.getWallclockTime();
        byte[] data = { 0, 11, 2, 22, 33, 44 };
        var result = extractor.processPacket(data, now, now, 0).getParameterResult();
        var records = (ArrayValue) result.getFirst().getEngValue();
        var first = (AggregateValue) records.getElementValue(0);
        var second = (AggregateValue) records.getElementValue(1);
        assertArrayEquals(new byte[] { 11 }, first.getMemberValue("data").getBinaryValue());
        assertArrayEquals(new byte[] { 22, 33, 44 }, second.getMemberValue("data").getBinaryValue());
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
