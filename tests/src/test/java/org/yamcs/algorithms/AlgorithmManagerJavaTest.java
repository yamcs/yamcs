package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.tests.RefMdbPacketGenerator;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.mdb.Mdb;
import org.yamcs.xtce.util.AggregateMemberNames;

/**
 * Java algorithms test
 */
public class AlgorithmManagerJavaTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        MdbFactory.reset();
    }

    static String instance = "refmdb";
    private Mdb db;
    private Processor processor;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;

    @BeforeEach
    public void beforeEachTest() throws Exception {
        EventProducerFactory.setMockup(true);

        db = MdbFactory.getInstance(instance);
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();

        Map<String, Object> jslib = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));

        config.put("libraries", jslib);
        AlgorithmManager am = new AlgorithmManager();
        processor = ProcessorFactory.create(instance, "AlgorithmManagerJavaTest", tmGenerator, am);
        prm = processor.getParameterRequestManager();
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        processor.quit();
    }

    @Test
    public void testJavaAlgo1() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoJavaFloat1");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(0.1672918, params.get(0).getEngValue().getDoubleValue(), 0.001);
    }

    @Test
    public void testJavaAlgo2() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoJavaFloat2");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(3.3672918, params.get(0).getEngValue().getDoubleValue(), 0.001);
    }

    @Test
    public void testJavaAlgo3() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoJavaFloat3");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(8.2672918, params.get(0).getEngValue().getDoubleValue(), 0.001);
    }

    @Test
    public void testJavaAlgo4() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoJavaAggr4");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());

        AggregateValue v = (AggregateValue) params.get(0).getEngValue();
        assertEquals(0.1672918051481247, v.getMemberValue("member3").getDoubleValue(), 0.001);
    }

    public static class MyAlgo1 extends AbstractAlgorithmExecutor {
        float v;

        public MyAlgo1(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) {
            super(algorithmDef, execCtx);
        }

        @Override
        public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
            Parameter p = algorithmDef.getOutputSet().get(0).getParameter();
            ParameterValue pv = new ParameterValue(p);

            pv.setEngValue(ValueUtility.getDoubleValue(v));
            return new AlgorithmExecutionResult(pv);
        }

        @Override
        protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
            v = newValue.getEngValue().getFloatValue();
        }
    }

    public static class MyAlgo2 extends AbstractAlgorithmExecutor {
        double x;
        float v;

        public MyAlgo2(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, Double x) {
            super(algorithmDef, execCtx);
            this.x = x;
        }

        @Override
        public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
            Parameter p = algorithmDef.getOutputSet().get(0).getParameter();
            ParameterValue pv = new ParameterValue(p);

            pv.setEngValue(ValueUtility.getDoubleValue(x + v));
            return new AlgorithmExecutionResult(Arrays.asList(pv));
        }

        @Override
        protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
            v = newValue.getEngValue().getFloatValue();
        }
    }

    public static class MyAlgo3 extends AbstractAlgorithmExecutor {
        int a;
        double b;
        String c;
        float v;

        public MyAlgo3(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, Map<String, Object> m) {
            super(algorithmDef, execCtx);
            this.a = (Integer) m.get("a");
            this.b = (Double) m.get("b");
            this.c = (String) m.get("c");
        }

        @Override
        public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
            Parameter p = algorithmDef.getOutputSet().get(0).getParameter();
            ParameterValue pv = new ParameterValue(p);

            pv.setEngValue(ValueUtility.getDoubleValue(a + b + c.length() + v));
            return new AlgorithmExecutionResult(pv);
        }

        @Override
        protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
            v = newValue.getEngValue().getFloatValue();
        }
    }

    public static class MyAlgo4 extends AbstractAlgorithmExecutor {
        int m1;
        float v;
        AggregateMemberNames aggrMbr;
        Parameter out;

        public MyAlgo4(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, Integer m1) {
            super(algorithmDef, execCtx);
            this.m1 = m1;
            out = execCtx.getMdb().getParameter("/REFMDB/SUBSYS1/AlgoJavaAggr4");
            ParameterType pt = out.getParameterType();

            this.aggrMbr = ((AggregateParameterType) pt).getMemberNames();
        }

        @Override
        public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
            ParameterValue pv = new ParameterValue(out);
            AggregateValue ev = new AggregateValue(aggrMbr);
            ev.setMemberValue("member1", ValueUtility.getUint32Value(m1));
            ev.setMemberValue("member2", ValueUtility.getUint32Value(23));
            ev.setMemberValue("member3", ValueUtility.getDoubleValue(v));

            pv.setEngValue(ev);
            return new AlgorithmExecutionResult(pv);
        }

        @Override
        protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
            v = newValue.getEngValue().getFloatValue();
        }

        public List<OutputParameter> getOutputList() {
            return Collections.singletonList(new OutputParameter(out));
        }
    }
}
