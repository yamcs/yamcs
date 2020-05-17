package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

public class ColumnSerializerTest {
    @BeforeClass
    public static void beforeClass() {
        TimeEncoding.setUp();
    }
    
    @Test
    public void test1() throws IOException {
        ColumnDefinition cd = new ColumnDefinition("test", DataType.protobuf(Event.class.getName()));
        ColumnSerializer<Event> cs = ColumnSerializerFactory.getProtobufSerializer(cd);
        Event ev = Event.newBuilder().setSource("test1").setGenerationTime(1000).setType("evtype").setMessage("msg").build();
        
        ByteBuffer bb = ByteBuffer.allocate(1000);
        bb.mark();
        
        cs.serialize(bb, ev);
        System.out.println("bbposition: "+bb.position());
        bb.reset();
        Event ev1 = cs.deserialize(bb, cd);
        
        assertEquals(ev, ev1);
        
        ColumnDefinition cd1 = new ColumnDefinition("/test/abc", DataType.PARAMETER_VALUE);
        ColumnSerializer<ParameterValue> cs1 = ColumnSerializerFactory.getBasicColumnSerializer(cd1.type);
        ParameterValue pv = new ParameterValue("/test/abc");
        pv.setRawValue(ValueUtility.getUint32Value(1));
        pv.setEngineeringValue(ValueUtility.getDoubleValue(3.14));
        pv.setGenerationTime(1000);
        
        bb.mark();
        cs1.serialize(bb, pv);
        System.out.println("bbposition: "+bb.position());
        
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
        assertEquals(expected.getParameterQualifiedNamed(), actual.getParameterQualifiedNamed());
        assertEquals(expected.getGenerationTime(), actual.getGenerationTime());
        assertEquals(expected.getEngValue(), actual.getEngValue());
        
    }
    
    
}
