package org.yamcs.algorithms;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.ProcessorFactory;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlgorithmWithContextTest {

    private XtceDb db;
    private Processor c;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        XtceDbFactory.reset();
        EventProducerFactory.setMockup(true);
    }

    @Before
    public void beforeEachTest() throws ConfigurationException, ProcessorException {
        db = XtceDbFactory.getInstance("refmdb");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();
        tmGenerator = new RefMdbPacketGenerator();
        Map<String, Object> jslib = new HashMap<String, Object>();
        Map<String, Object> config = new HashMap<String, Object>();
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        config.put("libraries", jslib);

        c = ProcessorFactory.create("refmdb", "AlgorithmManagerTest", tmGenerator, new AlgorithmManager("refmdb", config));
        prm = c.getParameterRequestManager();
    }

    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        c.quit();
    }

    @Test
    public void testIt() throws InvalidIdentification {
        final ArrayList<Object> params = new ArrayList<Object>();
        c.start();

        AlgorithmManager algm = prm.getParameterProvider(AlgorithmManager.class);
        AlgorithmExecutionContext ctx = algm.createContext("test");
        Algorithm alg = db.getAlgorithm("/REFMDB/SUBSYS1/ctx_param_test");
        algm.activateAlgorithm(alg, ctx, new AlgorithmExecListener() {
            @Override
            public void algorithmRun(Object returnValue, List<ParameterValue> outputValues) {
                params.add(returnValue);
            }
        });

        tmGenerator.generate_PKT1_1();
        Parameter p = db.getParameter("/yamcs/cmd/para1");
        ParameterValue pv = new ParameterValue(p);
        pv.setEngineeringValue(ValueUtility.getUint32Value(10));
        algm.updateParameters(Arrays.asList(pv), ctx);

        assertEquals(2, params.size());
        assertNull(params.get(0));
        assertEquals(10, ((Integer) params.get(1)).intValue());
    }
}
