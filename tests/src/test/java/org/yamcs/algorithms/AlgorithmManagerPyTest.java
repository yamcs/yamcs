package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.algorithms.AlgorithmManagerTest.getPwc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.tests.RefMdbPacketGenerator;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

/**
 * Just a small sanity check to verify python/jython still works. Uses algorithms in the spreadsheet that are
 * interpreted the same in javascript and python
 */
public class AlgorithmManagerPyTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        MdbFactory.reset();
        // org.yamcs.LoggingUtils.enableLogging();
    }

    static String instance = "refmdb";
    private Mdb mdb;
    private Processor processor;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;

    @BeforeEach
    public void beforeEachTest() throws Exception {
        EventProducerFactory.setMockup(true);

        mdb = MdbFactory.getInstance(instance);
        assertNotNull(mdb.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();

        Map<String, Object> jslib = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));

        config.put("libraries", jslib);

        AlgorithmManager am = new AlgorithmManager();
        processor = ProcessorFactory.create("refmdb", "AlgorithmManagerPyTest",
                getPwc(tmGenerator, YConfiguration.emptyConfig()),
                getPwc(am, YConfiguration.wrap(config)));

        prm = processor.getParameterRequestManager();
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        processor.quit();
    }

    @Test
    public void testFloats() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionPy");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(2.1672918, params.get(0).getEngValue().getFloatValue(), 0.001);
    }

    @Test
    public void testTime() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoTestTimePy");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        long t = TimeEncoding.parse("2022-01-30T12:06:00Z");
        processor.start();
        long pt0 = processor.getCurrentTime();

        tmGenerator.setGenerationTime(t);
        tmGenerator.generate_PKT1_1();
        long pt1 = processor.getCurrentTime();

        assertEquals(1, params.size());
        long parav = params.get(0).getEngValue().getUint64Value();

        assertTrue(t + pt0 <= parav);

        assertTrue(parav <= t + pt1);
    }

    @Test
    public void testSignedIntegers() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome1"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome2"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome3"),
                prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome4")),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_8(2, -2);
        assertEquals(4, params.size());
        assertEquals(2, params.get(0).getEngValue().getSint32Value());
        assertEquals(-2, params.get(1).getEngValue().getSint32Value());
        assertEquals(-2, params.get(2).getEngValue().getSint32Value());
        assertEquals(2, params.get(3).getEngValue().getSint32Value());
    }

    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatDivisionPy"),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getFloatValue() * 3, 0.001);
    }
}
