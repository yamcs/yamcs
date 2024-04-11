package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.InvalidIdentification;
import org.yamcs.LoggingUtils;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.ValidationException;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

public class AlgorithmManagerErrorTest {
    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        TimeEncoding.setUp();
        MdbFactory.reset();

        // this test intentionally generates some errors which we don't want to see in the test output
        LoggingUtils.configureLogging(Level.SEVERE);
    }

    static String instance = "errmdb";
    private Mdb mdb;
    private Processor processor;
    private ParameterRequestManager prm;
    AlgorithmManager algMgr;
    Parameter p1, p2;

    @BeforeEach
    public void beforeEachTest() throws InitException, ProcessorException, ConfigurationException, ValidationException {
        EventProducerFactory.setMockup(true);

        mdb = MdbFactory.getInstance(instance);
        p1 = mdb.getParameter("/ERRMDB/para1");
        p2 = mdb.getParameter("/ERRMDB/para2");

        algMgr = new AlgorithmManager();
        processor = ProcessorFactory.create(instance, "AlgorithmManagerJavaTest", new MyParaProvider(), algMgr);
        prm = processor.getParameterProcessorManager().getParameterRequestManager();
    }

    @Test
    public void testAlgoError() throws InvalidIdentification {
        Parameter algoErrPara1 = mdb.getParameter("/ERRMDB/AlgoError1");
        Parameter algoErrPara2 = mdb.getParameter("/ERRMDB/AlgoError2");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(algoErrPara1, algoErrPara2),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        Algorithm errAlg1 = mdb.getAlgorithm("/ERRMDB/algo_producing_error1");
        Algorithm errAlg2 = mdb.getAlgorithm("/ERRMDB/algo_producing_error2");

        AlgorithmStatus status1 = algMgr.getAlgorithmStatus(errAlg1);
        assertEquals(status1.getRunCount(), 0);
        assertTrue(status1.hasErrorMessage());

        AlgorithmStatus status2 = algMgr.getAlgorithmStatus(errAlg2);
        assertEquals(status2.getRunCount(), 0);
        assertFalse(status2.hasErrorMessage());

        ParameterValue pv1 = new ParameterValue(p1);
        pv1.setEngValue(ValueUtility.getUint32Value(3));

        algMgr.process(getProcessingData(pv1));
        status1 = algMgr.getAlgorithmStatus(errAlg1);
        // errAlg1 doesn't run at all
        assertEquals(status1.getRunCount(), 0);
        assertTrue(status1.hasErrorMessage());

        // errAlg2 ran and produced error
        status2 = algMgr.getAlgorithmStatus(errAlg2);
        assertEquals(1, status2.getRunCount());
        assertEquals(1, status2.getErrorCount());

        assertTrue(status2.hasErrorMessage());
    }

    public static ProcessingData getProcessingData(ParameterValue pv) {
        ProcessingData data = ProcessingData.createForTmProcessing(new LastValueCache());
        data.addTmParam(pv);
        return data;
    }
}
