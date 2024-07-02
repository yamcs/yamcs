package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Parameter;

public class LocalParameterManagerTest {
    Mdb mdb;
    MyParamProcessor paraProc;
    LocalParameterManager localParamMgr;
    Parameter p1, p2, p4, p7, p9;

    @BeforeAll
    static public void setupTime() {
        YConfiguration.setupTest(null);
        MdbFactory.reset();

    }

    @BeforeEach
    public void beforeTest() throws Exception {
        localParamMgr = new LocalParameterManager();
        mdb = MdbFactory.createInstanceByConfig("refmdb");
        localParamMgr.init("test", mdb);
        paraProc = new MyParamProcessor();
        localParamMgr.setParameterProcessor(paraProc);

        p1 = mdb.getParameter("/REFMDB/SUBSYS1/LocalPara1");
        p2 = localParamMgr.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara2").build());

        p4 = mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue4");
        p7 = mdb.getParameter("/REFMDB/SUBSYS1/LocalParaWithInitialValue7");
        p9 = mdb.getParameter("/REFMDB/SUBSYS1/LocalParaTime9");

        assertNotNull(p1);
        assertNotNull(p2);
    }

    @Test
    public void test() throws Exception {
        assertFalse(
                localParamMgr.canProvide(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build()));
        localParamMgr.startProviding(p1);

        ParameterValue pv1 = new ParameterValue(p1);
        pv1.setEngValue(ValueUtility.getUint32Value(3));
        ParameterValue pv2 = new ParameterValue(p2);
        pv2.setEngValue(ValueUtility.getDoubleValue(2.72));

        List<ParameterValue> pvList = Arrays.asList(pv1, pv2);

        localParamMgr.updateParameters(pvList);
        Collection<ParameterValue> pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(pvs);

        assertEquals(1, pvs.size());
        ParameterValue pv = pvs.iterator().next();
        assertEquals(p1, pv.getParameter());

        localParamMgr.stopProviding(p1);
        localParamMgr.updateParameters(pvList);
        pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertNull(pvs);

        localParamMgr.startProviding(p1);
        localParamMgr.startProviding(p2);
        localParamMgr.updateParameters(pvList);
        pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(2, pvs.size());

        localParamMgr.stopProviding(p2);
        localParamMgr.updateParameters(pvList);
        pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(1, pvs.size());
        pv = pvs.iterator().next();
        assertEquals(p1, pv.getParameter());

        localParamMgr.startProvidingAll();

        localParamMgr.updateParameters(pvList);
        pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(2, pvs.size());
    }

    @Test
    public void testTypeConversion() throws Exception {
        localParamMgr.startProviding(p2);
        ParameterValue pv2 = new ParameterValue(p2);
        pv2.setEngValue(ValueUtility.getUint32Value(3));
        localParamMgr.updateParameters(Arrays.asList(pv2));

        List<ParameterValue> pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(3.0, pvs.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testTypeConversion2() throws Exception {
        localParamMgr.startProviding(p4);
        ParameterValue pv4 = new ParameterValue(p4);
        AggregateParameterType p4type = (AggregateParameterType) p4.getParameterType();
        AggregateValue sentv = new AggregateValue(p4type.getMemberNames());
        sentv.setMemberValue("member1", ValueUtility.getSint64Value(32)); // will be converted to UINT32
        sentv.setMemberValue("member2", ValueUtility.getSint64Value(10)); // will be converted to FLOAT
        pv4.setEngValue(sentv);
        localParamMgr.updateParameters(Arrays.asList(pv4));

        List<ParameterValue> pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(1, pvs.size());
        AggregateValue rcvd = (AggregateValue) pvs.get(0).getEngValue();
        assertEquals(32, rcvd.getMemberValue("member1").getUint32Value());
        assertEquals(10, rcvd.getMemberValue("member2").getFloatValue(), 1e-5);
    }

    @Test
    public void testTypeConversion7() throws Exception {
        localParamMgr.startProviding(p7);
        ParameterValue pv7 = new ParameterValue(p7);
        ArrayValue sentv = new ArrayValue(new int[] { 2 }, Yamcs.Value.Type.SINT32);
        sentv.setElementValue(0, ValueUtility.getSint32Value(1));// will be converted to FLOAT
        sentv.setElementValue(1, ValueUtility.getSint32Value(2));// will be converted to FLOAT
        pv7.setEngValue(sentv);
        localParamMgr.updateParameters(Arrays.asList(pv7));

        List<ParameterValue> pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(1, pvs.size());
        ArrayValue rcvd = (ArrayValue) pvs.get(0).getEngValue();
        assertEquals(1, rcvd.getElementValue(0).getFloatValue(), 1e-5);
        assertEquals(2, rcvd.getElementValue(1).getFloatValue(), 1e-5);
    }

    @Test
    public void testTypeConversion9() throws Exception {
        String ts = "2020-03-09T12:09:00Z";

        localParamMgr.startProviding(p9);
        ParameterValue pv9 = new ParameterValue(p9);
        pv9.setEngValue(ValueUtility.getStringValue(ts));
        localParamMgr.updateParameters(Arrays.asList(pv9));

        List<ParameterValue> pvs = paraProc.received.poll(5, TimeUnit.SECONDS);
        assertEquals(1, pvs.size());
        TimestampValue tv = (TimestampValue) pvs.get(0).getEngValue();
        assertEquals(TimeEncoding.parse(ts), tv.getTimestampValue());
    }

    @Test
    public void testInvalidConversion1() {
        assertThrows(IllegalArgumentException.class, () -> {
            localParamMgr.startProviding(p1);
            ParameterValue pv1 = new ParameterValue(p1);
            pv1.setEngValue(ValueUtility.getUint64Value(Integer.MAX_VALUE * 2 + 1)); // out of range for UINT32
            localParamMgr.updateParameters(Arrays.asList(pv1));
        });
    }

    class MyParamProcessor implements ParameterProcessor {
        BlockingQueue<List<ParameterValue>> received = new LinkedBlockingQueue<>();

        @Override
        public void process(ProcessingData data) {
            try {
                received.put(new ArrayList<>(data.getTmParams()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
