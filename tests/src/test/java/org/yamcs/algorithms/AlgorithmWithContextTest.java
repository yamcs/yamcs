package org.yamcs.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.tests.RefMdbPacketGenerator;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

public class AlgorithmWithContextTest {

    private Mdb db;
    private Processor proc;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterProcessorManager ppm;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        EventProducerFactory.setMockup(true);
    }

    @BeforeEach
    public void beforeEachTest() throws Exception {
        db = MdbFactory.getInstance("refmdb");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();
        tmGenerator = new RefMdbPacketGenerator();
        Map<String, Object> jslib = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        config.put("libraries", jslib);

        AlgorithmManager am = new AlgorithmManager();
        proc = ProcessorFactory.create("refmdb", "AlgorithmManagerTest", tmGenerator, am);
        ppm = proc.getParameterProcessorManager();
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        proc.quit();
    }

    @Test
    public void testIt() throws InvalidIdentification {
        final ArrayList<Object> params = new ArrayList<>();
        proc.start();

        AlgorithmManager algm = ppm.getParameterProvider(AlgorithmManager.class);
        AlgorithmExecutionContext ctx = algm.createContext("test");
        Algorithm alg = db.getAlgorithm("/REFMDB/SUBSYS1/ctx_param_test");
        algm.activateAlgorithm(alg, ctx)
                .addExecListener((inputValues, returnValue, outputValues) -> params.add(returnValue));

        tmGenerator.generate_PKT1_1();
        Parameter p = db.getParameter("/yamcs/cmd/para1");
        ParameterValue pv = new ParameterValue(p);
        pv.setEngValue(ValueUtility.getUint32Value(10));

        ctx.process(0, ProcessingData.createForTestCmd(pv));

        assertEquals(2, params.size());
        assertNull(params.get(0));
        assertEquals(10, ((Number) params.get(1)).intValue());
    }
}
