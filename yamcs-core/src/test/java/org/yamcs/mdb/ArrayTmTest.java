package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

public class ArrayTmTest {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;
    static ProcessorData pdata;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("ArrayTmTest");
        pdata = new ProcessorData("test", mdb, new ProcessorConfig());
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void testEmptyArray() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(0);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("n"));
        assertEquals(0, pv.getEngValue().getUint32Value());

        pv = pvl.getFirstInserted(param("array1"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 0 }, ev.getDimensions());
        assertEquals(Type.UINT32, ev.getElementType());

        ArrayValue rv = (ArrayValue) pv.getRawValue();
        assertArrayEquals(new int[] { 0 }, rv.getDimensions());
        assertEquals(Type.UINT32, rv.getElementType());
    }

    @Test
    public void testEmptyArray2() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(0);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("n"));
        assertEquals(0, pv.getEngValue().getUint32Value());

        pv = pvl.getFirstInserted(param("array2"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 0 }, ev.getDimensions());
        assertEquals(Type.DOUBLE, ev.getElementType());

        ArrayValue rv = (ArrayValue) pv.getRawValue();
        assertArrayEquals(new int[] { 0 }, rv.getDimensions());
        assertEquals(Type.UINT32, rv.getElementType());
    }

    @Test
    public void testEmptyArray3() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(0);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet3"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("n"));
        assertEquals(0, pv.getEngValue().getUint32Value());

        pv = pvl.getFirstInserted(param("array3"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 0 }, ev.getDimensions());
        assertEquals(Type.AGGREGATE, ev.getElementType());

        ArrayValue rv = (ArrayValue) pv.getRawValue();
        assertArrayEquals(new int[] { 0 }, rv.getDimensions());
        assertEquals(Type.AGGREGATE, rv.getElementType());
    }

    @Test
    public void testEmptyArray4() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(0);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet4"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("n"));
        assertEquals(0, pv.getEngValue().getUint32Value());

        pv = pvl.getFirstInserted(param("array4"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 0 }, ev.getDimensions());
        assertEquals(Type.AGGREGATE, ev.getElementType());

        ArrayValue rv = (ArrayValue) pv.getRawValue();
        assertArrayEquals(new int[] { 0 }, rv.getDimensions());
        assertEquals(Type.AGGREGATE, rv.getElementType());
    }

    @Test
    public void test1ElementArray() {
        org.yamcs.LoggingUtils.enableTracing();
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putInt(1);
        bb.putInt(5);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("array1"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 1 }, ev.getDimensions());
        assertEquals(5, ev.getElementValue(0).getUint32Value());
    }

    @Test
    public void test1ElementArray4() {
        org.yamcs.LoggingUtils.enableTracing();
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putInt(1);
        bb.putInt(5);
        bb.putInt(3);
        ContainerProcessingResult cpr = processPacket(bb.array(), mdb.getSequenceContainer("/ArrayTmTest/packet4"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("array4"));
        ArrayValue ev = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { 1 }, ev.getDimensions());
        AggregateValue aggrv = (AggregateValue) ev.getElementValue(0);

        assertEquals(5, aggrv.getMemberValue("m1").getUint32Value());
        assertEquals("trei", aggrv.getMemberValue("m2").getStringValue());
    }

    private Parameter param(String name) {
        return mdb.getParameter("/ArrayTmTest/" + name);
    }

    private ContainerProcessingResult processPacket(byte[] buf, SequenceContainer sc) {
        return extractor.processPacket(buf, now, now, 0, sc);
    }
}
