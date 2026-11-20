package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.TimeEncoding;

class AggregateBinaryTmTest {
    @TempDir
    Path tempDirectory;

    @Test
    void nestedBinaryFieldsUseEachRowsLengthMember() throws IOException {
        Mdb mdb = load("""
                <IntegerParameterType name="byte" signed="false">
                  <IntegerDataEncoding sizeInBits="8" encoding="unsigned"/>
                </IntegerParameterType>
                <BinaryParameterType name="body">
                  <AncillaryDataSet><AncillaryData name="Yamcs:AggregateMember">length</AncillaryData></AncillaryDataSet>
                  <BinaryDataEncoding><SizeInBits><DynamicValue>
                    <ParameterInstanceRef parameterRef="length"/>
                    <LinearAdjustment slope="8" intercept="8"/>
                  </DynamicValue></SizeInBits></BinaryDataEncoding>
                </BinaryParameterType>
                <AggregateParameterType name="entry"><MemberList>
                  <Member name="length" typeRef="byte"/>
                  <Member name="body" typeRef="body"/>
                </MemberList></AggregateParameterType>
                <ArrayParameterType name="report" arrayTypeRef="entry"><DimensionList><Dimension>
                  <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
                  <EndingIndex><FixedValue>1</FixedValue></EndingIndex>
                </Dimension></DimensionList></ArrayParameterType>
                """);
        assertNestedBinaryFields(mdb);
        String exported = new XtceAssembler().toXtce(mdb, "/AggregateBinary", x -> true);
        assertNestedBinaryFields(loadXml(exported));
    }

    private static void assertNestedBinaryFields(Mdb mdb) {
        ArrayValue report = (ArrayValue) decode(mdb, new byte[] { 1, 11, 22, 0, 33 });
        assertArrayEquals(new byte[] { 11, 22 },
                ((AggregateValue) report.getElementValue(0)).getMemberValue("body").getBinaryValue());
        assertArrayEquals(new byte[] { 33 },
                ((AggregateValue) report.getElementValue(1)).getMemberValue("body").getBinaryValue());
    }

    @Test
    void extractsEightBitBinaryFieldFollowingOneBitFlag() throws IOException {
        Mdb mdb = load("""
                <IntegerParameterType name="flag" signed="false">
                  <IntegerDataEncoding sizeInBits="1" encoding="unsigned"/>
                </IntegerParameterType>
                <BinaryParameterType name="checksum">
                  <BinaryDataEncoding><SizeInBits><FixedValue>8</FixedValue></SizeInBits></BinaryDataEncoding>
                </BinaryParameterType>
                <AggregateParameterType name="report"><MemberList>
                  <Member name="first" typeRef="checksum"/>
                  <Member name="flag" typeRef="flag"/>
                  <Member name="second" typeRef="checksum"/>
                </MemberList></AggregateParameterType>
                """);
        AggregateValue report = (AggregateValue) decode(mdb, new byte[] { 1, (byte) 0x81, (byte) 0x80 });
        assertArrayEquals(new byte[] { 1 }, report.getMemberValue("first").getBinaryValue());
        assertArrayEquals(new byte[] { 3 }, report.getMemberValue("second").getBinaryValue());
    }

    private Mdb load(String types) throws IOException {
        return loadXml("""
                <SpaceSystem name="AggregateBinary" xmlns="http://www.omg.org/spec/XTCE/20180204">
                  <TelemetryMetaData>
                    <ParameterTypeSet>%s</ParameterTypeSet>
                    <ParameterSet><Parameter name="report" parameterTypeRef="report"/></ParameterSet>
                    <ContainerSet><SequenceContainer name="report">
                      <EntryList><ParameterRefEntry parameterRef="report"/></EntryList>
                    </SequenceContainer></ContainerSet>
                  </TelemetryMetaData>
                </SpaceSystem>
                """.formatted(types));
    }

    private Mdb loadXml(String xml) throws IOException {
        TimeEncoding.setUp();
        Path path = tempDirectory.resolve("aggregate-binary.xml");
        Files.writeString(path, xml);
        return MdbFactory.createInstance(List.of(YConfiguration.wrap(Map.of("type", "xtce", "spec", path.toString()))),
                false, false);
    }

    private static Value decode(Mdb mdb, byte[] bytes) {
        var extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        long now = TimeEncoding.getWallclockTime();
        var result = extractor.processPacket(bytes, now, now, 0, mdb.getSequenceContainer("/AggregateBinary/report"));
        var parameter = result.getParameterResult().getFirstInserted(mdb.getParameter("/AggregateBinary/report"));
        assertNotNull(parameter);
        return parameter.getEngValue();
    }
}
