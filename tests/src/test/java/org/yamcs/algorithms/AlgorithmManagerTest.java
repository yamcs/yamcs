package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.ProcessorService;
import org.yamcs.ProcessorServiceWithConfig;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlgorithmTrace.Log;
import org.yamcs.protobuf.AlgorithmTrace.Run;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.tests.LoggingUtils;
import org.yamcs.tests.RefMdbPacketGenerator;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.protobuf.Db.Event;

public class AlgorithmManagerTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
    }

    private Mdb db;
    private Processor proc;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;
    private Queue<Event> q;
    AlgorithmManager algMgr;

    @BeforeEach
    public void beforeEachTest() throws Exception {
        EventProducerFactory.setMockup(true);
        q = EventProducerFactory.getMockupQueue();

        db = MdbFactory.getInstance("refmdb");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();
        tmGenerator = new RefMdbPacketGenerator();
        Map<String, Object> jslib = new HashMap<>();

        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        Map<String, Object> config = new HashMap<>();
        config.put("libraries", jslib);

        algMgr = new AlgorithmManager();
        proc = ProcessorFactory.create("refmdb", "AlgorithmManagerTest",
                getPwc(tmGenerator, YConfiguration.emptyConfig()),
                getPwc(algMgr, YConfiguration.wrap(config)));
        prm = proc.getParameterRequestManager();

    }

    static ProcessorServiceWithConfig getPwc(ProcessorService service, YConfiguration config) {
        return new ProcessorServiceWithConfig(service, service.getClass().getName(),
                service.getClass().getName(), config);
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        proc.quit();
    }

    @Test
    public void testFloatAdd() throws InvalidIdentification {
        Parameter floatPara = db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2");
        Parameter floatAddition = db.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJs");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(floatPara, floatAddition),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();

        // enable temporarily the logging at trrace level to see that we can have access to detailed information for
        // debugging (and also to improve the test coverage...)
        LoggingUtils.ArrayLogHandler alh = LoggingUtils.startCapture(ScriptAlgorithmExecutor.class);
        tmGenerator.generate_PKT1_1();
        LoggingUtils.stopCapture(ScriptAlgorithmExecutor.class);

        assertEquals(2, params.size());
        verifyEqual(params.get(0), floatPara, 0.1672918f);
        verifyEqual(params.get(1), floatAddition, 2.1672918f);

        // a bit fragile these messages but it's easy enough to change the test in case the messages would change
        assertTrue(alh.contains("Running algorithm float_add( f0: [r: 1000, v: 0.1672918], f1: [r: 2.0, v: 2.0])"));
        assertTrue(alh.contains("algorithm float_add outputs: "
                + "( null: OutputValueBinding [rawValue=null, value=2.1672918051481247, updated=true]) returnValue: null"));
    }

    @Test
    @Disabled
    // this can be used to test the performance of a very simple addition algorithm
    // to do that, you can comment in/out the right version of the parameter
    //
    // The number at the end includes the time it takes to process the packet, if you want to compute that time,
    // comment out the AlgoFloatAddition and comment in the FloatPara1_1_3, this is a parameter part of the packet
    // Results i7-8650U java 11:
    // no algorithm: 1500 nsec/iteration
    // java-expression: 1900 nsec/iteration
    // python: 5000 nsec/iteration
    // javascript: 23000 nsec/iteration
    public void testPerformanceFloatAdd() throws InvalidIdentification {
        List<Parameter> paraList = new ArrayList<>();

        paraList.add(prm.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        // paraList.add(prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionPy"));
        // paraList.add(prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJe"));
        paraList.add(prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJs"));

        // paraList.add(prm.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_3"));

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(paraList, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        long t0 = System.nanoTime();

        int m = 10;
        int n = 100000;
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                tmGenerator.generate_PKT1_1();
            }
            if (j == 0) {
                System.out.println(params.get(1));
            }
            assertEquals(2 * n, params.size());
            params.clear();
        }
        long t1 = System.nanoTime();
        System.out.println("time: " + (t1 - t0) / (n * m) + " nsec/iteration");

    }

    @Test
    public void testSlidingWindow() throws InvalidIdentification, InterruptedException {
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoWindowResult");
        final List<ParameterValue> params = new ArrayList<>();
        prm.addRequest(p, (subscriptionId, items) -> params.addAll(items));

        proc.start();
        long startTime = TimeEncoding.getWallclockTime();
        tmGenerator.generate_PKT1_6(1, 2, startTime, startTime);
        assertEquals(0, params.size()); // Windows: [* * * 1] && [* 2]

        tmGenerator.generate_PKT1_6(2, 4, startTime + 1, startTime + 1);
        assertEquals(0, params.size()); // Windows: [* * 1 2] && [2 4]

        tmGenerator.generate_PKT1_6(3, 6, startTime + 2, startTime + 2);
        assertEquals(0, params.size()); // Windows: [* 1 2 3] && [4 6]

        // Production starts only when all relevant values for the expression are present
        tmGenerator.generate_PKT1_6(5, 8, startTime + 3, startTime + 3);
        assertEquals(1, params.size()); // Windows: [1 2 3 5] && [6 8] => produce (1 + 5) * 6
        assertEquals(36, params.get(0).getEngValue().getUint32Value());

        params.clear();
        tmGenerator.generate_PKT1_6(8, 10, startTime + 4, startTime + 4);
        assertEquals(1, params.size()); // Windows: [2 3 5 8] && [8 10] => produce (2 + 8) * 8
        assertEquals(80, params.get(0).getEngValue().getUint32Value());
    }

    @Test
    public void testFunctions() throws Exception {
        // No need to subscribe. This algorithm doesn't have any outputs
        // and is therefore auto-activated (will only trigger if an input changes)

        proc.start();
        tmGenerator.generate_PKT1_6(1, 0);
        assertEquals(17, q.size());
        String algName = "/REFMDB/SUBSYS1/script_functions";
        String defaultSource = "CustomAlgorithm";

        for (EventSeverity sev : EventSeverity.values()) {
            if (sev == EventSeverity.ERROR) {
                continue;
            }

            String s = sev.name().toLowerCase();
            verifyEvent(q.poll(), sev, defaultSource, algName, s + " message1");
            verifyEvent(q.poll(), sev, s + "_source", s + " type", s + " message2");
        }

        // processor name
        verifyEventMessage(q.poll(), proc.getInstance());

        // processor name
        verifyEventMessage(q.poll(), proc.getName());

        // calibrate polynomial
        verifyEventMessage(q.poll(), "0.0001672918");

        // calibrate enumeration
        verifyEventMessage(q.poll(), "one_why not");

        // little endian to host
        verifyEventMessage(q.poll(), Long.toString(0xF3F2F1F0l));

        System.out.println(q.poll());
    }

    private void verifyEventMessage(Event evt, String message) {
        assertEquals(message, evt.getMessage());
    }

    private void verifyEvent(Event evt, EventSeverity severity, String source, String type, String message) {
        assertEquals(severity, evt.getSeverity());
        assertEquals(source, evt.getSource());
        assertEquals(type, evt.getType());
        assertEquals(message, evt.getMessage());
    }

    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatDivision");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getFloatValue() * 3, 0.001);
    }

    @Test
    public void testAlgorithmChaining() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatMultiplication");
        int subscriptionId = prm.addRequest(p, (ParameterConsumer) (subscriptionId1, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getFloatValue(), 0.001);

        // Test unsubscribe
        params.clear();
        prm.removeItemsFromRequest(subscriptionId, p);
        tmGenerator.generate_PKT1_1();
        assertTrue(params.isEmpty());

        // Subscribe again
        params.clear();
        prm.addItemsToRequest(subscriptionId, p);
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getFloatValue(), 0.001);
    }

    @Test
    public void testAlgorithmChainingWithWindowing() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        int subscriptionId = prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAverage"),
                prm.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_1")),
                (ParameterConsumer) (subscriptionId1, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getUint32Value());

        params.clear();
        tmGenerator.generate_PKT1_1();
        assertEquals(2, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getUint32Value());
        assertEquals((20 + 20 + 20 + (20 / 3.0)) / 4.0, params.get(1).getEngValue().getFloatValue(), 0.001);

        // Unsubscribe
        params.clear();
        prm.removeItemsFromRequest(subscriptionId, prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAverage"));

        tmGenerator.generate_PKT1_1();
        tmGenerator.generate_PKT1_1();
        assertEquals(2, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getUint32Value());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(1).getEngValue().getUint32Value());

        // Unsubscribe after subscribing to dependent algorithm's output as well
        params.clear();
        prm.addItemsToRequest(subscriptionId, Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAverage"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatMultiplication")));
        prm.removeItemsFromRequest(subscriptionId, prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAverage"));
        tmGenerator.generate_PKT1_1();
        // We should still get AlgoFloatMultiplication
        assertEquals(2, params.size());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_1", params.get(0).getParameter().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/AlgoFloatMultiplication", params.get(1).getParameter().getQualifiedName());
    }

    @Test
    public void testEnumCalibration() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoCalibrationEnum"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_6(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getRawValue().getUint32Value());
        assertEquals("one_why not", params.get(0).getEngValue().getStringValue());
    }

    @Test
    public void testBooleanAlgorithms() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoBooleanTrueOutcome"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoBooleanFalseOutcome")),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_9();
        assertEquals(2, params.size());
        assertEquals(true, params.get(0).getEngValue().getBooleanValue());
        assertEquals(false, params.get(1).getEngValue().getBooleanValue());
    }

    @Test
    public void testFloatCalibration() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoCalibrationFloat"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_6(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getRawValue().getUint32Value());
        assertEquals(0.0001672918, params.get(0).getEngValue().getFloatValue(), 1e-8);
    }

    @Test
    public void testSeparateUpdate() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoSeparateUpdateOutcome"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(0.1672918, params.get(0).getEngValue().getFloatValue(), 1e-8);

        params.clear();
        tmGenerator.generate_PKT1_6(5, 6);
        assertEquals(1, params.size());
        assertEquals(5.167291, params.get(0).getEngValue().getFloatValue(), 1e-6);

        params.clear();
        tmGenerator.generate_PKT1_6(4, 6);
        assertEquals(1, params.size());
        assertEquals(4.167291, params.get(0).getEngValue().getFloatValue(), 1e-6);
    }

    @Test
    public void testMarkedNotUpdated() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoUpdatedOut"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoUnupdatedOut")),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        int pIntegerPara16_1 = 5;
        tmGenerator.generate_PKT1_6(pIntegerPara16_1, 0);
        assertEquals(1, params.size());
        assertEquals("/REFMDB/SUBSYS1/AlgoUpdatedOut", params.get(0).getParameter().getQualifiedName());
        assertEquals(pIntegerPara16_1, params.get(0).getEngValue().getUint32Value());
    }

    @Test
    public void testSelectiveRun() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoSelectiveOut"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        int pIntegerPara16_1 = 5;
        tmGenerator.generate_PKT1_6(pIntegerPara16_1, 0);
        assertEquals(1, params.size());
        assertEquals("/REFMDB/SUBSYS1/AlgoSelectiveOut", params.get(0).getParameter().getQualifiedName());
        assertEquals(pIntegerPara16_1, params.get(0).getEngValue().getFloatValue(), 1e-6);

        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size()); // No change, not in OnParameterUpdate list

        pIntegerPara16_1 = 7;
        tmGenerator.generate_PKT1_6(pIntegerPara16_1, 0);
        assertEquals(2, params.size()); // Now change, also with updated float from PKT11
        assertEquals(pIntegerPara16_1 + tmGenerator.pFloatPara1_1_3, params.get(1).getEngValue().getFloatValue(), 1e-6);
    }

    @Test
    public void testOnPeriodicRate() throws InvalidIdentification, InterruptedException {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/OnPeriodicRateOut"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        Thread.sleep(10000);
    }

    @Test
    public void testBinaryInput() throws InvalidIdentification, InterruptedException {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/PrependedSizeBinary1"),
                prm.getParameter("/REFMDB/SUBSYS1/PrependedSizeBinary1_length")),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();

        tmGenerator.generate_PKT5();

        assertEquals(2, params.size());
        ParameterValue pv0 = params.get(0);
        ParameterValue pv1 = params.get(1);

        assertEquals("/REFMDB/SUBSYS1/PrependedSizeBinary1_length", pv1.getParameter().getQualifiedName());
        assertEquals(pv0.getEngValue().getBinaryValue().length, pv1.getEngValue().getUint32Value());
    }

    @Test
    public void testAlgoAggrInput() throws InvalidIdentification, InterruptedException {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoAggr1"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();

        tmGenerator.generate_PKT7();
        assertEquals(1, params.size());
        ParameterValue pv0 = params.get(0);
        assertEquals("/REFMDB/SUBSYS1/AlgoAggr1", pv0.getParameter().getQualifiedName());
        assertEquals(8.0, pv0.getEngValue().getDoubleValue(), 1e-5);
    }

    @Test
    public void testAlgoArrayInput() throws InvalidIdentification, InterruptedException {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoArray1"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        tmGenerator.generate_PKT8();
        assertEquals(1, params.size());
        ParameterValue pv0 = params.get(0);
        assertEquals("/REFMDB/SUBSYS1/AlgoArray1", pv0.getParameter().getQualifiedName());
        assertEquals(3.0, pv0.getEngValue().getDoubleValue(), 1e-5);
    }

    @Test
    public void testAllInOut() throws Exception {
        Parameter p_sint32 = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_sint32");
        Parameter p_uint32 = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_uint32");
        Parameter p_sint64 = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_sint64");
        Parameter p_uint64 = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_uint64");
        Parameter p_double = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_double");
        Parameter p_float = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_float");
        Parameter p_bool = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_bool");
        Parameter p_enum = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_enum");
        Parameter p_string = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_string");
        Parameter p_binary = db.getParameter("/REFMDB/SUBSYS1/AlgoOut_binary");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(
                Arrays.asList(p_sint32, p_uint32, p_sint64, p_uint64, p_double, p_float, p_bool, p_enum, p_string,
                        p_binary),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        // LoggingUtils.enableTracing();
        tmGenerator.generate_PKT12();
        assertEquals(10, params.size());

        // from generate_PKT12();
        assertEquals(-1, params.get(0).getEngValue().getSint32Value());
        assertEquals(0xF0F1F2F3, params.get(1).getEngValue().getUint32Value());
        assertEquals(-2, params.get(2).getEngValue().getSint64Value());
        assertEquals(0xF0F1F2F3F4F5F6F7l, params.get(3).getEngValue().getUint64Value());
        assertEquals(3.14, params.get(4).getEngValue().getDoubleValue(), 1e-5);
        assertEquals(2.72f, params.get(5).getEngValue().getFloatValue(), 1e-5);
        assertEquals(true, params.get(6).getEngValue().getBooleanValue());
        assertEquals("one_why not", params.get(7).getEngValue().getStringValue());

        // generate_PK12() sends "bla" and the algorithm adds " yes"
        assertEquals("bla yes", params.get(8).getEngValue().getStringValue());

        // from generate_PK12
        assertEquals("0102030405", StringConverter.arrayToHexString(params.get(9).getEngValue().getBinaryValue()));
    }

    @Test
    public void testTrace() throws InvalidIdentification {
        Parameter floatPara = db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2");
        Parameter floatAddition = db.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJs");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(floatPara, floatAddition),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        proc.start();
        Algorithm floatAddAlgo = db.getAlgorithm("/REFMDB/SUBSYS1/float_add");
        algMgr.enableTracing(floatAddAlgo);

        tmGenerator.generate_PKT1_1();
        assertEquals(2, params.size());
        verifyEqual(params.get(0), floatPara, 0.1672918f);
        verifyEqual(params.get(1), floatAddition, 2.1672918f);

        AlgorithmTrace trace = algMgr.getTrace(floatAddAlgo);
        assertEquals(1, trace.runs.size());
        Run run = trace.runs.getFirst();
        assertEquals(2, run.getInputsCount());
        assertEquals(1, run.getOutputsCount());

        Pvalue.ParameterValue in0 = run.getInputs(0);
        Pvalue.ParameterValue in1 = run.getInputs(1);

        Pvalue.ParameterValue out0 = run.getOutputs(0);

        assertEquals(0.1672918f, in0.getEngValue().getFloatValue(), 1e-5);
        assertEquals(2f, in1.getEngValue().getFloatValue(), 1e-5);
        assertEquals(2.1672918f, out0.getEngValue().getFloatValue(), 1e-5);

        assertEquals(1, trace.logs.size());
        Log traceLog = trace.logs.getFirst();
        assertEquals("adding 0.1672918051481247 and 2", traceLog.getMsg());

        algMgr.disableTracing(floatAddAlgo);
        assertNull(algMgr.getTrace(floatAddAlgo));
    }

    void verifyEqual(ParameterValue pv, Parameter p, float v) {
        assertEquals(p, pv.getParameter());
        assertEquals(v, pv.getEngValue().getFloatValue(), 1e-5);
    }
}
