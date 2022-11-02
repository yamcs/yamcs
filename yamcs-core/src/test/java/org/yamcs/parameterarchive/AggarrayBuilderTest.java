package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.ValueUtility;

public class AggarrayBuilderTest {

    @Test
    public void test1() {
        ParameterId px = new MyPid("/sys1/a.x");
        ParameterId py = new MyPid("/sys1/a.y");
        AggrrayBuilder builder = new AggrrayBuilder(px, py);
        builder.setValue(px, ValueUtility.getUint32Value(1));
        builder.setValue(py, ValueUtility.getUint32Value(2));
        AggregateValue av = (AggregateValue) builder.build();
        assertEquals(1, av.getMemberValue("x").getUint32Value());
        assertEquals(2, av.getMemberValue("y").getUint32Value());
    }

    @Test
    public void test2() {
        ParameterId p1 = new MyPid("/sys1/a[0]");
        ParameterId p2 = new MyPid("/sys1/a[1]");
        AggrrayBuilder builder = new AggrrayBuilder(p1, p2);
        builder.setValue(p1, ValueUtility.getUint32Value(1));
        builder.setValue(p2, ValueUtility.getUint32Value(2));
        ArrayValue av = (ArrayValue) builder.build();
        assertEquals(1, av.getElementValue(0).getUint32Value());
        assertEquals(2, av.getElementValue(1).getUint32Value());
    }

    @Test
    public void test3() {
        ParameterId px0 = new MyPid("/sys1/a.x[0]");
        ParameterId px1 = new MyPid("/sys1/a.x[1]");
        ParameterId py = new MyPid("/sys1/a.y");
        AggrrayBuilder builder = new AggrrayBuilder(px0, px1, py);
        builder.setValue(px0, ValueUtility.getUint32Value(1));
        builder.setValue(px1, ValueUtility.getUint32Value(2));
        builder.setValue(py, ValueUtility.getUint32Value(3));

        AggregateValue pv = (AggregateValue) builder.build();

        ArrayValue av = (ArrayValue) pv.getMemberValue("x");
        assertEquals(1, av.getElementValue(0).getUint32Value());
        assertEquals(2, av.getElementValue(1).getUint32Value());

        assertEquals(3, pv.getMemberValue("y").getUint32Value());
    }

    @Test
    public void test4() {
        ParameterId pbx = new MyPid("/sys1/a.b.x");
        ParameterId pby = new MyPid("/sys1/a.b.y");
        ParameterId py = new MyPid("/sys1/a.y");
        AggrrayBuilder builder = new AggrrayBuilder(pbx, pby, py);

        builder.setValue(pbx, ValueUtility.getUint32Value(1));
        builder.setValue(pby, ValueUtility.getUint32Value(2));
        builder.setValue(py, ValueUtility.getUint32Value(3));

        AggregateValue pv = (AggregateValue) builder.build();

        AggregateValue av = (AggregateValue) pv.getMemberValue("b");
        assertEquals(1, av.getMemberValue("x").getUint32Value());
        assertEquals(2, av.getMemberValue("y").getUint32Value());

        assertEquals(3, pv.getMemberValue("y").getUint32Value());
    }

    @Test
    public void test5() {
        ParameterId pb0x = new MyPid("/sys1/a.b[0].x");
        ParameterId pb0y = new MyPid("/sys1/a.b[0].y");
        ParameterId pb1x = new MyPid("/sys1/a.b[1].x");
        ParameterId pb1y = new MyPid("/sys1/a.b[1].y");
        ParameterId py = new MyPid("/sys1/a.y");
        AggrrayBuilder builder = new AggrrayBuilder(pb0x, pb0y, pb1x, pb1y, py);

        builder.setValue(pb0x, ValueUtility.getUint32Value(1));
        builder.setValue(pb0y, ValueUtility.getUint32Value(2));
        builder.setValue(pb1x, ValueUtility.getUint32Value(3));
        builder.setValue(pb1y, ValueUtility.getUint32Value(4));
        builder.setValue(py, ValueUtility.getUint32Value(5));

        AggregateValue pv = (AggregateValue) builder.build();

        ArrayValue av = (ArrayValue) pv.getMemberValue("b");
        AggregateValue pb0 = (AggregateValue) av.getElementValue(0);
        AggregateValue pb1 = (AggregateValue) av.getElementValue(1);

        assertEquals(1, pb0.getMemberValue("x").getUint32Value());
        assertEquals(2, pb0.getMemberValue("y").getUint32Value());
        assertEquals(3, pb1.getMemberValue("x").getUint32Value());
        assertEquals(4, pb1.getMemberValue("y").getUint32Value());

        assertEquals(5, pv.getMemberValue("y").getUint32Value());
    }

    @Test
    public void test6() {
        ParameterId p0x = new MyPid("/sys1/a[0].x");
        ParameterId p0y = new MyPid("/sys1/a[0].y");
        ParameterId p1x = new MyPid("/sys1/a[1].x");
        ParameterId p1y = new MyPid("/sys1/a[1].y");
        AggrrayBuilder builder = new AggrrayBuilder(p0x, p0y, p1x, p1y);

        builder.setValue(p0x, ValueUtility.getUint32Value(1));
        builder.setValue(p0y, ValueUtility.getUint32Value(2));
        builder.setValue(p1x, ValueUtility.getUint32Value(3));
        builder.setValue(p1y, ValueUtility.getUint32Value(4));

        ArrayValue pv = (ArrayValue) builder.build();

        AggregateValue p0 = (AggregateValue) pv.getElementValue(0);
        AggregateValue p1 = (AggregateValue) pv.getElementValue(1);

        assertEquals(1, p0.getMemberValue("x").getUint32Value());
        assertEquals(2, p0.getMemberValue("y").getUint32Value());
        assertEquals(3, p1.getMemberValue("x").getUint32Value());
        assertEquals(4, p1.getMemberValue("y").getUint32Value());

    }

    static class MyPid implements ParameterId {
        static int n = 1;
        String fqn;
        int pid;

        public MyPid(String fqn) {
            this.fqn = fqn;
            this.pid = n++;
        }

        @Override
        public Type getRawType() {
            return Type.UINT32;
        }

        @Override
        public Type getEngType() {
            return Type.UINT32;
        }

        @Override
        public int getPid() {
            return pid;
        }

        @Override
        public String getParamFqn() {
            return fqn;
        }

        @Override
        public boolean isSimple() {
            return true;
        }

        @Override
        public boolean hasRawValue() {
            return true;
        }

        @Override
        public IntArray getComponents() {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
