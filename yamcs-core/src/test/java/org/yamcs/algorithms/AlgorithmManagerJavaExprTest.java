package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Java algorithms test
 */
public class AlgorithmManagerJavaExprTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest(instance);
        XtceDbFactory.reset();
    }

    static String instance = "refmdb";
    private Processor processor;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;

    @Before
    public void beforeEachTest() throws Exception {
        EventProducerFactory.setMockup(true);

        tmGenerator = new RefMdbPacketGenerator();
        tmGenerator.setGenerationTime(TimeEncoding.getWallclockTime());

        AlgorithmManager am = new AlgorithmManager();
        processor = ProcessorFactory.create(instance, "AlgorithmManagerJavaTest", tmGenerator, am);
        prm = processor.getParameterRequestManager();
    }

    @After
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

}
