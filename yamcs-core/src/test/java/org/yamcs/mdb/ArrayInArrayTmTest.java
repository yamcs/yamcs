package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SequenceContainer;

public class ArrayInArrayTmTest {
    static Mdb mdb;
    static ProcessorData pdata;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("ArrayInArrayTmTest", false);
        pdata = new ProcessorData("test", mdb, new ProcessorConfig());
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void testNestedAggregateArraysProduceOneParameter() {
        byte[] packet = new byte[] {
                0x00, 0x02,
                0x02, 0x00, (byte) 0xAB, 0x00, (byte) 0xCD,
                0x01, 0x00, (byte) 0x88,
        };

        ParameterValueList values = process(packet, "packet").getParameterResult();
        assertEquals(1, values.size());
        ParameterValue reportValue = values.getFirstInserted(mdb.getParameter("/ArrayInArrayTmTest/report"));
        AggregateValue report = (AggregateValue) reportValue.getEngValue();
        assertEquals(2, report.getMemberValue("outer_array_length").toLong());

        ArrayValue outer = (ArrayValue) report.getMemberValue("outer_array");
        assertArrayEquals(new int[] { 2 }, outer.getDimensions());
        assertInner((AggregateValue) outer.getElementValue(0), new long[] { 0xAB, 0xCD });
        assertInner((AggregateValue) outer.getElementValue(1), new long[] { 0x88 });
    }

    @Test
    public void testZeroLengthOuterArray() {
        ParameterValueList values = process(new byte[] { 0x00, 0x00 }, "packet").getParameterResult();
        AggregateValue report = (AggregateValue) values.getFirst().getEngValue();
        ArrayValue outer = (ArrayValue) report.getMemberValue("outer_array");
        assertArrayEquals(new int[] { 0 }, outer.getDimensions());
    }

    @Test
    public void testZeroLengthInnerArray() {
        ParameterValueList values = process(new byte[] { 0x00, 0x01, 0x00 }, "packet").getParameterResult();
        AggregateValue report = (AggregateValue) values.getFirst().getEngValue();
        ArrayValue outer = (ArrayValue) report.getMemberValue("outer_array");
        assertInner((AggregateValue) outer.getElementValue(0), new long[0]);
    }

    @Test
    public void testCompletedAggregateMemberReference() {
        byte[] packet = new byte[] { 0x02, 0x00, 0x11, 0x00, 0x22 };
        ParameterValueList values = process(packet, "external_member_packet").getParameterResult();
        assertEquals(2, values.size());
        ParameterValue arrayValue = values.getFirstInserted(mdb.getParameter("/ArrayInArrayTmTest/external_array"));
        ArrayValue array = (ArrayValue) arrayValue.getEngValue();
        assertArrayEquals(new int[] { 2 }, array.getDimensions());
        assertEquals(0x11, array.getElementValue(0).toLong());
        assertEquals(0x22, array.getElementValue(1).toLong());
    }

    @Test
    public void testBareReferenceFallsBackToOrdinaryParameter() {
        byte[] packet = new byte[] { 0x02, 0x11, 0x22 };
        ParameterValueList values = process(packet, "ordinary_parameter_packet").getParameterResult();
        ParameterValue arrayValue = values.getFirstInserted(mdb.getParameter("/ArrayInArrayTmTest/ordinary_array"));
        ArrayValue array = (ArrayValue) arrayValue.getEngValue();
        assertArrayEquals(new int[] { 2 }, array.getDimensions());
        assertEquals(0x11, array.getElementValue(0).toLong());
        assertEquals(0x22, array.getElementValue(1).toLong());
    }

    @Test
    public void testNegativeAggregateMemberCount() {
        assertProcessingError(new byte[] { (byte) 0xFF }, "negative_count_packet",
                "Negative array size -1 encountered when processing array negative_array_type");
    }

    @Test
    public void testOversizedAggregateMemberCount() {
        assertProcessingError(new byte[] { 0x27, 0x11 }, "oversized_count_packet",
                "Size of one dimension of array oversized_array_type exceeds the max allowed: 10001 > 10000");
    }

    @Test
    public void testMissingOrdinaryParameterCount() {
        assertProcessingError(new byte[0], "missing_count_packet",
                "Missing value for dynamic integer value: /ArrayInArrayTmTest/missing_count");
    }

    @Test
    public void testMissingAggregateMemberReferenceFailsDuringMdbLoad() {
        DatabaseLoadException exception = assertThrows(DatabaseLoadException.class,
                () -> MdbFactory.createInstanceByConfig("ArrayInArrayTmInvalidTest", false));
        assertEquals("Cannot find aggregate member 'missing_count' used as an array size at 'report.values' "
                + "in parameter 'report'", exception.getMessage());
    }

    @Test
    public void testQualifiedReferenceDoesNotUseAggregateMember() {
        byte[] packet = new byte[] { 0x02, 0x01, 0x11, 0x22 };
        ParameterValueList values = process(packet, "qualified_parameter_packet").getParameterResult();
        ParameterValue reportValue = values.getFirstInserted(mdb.getParameter("/ArrayInArrayTmTest/qualified_report"));
        AggregateValue report = (AggregateValue) reportValue.getEngValue();
        assertEquals(1, report.getMemberValue("external_count").toLong());

        ArrayValue array = (ArrayValue) report.getMemberValue("values");
        assertArrayEquals(new int[] { 2 }, array.getDimensions());
        assertEquals(0x11, array.getElementValue(0).toLong());
        assertEquals(0x22, array.getElementValue(1).toLong());
    }

    @Test
    public void testPus14ConfigurationHierarchy() {
        byte[] packet = pack(
                new int[] { 8, 11, 8, 8, 8, 8, 8, 8, 8, 8, 11, 8, 8, 8, 8, 8 },
                new long[] { 2, 0x11, 2, 3, 2, 4, 5, 6, 1, 7, 0x22, 1, 8, 2, 9, 10 });

        AggregateValue configuration = getConfiguration(packet);
        assertEquals(2, configuration.getMemberValue("n_count").toLong());
        ArrayValue reports = (ArrayValue) configuration.getMemberValue("apid_reports");
        assertArrayEquals(new int[] { 2 }, reports.getDimensions());
        assertApidReport((AggregateValue) reports.getElementValue(0), 0x11,
                new int[] { 3, 6 }, new long[][] { { 4, 5 }, { 7 } });
        assertApidReport((AggregateValue) reports.getElementValue(1), 0x22,
                new int[] { 8 }, new long[][] { { 9, 10 } });
    }

    @Test
    public void testPus14ZeroLengthArraysAtEveryLevel() {
        AggregateValue configuration = getConfiguration(new byte[] { 0x00 });
        ArrayValue reports = (ArrayValue) configuration.getMemberValue("apid_reports");
        assertArrayEquals(new int[] { 0 }, reports.getDimensions());

        configuration = getConfiguration(pack(
                new int[] { 8, 11, 8 },
                new long[] { 1, 0x11, 0 }));
        reports = (ArrayValue) configuration.getMemberValue("apid_reports");
        AggregateValue report = (AggregateValue) reports.getElementValue(0);
        ArrayValue services = (ArrayValue) report.getMemberValue("services");
        assertArrayEquals(new int[] { 0 }, services.getDimensions());

        configuration = getConfiguration(pack(
                new int[] { 8, 11, 8, 8, 8 },
                new long[] { 1, 0x11, 1, 3, 0 }));
        reports = (ArrayValue) configuration.getMemberValue("apid_reports");
        report = (AggregateValue) reports.getElementValue(0);
        services = (ArrayValue) report.getMemberValue("services");
        AggregateValue service = (AggregateValue) services.getElementValue(0);
        ArrayValue subservices = (ArrayValue) service.getMemberValue("subservices");
        assertArrayEquals(new int[] { 0 }, subservices.getDimensions());
    }

    private void assertInner(AggregateValue aggregate, long[] expected) {
        assertEquals(expected.length, aggregate.getMemberValue("inner_array_length").toLong());
        ArrayValue inner = (ArrayValue) aggregate.getMemberValue("inner_array");
        assertArrayEquals(new int[] { expected.length }, inner.getDimensions());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], inner.getElementValue(i).toLong());
        }
    }

    private void assertApidReport(AggregateValue report, long expectedApid, int[] serviceTypes,
            long[][] expectedSubservices) {
        assertEquals(expectedApid, report.getMemberValue("apid").toLong());
        assertEquals(serviceTypes.length, report.getMemberValue("service_count").toLong());
        ArrayValue services = (ArrayValue) report.getMemberValue("services");
        assertArrayEquals(new int[] { serviceTypes.length }, services.getDimensions());
        for (int i = 0; i < serviceTypes.length; i++) {
            AggregateValue service = (AggregateValue) services.getElementValue(i);
            assertEquals(serviceTypes[i], service.getMemberValue("service_type").toLong());
            assertEquals(expectedSubservices[i].length, service.getMemberValue("subservice_count").toLong());
            ArrayValue subservices = (ArrayValue) service.getMemberValue("subservices");
            assertArrayEquals(new int[] { expectedSubservices[i].length }, subservices.getDimensions());
            for (int j = 0; j < expectedSubservices[i].length; j++) {
                assertEquals(expectedSubservices[i][j], subservices.getElementValue(j).toLong());
            }
        }
    }

    private AggregateValue getConfiguration(byte[] packet) {
        ParameterValueList values = process(packet, "configuration_packet").getParameterResult();
        assertEquals(1, values.size());
        return (AggregateValue) values.getFirst().getEngValue();
    }

    private byte[] pack(int[] widths, long[] values) {
        assertEquals(widths.length, values.length);
        int totalBits = 0;
        for (int width : widths) {
            totalBits += width;
        }

        byte[] result = new byte[(totalBits + 7) / 8];
        int bitOffset = 0;
        for (int i = 0; i < widths.length; i++) {
            for (int bit = widths[i] - 1; bit >= 0; bit--) {
                if ((values[i] & (1L << bit)) != 0) {
                    result[bitOffset / 8] |= 1 << (7 - bitOffset % 8);
                }
                bitOffset++;
            }
        }
        return result;
    }

    private ContainerProcessingResult process(byte[] packet, String containerName) {
        SequenceContainer container = mdb.getSequenceContainer("/ArrayInArrayTmTest/" + containerName);
        return extractor.processPacket(packet, now, now, 0, container);
    }

    private void assertProcessingError(byte[] packet, String containerName, String expectedMessage) {
        XtceProcessingException exception = process(packet, containerName).exception;
        assertNotNull(exception);
        assertEquals(expectedMessage, exception.getMessage());
    }
}
