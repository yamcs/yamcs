package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

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
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.Parameter;

/**
 * Java algorithms test
 */
public class AlgorithmManagerJavaExprTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        MdbFactory.reset();
    }

    static String instance = "refmdb";
    private Processor processor;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;
    private AlgorithmManager algoMgr;

    @BeforeEach
    public void beforeEachTest() throws Exception {
        EventProducerFactory.setMockup(true);

        tmGenerator = new RefMdbPacketGenerator();
        tmGenerator.setGenerationTime(TimeEncoding.getWallclockTime());

        algoMgr = new AlgorithmManager();
        processor = ProcessorFactory.create(instance, "AlgorithmManagerJavaTest", tmGenerator, algoMgr);
        prm = processor.getParameterRequestManager();
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        processor.quit();
    }

    @Test
    public void testJavaExprAlgo1() throws InvalidIdentification {
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJe");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();

        tmGenerator.generate_PKT1_1();

        assertEquals(1, params.size());
        ParameterValue pv = params.get(0);

        assertEquals(2.1672918, pv.getEngValue().getFloatValue(), 0.001);
        assertEquals(tmGenerator.getGenerationTime(), pv.getGenerationTime());
    }

    @Test
    public void testJavaExprAlgoFailure1() throws InvalidIdentification {
        CustomAlgorithm calg = (CustomAlgorithm) processor.getMdb().getAlgorithm("/REFMDB/SUBSYS1/float_addje");
        algoMgr.overrideAlgorithm(calg, "bum");
        assertTrue(algoMgr.algorithmsInError.containsKey(calg.getQualifiedName()));
    }

    @Test
    public void testJavaExprAlgoFailure2() throws InvalidIdentification {
        processor.start();

        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionJe");
        prm.addRequest(p, (ParameterConsumer) (subscriptionId, items) -> {
            params.addAll(items);
        });
        CustomAlgorithm calg = (CustomAlgorithm) processor.getMdb().getAlgorithm("/REFMDB/SUBSYS1/float_addje");
        algoMgr.overrideAlgorithm(calg, "AlgoFloatAdditionJe.setFloatValue((float)(5/0));");

        AlgorithmStatus status = algoMgr.getAlgorithmStatus(calg);
        assertEquals(0, status.getErrorCount());

        // after 10 errors, it should be automatically deactivated
        for (int i = 0; i < 10; i++) {
            status = algoMgr.getAlgorithmStatus(calg);
            assertTrue(status.getActive());
            assertEquals(i, status.getErrorCount());

            tmGenerator.generate_PKT1_1();

            status = algoMgr.getAlgorithmStatus(calg);

            assertTrue(status.getErrorMessage().contains("/ by zero"));
        }

        assertEquals(0, params.size());

        assertEquals(10, status.getErrorCount());
        assertTrue(status.getErrorMessage().contains("Deactivated after 10 errors"));
        assertFalse(status.getActive());
    }
}
