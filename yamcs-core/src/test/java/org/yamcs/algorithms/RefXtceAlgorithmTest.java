package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

import com.google.common.util.concurrent.AbstractService;

public class RefXtceAlgorithmTest {
    static String instance = "refxtce";
    private static Mdb mdb;
    private static Processor proc;
    private static ParameterRequestManager prm;

    private static MyProcService mpp = new MyProcService();

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        EventProducerFactory.setMockup(false);
        MdbFactory.reset();
        AlgorithmManager am = new AlgorithmManager();
        proc = ProcessorFactory.create(instance, "XtceAlgorithmTest", mpp, am);
        prm = proc.getParameterRequestManager();
        mdb = proc.getMdb();
    }

    @Test
    public void test1() {
        Parameter param3 = mdb.getParameter("/RefXtce/param3");
        Parameter param4 = mdb.getParameter("/RefXtce/param4");
        List<ParameterValue> params = subscribe(param3, param4);

        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putFloat(0.2f);
        buf.putShort((short) 10);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");

        assertEquals(2, params.size());
        assertEquals(5.1, params.get(0).getEngValue().getFloatValue(), 1e-5);
        assertEquals(5.1, params.get(1).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test2() {
        List<ParameterValue> params = subscribe(mdb.getParameter("/RefXtce/param6"));
        mpp.injectPacket(new byte[6], "/RefXtce/packet2");
        assertEquals(1, params.size());
        assertEquals(3.14, params.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test3() {
        List<ParameterValue> params = subscribe(mdb.getParameter("/RefXtce/param7"));
        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putFloat(0.28f);
        buf.putShort((short) 6);

        mpp.injectPacket(buf.array(), "/RefXtce/packet2");

        assertEquals(1, params.size());
        assertEquals(3.14, params.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testAvg4() {
        List<ParameterValue> params = subscribe(mdb.getParameter("/RefXtce/avg4_result"));
        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putFloat(0.28f);
        buf.putShort((short) 6);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");

        assertEquals(1, params.size());
        assertEquals(3.14, params.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testFlipFlop() {
        Parameter param11 = mdb.getParameter("/RefXtce/param11");
        List<ParameterValue> params = subscribe(param11);

        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putFloat(-10f);
        buf.putShort((short) 10);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");

        assertEquals(0, params.size());
        buf.rewind();
        buf.putFloat(10);
        buf.putShort((short) 10);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");
        assertEquals(1, params.size());

        assertEquals(true, params.get(0).getEngValue().getBooleanValue());
        params.clear();

        buf.rewind();
        buf.putFloat(-10f);
        buf.putShort((short) 10);
        mpp.injectPacket(buf.array(), "/RefXtce/packet2");
        assertEquals(0, params.size());
    }

    List<ParameterValue> subscribe(Parameter... plist) {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(plist), (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        return params;
    }

    static class MyProcService extends AbstractService implements ParameterProvider {
        ParameterProcessorManager ppm;
        XtceTmExtractor extractor;
        Mdb mdb;

        @Override
        public void init(Processor processor, YConfiguration config, Object spec) {
            this.ppm = processor.getParameterProcessorManager();
            ppm.addParameterProvider(this);
            mdb = processor.getMdb();
            extractor = new XtceTmExtractor(mdb);
            extractor.provideAll();
        }

        public void injectPacket(byte[] array, String name) {
            ContainerProcessingResult cpr = extractor.processPacket(array, 0, 0, 0, mdb.getSequenceContainer(name));
            ppm.process(cpr);
        }

        @Override
        public void setParameterProcessor(ParameterProcessor parameterProcessor) {
        }

        @Override
        public void startProviding(Parameter paramDef) {
        }

        @Override
        public void startProvidingAll() {

        }

        @Override
        public void stopProviding(Parameter paramDef) {
        }

        @Override
        public boolean canProvide(NamedObjectId paraId) {
            return true;
        }

        @Override
        public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
            return mdb.getParameter(paraId.getName());
        }

        @Override
        public boolean canProvide(Parameter param) {
            return true;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }

    public static class AvgAlgorithm extends AbstractAlgorithmExecutor {

        public AvgAlgorithm(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) {
            super(algorithmDef, execCtx);
        }

        @Override
        public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
            AggregateValue v = (AggregateValue) inputValues.get(0).getEngValue();
            float m1 = v.getMemberValue(0).getFloatValue();
            int m2 = v.getMemberValue(1).getUint32Value();

            ParameterValue pv = new ParameterValue(getOutputParameter(0));

            pv.setEngValue(ValueUtility.getFloatValue((m1 + m2) / 2));

            return new AlgorithmExecutionResult(pv);
        }
    }
}
