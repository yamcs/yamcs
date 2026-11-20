package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ContainerEntry;

class DynamicParameterReferenceTest {
    private static final String SYSTEM = "/DynamicParameterReferenceTest/";
    private static final Path FIXTURE = Path.of("src/test/resources/xtce/dynamic-parameter-reference.xml");
    private static final byte[] REPEATED = { 2, 0x11, 0x22, 3, 0x33, 0x44, 0x55, 0x7E };

    @TempDir
    Path directory;

    @BeforeEach
    void setup() {
        YConfiguration.setupTest(null);
        TimeEncoding.setUp();
    }

    @Test
    void repeatedBinaryUsesEachEntryLength() throws Exception {
        Mdb mdb = load(FIXTURE);
        assertRepeatedBinary(mdb);
    }

    @Test
    void repeatedArrayUsesEachEntryCount() throws Exception {
        Mdb mdb = load(FIXTURE);
        ParameterValueList values = process(mdb, "array_packet", REPEATED);
        var parameter = mdb.getParameter(SYSTEM + "values");
        ArrayValue first = (ArrayValue) values.get(parameter, 0).getEngValue();
        ArrayValue second = (ArrayValue) values.get(parameter, 1).getEngValue();
        assertEquals(2, first.flatLength());
        assertEquals(3, second.flatLength());
        assertEquals(0x55, second.getElementValue(2).toLong());
    }

    @Test
    void repeatedArrayCanGrowAfterAnEmptyEntry() throws Exception {
        Mdb mdb = load(FIXTURE);
        ParameterValueList values = process(mdb, "array_packet", new byte[] { 0, 2, 0x11, 0x22, 0x7E });
        var parameter = mdb.getParameter(SYSTEM + "values");
        ArrayValue first = (ArrayValue) values.get(parameter, 0).getEngValue();
        ArrayValue second = (ArrayValue) values.get(parameter, 1).getEngValue();
        assertEquals(0, first.flatLength());
        assertEquals(2, second.flatLength());
        assertEquals(0x22, second.getElementValue(1).toLong());
    }

    @Test
    void explicitInstanceSelectsFromPacketStart() throws Exception {
        assertExplicitSelection(withInstance(0), new byte[] { 1, 2, 0x11, 0x7E }, new byte[] { 0x11 });
        assertExplicitSelection(withInstance(1), new byte[] { 1, 2, 0x11, 0x22, 0x7E },
                new byte[] { 0x11, 0x22 });
    }

    @Test
    void exportPreservesCurrentEntryLookup() throws Exception {
        assertRepeatedBinary(roundTrip(load(FIXTURE)));
    }

    @Test
    void exportPreservesExplicitPacketStartSelection() throws Exception {
        assertExplicitSelection(roundTrip(withInstance(0)), new byte[] { 1, 2, 0x11, 0x7E },
                new byte[] { 0x11 });
        assertExplicitSelection(roundTrip(withInstance(1)), new byte[] { 1, 2, 0x11, 0x22, 0x7E },
                new byte[] { 0x11, 0x22 });
    }

    @Test
    void pus19DefinitionReportDecodesUnequalEmbeddedPacketLengths() throws Exception {
        Mdb mdb = MdbFactory.createInstance(List.of(
                YConfiguration.wrap(Map.of("type", "xtce", "spec", "../examples/pus/src/main/yamcs/mdb/dt.xml")),
                YConfiguration.wrap(Map.of("type", "xtce", "spec", "../examples/pus/src/main/yamcs/mdb/pus.xml")),
                YConfiguration.wrap(Map.of("type", "xtce", "spec", "../examples/pus/src/main/yamcs/mdb/pus19.xml"))),
                false, false);
        byte[] first = { 0x18, 1, (byte) 0xC0, 0, 0, 10, 0x27, 17, 1, 0, 0, 0, 0, 0, 0, 0, 0 };
        byte[] second = { 0x18, 1, (byte) 0xC0, 1, 0, 13, 0x27, 20, 1, 0, 0, 0, 0, 0, 0, 1, 0x10, 1, 0, 0 };
        ByteBuffer packet = ByteBuffer.allocate(75);
        packet.putShort((short) 0x0801).putShort((short) 0xC000).putShort((short) (packet.capacity() - 7));
        packet.put((byte) 0x21).put((byte) 19).put((byte) 11).putShort((short) 0).putShort((short) 0);
        packet.put((byte) 0x2F).putInt(0).put(new byte[3]);
        packet.putInt(0); // Four spare bytes follow the time in ordinary TM.
        packet.put((byte) 2);
        packet.putShort((short) 1).putShort((short) 1).put((byte) 0).put(first);
        packet.putShort((short) 1).putShort((short) 2).put((byte) 1).put(second);
        packet.putShort((short) 0); // Outer CRC is not checked by the XTCE extractor.

        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        ContainerProcessingResult result = extractor.processPacket(packet.array(), 0, 0, 0,
                mdb.getSequenceContainer("/PUS/ccsds"));
        assertNull(result.exception);
        var parameter = mdb.getParameter("/PUS19/ea11_request_data");
        assertEquals(2, result.getParameterResult().count(parameter));
        assertArrayEquals(first, result.getParameterResult().get(parameter, 0).getEngValue().getBinaryValue());
        assertArrayEquals(second, result.getParameterResult().get(parameter, 1).getEngValue().getBinaryValue());
    }

    @Test
    void exportPreservesDynamicBinaryArgumentLength() throws Exception {
        Mdb mdb = load(Path.of("src/test/resources/xtce/VariableBinary.xml"));
        Path xml = directory.resolve("binary-argument.xml");
        Files.writeString(xml, new XtceAssembler().toXtce(mdb, "/VariableBinaryTest", x -> true));
        Mdb reloaded = load(xml);
        MetaCommandProcessor processor = new MetaCommandProcessor(
                new ProcessorData("test", reloaded, new ProcessorConfig()));
        byte[] command = processor.buildCommand(reloaded.getMetaCommand("/VariableBinaryTest/Command"),
                Map.of("size", 1, "data", "1122", "value", 3.14F), 0).getCmdPacket();
        // The dynamic size selects one of the supplied two bytes; the float follows immediately.
        assertArrayEquals(new byte[] { 0, 1, 0x11, 0x40, 0x48, (byte) 0xF5, (byte) 0xC3 }, command);
    }

    private void assertRepeatedBinary(Mdb mdb) {
        ParameterValueList values = process(mdb, "binary_packet", REPEATED);
        var parameter = mdb.getParameter(SYSTEM + "data");
        assertEquals(2, values.count(parameter));
        assertArrayEquals(new byte[] { 0x11, 0x22 }, values.get(parameter, 0).getEngValue().getBinaryValue());
        assertArrayEquals(new byte[] { 0x33, 0x44, 0x55 }, values.get(parameter, 1).getEngValue().getBinaryValue());
    }

    private void assertExplicitSelection(Mdb mdb, byte[] packet, byte[] expected) {
        ParameterValueList values = process(mdb, "selection_packet", packet);
        assertArrayEquals(expected, values.getFirstInserted(mdb.getParameter(SYSTEM + "data"))
                .getEngValue().getBinaryValue());
    }

    private ParameterValueList process(Mdb mdb, String container, byte[] packet) {
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        ContainerProcessingResult result = extractor.processPacket(packet, 0, 0, 0,
                mdb.getSequenceContainer(SYSTEM + container));
        assertNull(result.exception);
        ParameterValueList values = result.getParameterResult();
        assertEquals(0x7E, values.getFirstInserted(mdb.getParameter(SYSTEM + "tail")).getEngValue().toLong());
        return values;
    }

    private Mdb withInstance(int instance) throws Exception {
        Path xml = directory.resolve("instance-" + instance + ".xml");
        Files.writeString(xml, Files.readString(FIXTURE).replace("<ParameterInstanceRef parameterRef=\"count\"/>",
                "<ParameterInstanceRef parameterRef=\"count\" instance=\"" + instance + "\"/>"));
        return load(xml);
    }

    private Mdb roundTrip(Mdb mdb) throws Exception {
        Path xml = Files.createTempFile(directory, "export-", ".xml");
        Files.writeString(xml, new XtceAssembler().toXtce(mdb, "/DynamicParameterReferenceTest", x -> true));
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new File("../yamcs-xtce/SpaceSystemV1.2.xsd"))
                .newValidator().validate(new StreamSource(xml.toFile()));
        Mdb reloaded = load(xml);
        ContainerEntry entry = (ContainerEntry) reloaded.getSequenceContainer(SYSTEM + "binary_packet")
                .getEntryList().get(0);
        assertEquals("binary_entry", entry.getRefContainer().getName());
        return reloaded;
    }

    private Mdb load(Path xml) throws Exception {
        return MdbFactory.createInstance(List.of(YConfiguration.wrap(
                Map.of("type", "xtce", "spec", xml.toString()))), false, false);
    }
}
