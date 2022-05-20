package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ByteArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.protobuf.Db.Event;

public class ColumnSerializerTest {
    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void test1() {
        ColumnDefinition cd = new ColumnDefinition("test", DataType.protobuf(Event.class.getName()));
        ColumnSerializer<Event> cs = ColumnSerializerFactory.getProtobufSerializer(cd);
        Event ev = Event.newBuilder().setSource("test1").setGenerationTime(1000).setType("evtype").setMessage("msg")
                .build();

        ByteBuffer bb = ByteBuffer.allocate(1000);
        bb.mark();

        cs.serialize(bb, ev);
        bb.reset();
        Event ev1 = cs.deserialize(bb, cd);

        assertEquals(ev, ev1);

        ColumnDefinition cd1 = new ColumnDefinition("/test/abc", DataType.PARAMETER_VALUE);
        ColumnSerializer<ParameterValue> cs1 = ColumnSerializerFactory.getBasicColumnSerializerV3(cd1.type);
        ParameterValue pv = new ParameterValue("/test/abc");
        pv.setRawValue(ValueUtility.getUint32Value(1));
        pv.setEngineeringValue(ValueUtility.getDoubleValue(3.14));
        pv.setGenerationTime(1000);

        bb.mark();
        cs1.serialize(bb, pv);

        bb.reset();
        ParameterValue pv1 = cs1.deserialize(bb, cd1);
        verify(pv, pv1);

        bb.position(0);
        cs.serialize(bb, ev);
        cs1.serialize(bb, pv);

        bb.position(0);
        ev1 = cs.deserialize(bb, cd);
        assertEquals(ev, ev1);

        pv1 = cs1.deserialize(bb, cd1);
        verify(pv, pv1);
    }

    void verify(ParameterValue expected, ParameterValue actual) {
        assertEquals(expected.getParameterQualifiedName(), actual.getParameterQualifiedName());
        assertEquals(expected.getGenerationTime(), actual.getGenerationTime());
        assertEquals(expected.getEngValue(), actual.getEngValue());

    }

    @Test
    public void testArrays() {
        DataType arrayDt = DataType.array(DataType.STRING);

        ColumnDefinition cd = new ColumnDefinition("test", arrayDt);

        ColumnSerializer<List> cs = new ColumnSerializerV3.ArrayColumnSerializer(
                ColumnSerializerFactory.getBasicColumnSerializerV3(DataType.STRING));

        List<String> l1 = Arrays.asList("a", "ab", "abcd");
        ByteArray array = new ByteArray();
        cs.serialize(array, l1);

        List<String> l2 = cs.deserialize(array, cd);
        assertEquals(array.size(), array.position());
        assertEquals(l1, l2);

        ByteBuffer byteBuf = ByteBuffer.allocate(array.size());
        cs.serialize(byteBuf, l1);
        byteBuf.position(0);
        List<String> l3 = cs.deserialize(byteBuf, cd);
        assertFalse(byteBuf.hasRemaining());
        assertEquals(l1, l3);
    }
}
