package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Just a small sanity check to verify python/jython still works.
 * Uses algorithms in the spreadsheet that are interpreted the same in javascript and python
 */
public class AlgorithmManagerPyTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup(instance);
        ManagementService.setup(false);
        XtceDbFactory.reset();
    }
    static String instance = "refmdb-py";
    private XtceDb db;
    private YProcessor c;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManagerImpl prm;
    
    @Before
    public void beforeEachTest() throws ConfigurationException, ProcessorException {
        EventProducerFactory.setMockup(true);
        
        db=XtceDbFactory.getInstance(instance);
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator=new RefMdbPacketGenerator();
        List<ParameterProvider> paramProviderList = new ArrayList<ParameterProvider>();
        
        Map<String, Object> jslib = new HashMap<String, Object>();
        Map<String, Object> config = new HashMap<String, Object>();
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));
        
        config.put("libraries", jslib);
        AlgorithmManager am = new AlgorithmManager(instance, config);
        paramProviderList.add(am);
        
        
        SimpleTcTmService tmtcs = new SimpleTcTmService(tmGenerator, paramProviderList, null);
        c=ProcessorFactory.create(instance, "AlgorithmManagerPyTest", "refmdb-py", tmtcs, "junit");
        prm=c.getParameterRequestManager();
    }
    

    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        c.quit();
    }
    
    @Test
    public void testFloats() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        Parameter p = prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAdditionPy");
        prm.addRequest(p, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
        	params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(2.1672918, params.get(0).getEngValue().getFloatValue(), 0.001);
    }
    
    @Test
    public void testSignedIntegers() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(Arrays.asList(
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome1"),
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome2"),
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome3"),
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoNegativeOutcome4")
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_8(2,-2);
        assertEquals(4, params.size());
        assertEquals(2, params.get(0).getEngValue().getSint32Value());
        assertEquals(-2, params.get(1).getEngValue().getSint32Value());
        assertEquals(-2, params.get(2).getEngValue().getSint32Value());
        assertEquals(2, params.get(3).getEngValue().getSint32Value());
    }

    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatDivisionPy"), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara1_1_1, params.get(0).getEngValue().getFloatValue()*3, 0.001);
    }
}
