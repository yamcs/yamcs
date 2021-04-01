package org.yamcs.algorithms;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.InvalidIdentification;
import org.yamcs.LoggingUtils;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.ValidationException;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlgorithmManagerErrorTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TimeEncoding.setUp();
        XtceDbFactory.reset();

        // this test intentionally generates some errors which we don't want to see in the test output
        LoggingUtils.enableLogging(Level.SEVERE);
    }

    static String instance = "errmdb";
    private XtceDb db;
    private Processor processor;
    private ParameterRequestManager prm;
    AlgorithmManager algMgr;
    Parameter p1, p2;

    @Before
    public void beforeEachTest() throws InitException, ProcessorException, ConfigurationException, ValidationException {
        EventProducerFactory.setMockup(true);

        db = XtceDbFactory.getInstance(instance);
        p1 = db.getParameter("/ERRMDB/para1");
        p2 = db.getParameter("/ERRMDB/para2");

        algMgr = new AlgorithmManager();
        processor = ProcessorFactory.create(instance, "AlgorithmManagerJavaTest", new MyParaProvider(), algMgr);
        prm = processor.getParameterRequestManager();
    }

    @Test
    public void testAlgoError() throws InvalidIdentification {
        Parameter algoErrPara1 = db.getParameter("/ERRMDB/AlgoError1");
        Parameter algoErrPara2 = db.getParameter("/ERRMDB/AlgoError2");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(Arrays.asList(algoErrPara1, algoErrPara2),
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));

        processor.start();
        Algorithm errAlg1 = db.getAlgorithm("/ERRMDB/algo_producing_error1");
        Algorithm errAlg2 = db.getAlgorithm("/ERRMDB/algo_producing_error2");

        AlgorithmStatus status1 = algMgr.getAlgorithmStatus(errAlg1);
        assertEquals(status1.getRunCount(), 0);
        assertTrue(status1.hasErrorMessage());

        AlgorithmStatus status2 = algMgr.getAlgorithmStatus(errAlg2);
        assertEquals(status2.getRunCount(), 0);
        assertFalse(status2.hasErrorMessage());
        
        ParameterValue pv1 = new ParameterValue(p1);
        pv1.setEngineeringValue(ValueUtility.getUint32Value(3));
        
        algMgr.updateDelivery(ParameterValueList.asList(pv1));
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
}
