package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.ProcessorFactory;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlgorithmManagerTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
    }
    
    private XtceDb db;
    private YProcessor c;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManagerImpl prm;
    private Queue<Event> q;
    
    @Before
    public void beforeEachTest() throws ConfigurationException, ProcessorException {
        EventProducerFactory.setMockup(true);
        q=EventProducerFactory.getMockupQueue();
        
        db=XtceDbFactory.getInstance("refmdb");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator=new RefMdbPacketGenerator();
        tmGenerator=new RefMdbPacketGenerator();
        List<ParameterProvider> paramProviderList = new ArrayList<ParameterProvider>();
        Map<String, Object> jslib = new HashMap<String, Object>();
        Map<String, Object> config = new HashMap<String, Object>();
        jslib.put("JavaScript", Arrays.asList("mdb/algolib.js"));
        jslib.put("python", Arrays.asList("mdb/algolib.py"));
        config.put("libraries", jslib);
        paramProviderList.add(new AlgorithmManager("refmdb", config));
        SimpleTcTmService tmtcs = new SimpleTcTmService(tmGenerator, paramProviderList, null);
        
        c=ProcessorFactory.create("refmdb", "AlgorithmManagerTest", "refmdb", tmtcs, "junit");
        prm=c.getParameterRequestManager();
    }
    
    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        c.quit();
    }

    @Test
    public void testFloatAdd() throws InvalidIdentification {
        Parameter floatPara = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_1_2").build());
        Parameter floatAddition = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAddition").build());

        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(Arrays.asList(floatPara, floatAddition), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_1();
        assertEquals(2, params.size());
        for(ParameterValue pvwi:params) {
            if(pvwi.getParameter().equals(floatPara)) {
                assertEquals(0.1672918, pvwi.getEngValue().getFloatValue(), 0.001);
            } else if(pvwi.getParameter().equals(floatAddition)) {
                assertEquals(2.1672918, pvwi.getRawValue().getFloatValue(), 0.001);
            } else {
                fail("Unexpected parameter "+pvwi.getParameter());
            }
        }
    }

    @Ignore
    @Test
    //this can be used to see that the performance of javascript is much worse in some later versions of Java 6
    //OpenJDK 7 is very fast.
    public void testJavascriptPerformanceFloatAdd() throws InvalidIdentification {
        List<Parameter> paraList=new ArrayList<Parameter>();
        paraList.add(prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoYprFloat").build()));
        paraList.add(prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_1_2").build()));

        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(paraList, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        long t0 = System.currentTimeMillis();
        int n = 100000;
        for(int i = 0 ; i<n; i++)  {
            tmGenerator.generate_PKT1_1();
        }
        long t1 = System.currentTimeMillis();
        assertEquals(2*n, params.size());
    }
    
    @Test
    public void testSlidingWindow() throws InvalidIdentification, InterruptedException {
	Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoWindowResult").build());
        final List<ParameterValue> params = new ArrayList<ParameterValue>();
        prm.addRequest(p, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        long startTime=TimeEncoding.getWallclockTime();
        tmGenerator.generate_PKT1_6(1, 2, startTime, startTime);
        assertEquals(0, params.size()); // Windows:  [*  *  *  1]  &&  [*  2]
        
        tmGenerator.generate_PKT1_6(2, 4, startTime+1, startTime+1);
        assertEquals(0, params.size()); // Windows:  [*  *  1  2]  &&  [2  4]
        
        tmGenerator.generate_PKT1_6(3, 6, startTime+2, startTime+2);
        assertEquals(0, params.size()); // Windows:  [*  1  2  3]  &&  [4  6]
        
        // Production starts only when all relevant values for the expression are present
        tmGenerator.generate_PKT1_6(5, 8, startTime+3, startTime+3);
        assertEquals(1, params.size()); // Windows:  [1  2  3  5]  &&  [6  8] => produce (1 + 5) * 6
        assertEquals(36, params.get(0).getEngValue().getUint32Value());
        
        params.clear();
        tmGenerator.generate_PKT1_6(8, 10, startTime+4, startTime+4);
        assertEquals(1, params.size()); // Windows:  [2  3  5  8]  &&  [8 10] => produce (2 + 8) * 8
        assertEquals(80, params.get(0).getEngValue().getUint32Value());
    }
    
    @Test
    public void testEvents() throws Exception {
        // No need to subscribe. This algorithm doesn't have any outputs
        // and is therefore auto-activated (will only trigger if an input changes)

        c.start();
        tmGenerator.generate_PKT1_6(1, 0);
        assertEquals(1, q.size());
        Event evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("low", evt.getMessage());
        assertEquals(EventSeverity.INFO, evt.getSeverity());
        
        tmGenerator.generate_PKT1_6(7, 0);
        assertEquals(1, q.size());
        evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("med", evt.getMessage());
        assertEquals(EventSeverity.WARNING, evt.getSeverity());
        
        tmGenerator.generate_PKT1_6(10, 0);
        assertEquals(1, q.size());
        evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("high", evt.getMessage());
        assertEquals(EventSeverity.ERROR, evt.getSeverity());
    }
    
    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatDivision").build());
        prm.addRequest(p, new ParameterConsumer() {
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
    
    @Test
    public void testAlgorithmChaining() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatMultiplication").build());
        int subscriptionId=prm.addRequest(p, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
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
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        int subscriptionId=prm.addRequest(Arrays.asList(
                prm.getParameter("/REFMDB/SUBSYS1/AlgoFloatAverage"),
                prm.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_1")
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
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
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoCalibrationEnum"), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_6(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getRawValue().getUint32Value());
        assertEquals("one_why not", params.get(0).getEngValue().getStringValue());
    }

    @Test
    public void testBooleanAlgorithms() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(Arrays.asList(
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoBooleanTrueOutcome"),
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoBooleanFalseOutcome")
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_9();
        assertEquals(2, params.size());
        assertEquals(true, params.get(0).getRawValue().getBooleanValue());
        assertEquals(true, params.get(0).getEngValue().getBooleanValue());
        
        assertEquals(false, params.get(1).getRawValue().getBooleanValue());
        assertEquals(false, params.get(1).getEngValue().getBooleanValue());
    }
    
    @Test
    public void testFloatCalibration() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoCalibrationFloat"), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT1_6(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getRawValue().getUint32Value());
        assertEquals(0.0001672918, params.get(0).getEngValue().getFloatValue(), 1e-8);
    }
    
    @Test
    public void testSeparateUpdate() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoSeparateUpdateOutcome"), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
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
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(Arrays.asList(
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoUpdatedOut"),
        	prm.getParameter("/REFMDB/SUBSYS1/AlgoUnupdatedOut")), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> items) {
                params.addAll(items);
            }
        });

        c.start();
        int pIntegerPara16_1 = 5;
        tmGenerator.generate_PKT1_6(pIntegerPara16_1, 0);
        assertEquals(1, params.size());
        assertEquals("/REFMDB/SUBSYS1/AlgoUpdatedOut", params.get(0).getParameter().getQualifiedName());
        assertEquals(pIntegerPara16_1, params.get(0).getEngValue().getUint32Value());
    }
    
    
    @Test
    public void testSelectiveRun() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/AlgoSelectiveOut"), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, List<ParameterValue> items) {
                        params.addAll(items);
                    }
        });

        c.start();
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
        assertEquals(pIntegerPara16_1+tmGenerator.pFloatPara1_1_3, params.get(1).getEngValue().getFloatValue(), 1e-6);
    }
    
    
    @Test
    public void testOnPeriodicRate() throws InvalidIdentification, InterruptedException {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(prm.getParameter("/REFMDB/SUBSYS1/OnPeriodicRateOut"), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, List<ParameterValue> items) {
                        params.addAll(items);
                    }
        });

        c.start();
        Thread.sleep(10000);
    }
}

