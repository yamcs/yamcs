package org.yamcs.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ParameterFormatter.Header;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParameterFormatterTest {

    private StringWriter writer;
    private ParameterFormatter formatter;
    private NamedObjectId param1;
    private NamedObjectId param2;

    @BeforeAll
    static void beforeAll() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    void setUp() {
        writer = new StringWriter();
        param1 = NamedObjectId.newBuilder().setName("param1").build();
        param2 = NamedObjectId.newBuilder().setName("param2").build();
        formatter = new ParameterFormatter(writer, List.of(param1, param2));
    }

    @Test
    void testWriteParameters_singleParameter() throws IOException {
        List<ParameterValueWithId> params = List.of(getPv(param1, 100L, "value1"));

        formatter.writeParameters(params);
        formatter.close();
        var reader = new BufferedReader(new StringReader(writer.toString()));

        assertTrue(reader.readLine().contains("value1"));
        assertEquals(1, formatter.getLinesReceived());
    }

    @Test
    void testWriteParameters_multipleParameters() throws IOException {
        List<ParameterValueWithId> params = List.of(
                getPv(param1, 100L, "value1"),
                getPv(param2, 100L, "value2"));

        formatter.writeParameters(params);
        formatter.close();

        var reader = new BufferedReader(new StringReader(writer.toString()));
        var l1 = reader.readLine();
        assertTrue(l1.contains("value1\tvalue2"));
        assertEquals(1, formatter.getLinesReceived());
    }

    @Test
    void testWriteParameters_timeWindow() throws IOException {
        formatter.setTimeWindow(50);
        List<ParameterValueWithId> params1 = List.of(getPv(param1, 100L, "value1"));
        List<ParameterValueWithId> params2 = List.of(getPv(param1, 130L, "value2"));
        List<ParameterValueWithId> params3 = List.of(getPv(param1, 160L, "value3"));

        formatter.writeParameters(params1);
        formatter.writeParameters(params2);
        formatter.writeParameters(params3);
        formatter.close();

        var reader = new BufferedReader(new StringReader(writer.toString()));
        assertTrue(reader.readLine().contains("value2"));
        assertTrue(reader.readLine().contains("value3"));
        assertEquals(3, formatter.getLinesReceived());
    }

    @Test
    void testWriteParameters_withHeader() throws IOException {
        formatter.setWriteHeader(Header.SHORT_NAME);
        List<ParameterValueWithId> params = List.of(getPv(param1, 100L, "value1"));

        formatter.writeParameters(params);
        formatter.close();
        var reader = new BufferedReader(new StringReader(writer.toString()));
        var l1 = reader.readLine();
        assertTrue(l1.contains("Time\tparam1"));
    }

    @Test
    void testWriteParameters_withDuplicates() throws IOException {
        List<ParameterValueWithId> params = List.of(getPv(param1, 100L, "value1"), getPv(param2, 100L, "value2"),
                getPv(param1, 100L, "value3"));

        formatter.writeParameters(params);
        formatter.close();
        var reader = new BufferedReader(new StringReader(writer.toString()));
        assertTrue(reader.readLine().contains("value1\tvalue2"));
        assertTrue(reader.readLine().contains("value3"));

    }

    private ParameterValueWithId getPv(NamedObjectId id, long generationTime, String value) {
        ParameterValue pv = new ParameterValue(id.getName());
        var v = ValueUtility.getStringValue(value);
        pv.setEngValue(v);
        pv.setRawValue(v);
        pv.setGenerationTime(generationTime);
        return new ParameterValueWithId(pv, id);
    }
}
