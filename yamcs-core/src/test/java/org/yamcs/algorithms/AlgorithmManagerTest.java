package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValueWithId;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.RefMdbTmService;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlgorithmManagerTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false,false);
        XtceDbFactory.reset();
    }
    
    private XtceDb db;
    private Channel c;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;
    private Queue<Event> q;
    
    @Before
    public void beforeEachTest() throws ConfigurationException, ChannelException {
        EventProducerFactory.setMockup(true);
        q=EventProducerFactory.getMockupQueue();
        
        db=XtceDbFactory.getInstance("refmdb");
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));

        tmGenerator=new RefMdbPacketGenerator();
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- before creating chanel: ");
        try {
            c=ChannelFactory.create("refmdb", "AlgorithmManagerTest", "refmdb", "refmdb", new RefMdbTmService(tmGenerator), "refmdb", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- after creating chanel c:"+c);
        prm=c.getParameterRequestManager();
    }
    
    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        System.out.println(System.currentTimeMillis()+":"+Thread.currentThread()+"----------- after eachtest c:"+c);
        c.quit();
    }

    @Test
    public void testFloatAdd() throws InvalidIdentification {
        NamedObjectId floatParaId=NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build();
        NamedObjectId floatAddition=NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAddition").build();

        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(floatParaId, floatAddition), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(2, params.size());
        for(ParameterValueWithId pvwi:params) {
            if(pvwi.getId().equals(floatParaId)) {
                assertEquals(0.1672918, pvwi.getParameterValue().getEngValue().getFloatValue(), 0.001);
            } else if(pvwi.getId().equals(floatAddition)) {
                assertEquals(2.1672918, pvwi.getParameterValue().getRawValue().getFloatValue(), 0.001);
                assertEquals(2.1672918, pvwi.getParameterValue().getEngValue().getFloatValue(), 0.001);
            } else {
                fail("Unexpected id "+pvwi.getId());
            }
        }
    }

    @Ignore
    @Test
    //this can be used to see that the performance of javascript is much worse in some later versions of Java 6
    //OpenJDK 7 is very fast.
    public void testJavascriptPerformanceFloatAdd() throws InvalidIdentification {
        List<NamedObjectId> paraList=new ArrayList<NamedObjectId>();
        paraList.add(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoYprFloat").build());
        paraList.add(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara11_2").build());

        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(paraList, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        long t0 = System.currentTimeMillis();
        int n = 100000;
        for(int i = 0 ; i<n; i++)  {
            tmGenerator.generate_PKT11();
        }
        long t1 = System.currentTimeMillis();
        System.out.println("got "+params.size()+" parameters + in "+(t1-t0)/1000.0+" seconds");
        assertEquals(2*n, params.size());
    }
    
    @Test
    public void testSlidingWindow() throws InvalidIdentification, InterruptedException {
        List<NamedObjectId> subList = Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoWindowResult").build());
        final List<ParameterValueWithId> params = new ArrayList<ParameterValueWithId>();
        prm.addRequest(subList, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        long startTime=TimeEncoding.currentInstant();
        tmGenerator.generate_PKT16(1, 2, startTime, startTime);
        assertEquals(0, params.size()); // Windows:  [*  *  *  1]  &&  [*  2]
        
        tmGenerator.generate_PKT16(2, 4, startTime+1, startTime+1);
        assertEquals(0, params.size()); // Windows:  [*  *  1  2]  &&  [2  4]
        
        tmGenerator.generate_PKT16(3, 6, startTime+2, startTime+2);
        assertEquals(0, params.size()); // Windows:  [*  1  2  3]  &&  [4  6]
        
        // Production starts only when all relevant values for the expression are present
        tmGenerator.generate_PKT16(5, 8, startTime+3, startTime+3);
        assertEquals(1, params.size()); // Windows:  [1  2  3  5]  &&  [6  8] => produce (1 + 5) * 6
        assertEquals(36, params.get(0).getParameterValue().getEngValue().getUint32Value());
        
        params.clear();
        tmGenerator.generate_PKT16(8, 10, startTime+4, startTime+4);
        assertEquals(1, params.size()); // Windows:  [2  3  5  8]  &&  [8 10] => produce (2 + 8) * 8
        assertEquals(80, params.get(0).getParameterValue().getEngValue().getUint32Value());
    }
    
    @Test
    public void testEvents() throws Exception {
        // No need to subscribe. This algorithm doesn't have any outputs
        // and is therefore auto-activated (will only trigger if an input changes)

        c.start();
        tmGenerator.generate_PKT16(1, 0);
        assertEquals(1, q.size());
        Event evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("low", evt.getMessage());
        assertEquals(EventSeverity.INFO, evt.getSeverity());
        
        tmGenerator.generate_PKT16(7, 0);
        assertEquals(1, q.size());
        evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("med", evt.getMessage());
        assertEquals(EventSeverity.WARNING, evt.getSeverity());
        
        tmGenerator.generate_PKT16(10, 0);
        assertEquals(1, q.size());
        evt = q.poll();
        assertEquals("CustomAlgorithm", evt.getSource());
        assertEquals("script_events", evt.getType());
        assertEquals("high", evt.getMessage());
        assertEquals(EventSeverity.ERROR, evt.getSeverity());
    }
    
    @Test
    public void testExternalLibrary() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatDivision").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getFloatValue()*3, 0.001);
    }
    
    @Test
    public void testAlgorithmChaining() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        List<NamedObjectId> sublist=Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatMultiplication").build());
        int subscriptionId=prm.addRequest(sublist, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getFloatValue(), 0.001);
        
        // Test unsubscribe
        params.clear();
        prm.removeItemsFromRequest(subscriptionId, sublist);
        tmGenerator.generate_PKT11();
        assertTrue(params.isEmpty());
        
        // Subscribe again
        params.clear();
        prm.addItemsToRequest(subscriptionId, sublist);
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getFloatValue(), 0.001);
    }
    
    @Test
    public void testAlgorithmChainingWithWindowing() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        int subscriptionId=prm.addRequest(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAverage").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara11_1").build()
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getUint32Value());
        
        params.clear();
        tmGenerator.generate_PKT11();
        assertEquals(2, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getUint32Value());
        assertEquals((20 + 20 + 20 + (20 / 3.0)) / 4.0, params.get(1).getParameterValue().getEngValue().getFloatValue(), 0.001);
        
        // Unsubscribe
        params.clear();
        prm.removeItemsFromRequest(subscriptionId, Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAverage").build()));

        tmGenerator.generate_PKT11();
        tmGenerator.generate_PKT11();
        assertEquals(2, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getUint32Value());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(1).getParameterValue().getEngValue().getUint32Value());
        
        // Unsubscribe after subscribing to dependent algorithm's output as well
        params.clear();
        prm.addItemsToRequest(subscriptionId, Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAverage").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatMultiplication").build()));
        prm.removeItemsFromRequest(subscriptionId, Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAverage").build()));
        tmGenerator.generate_PKT11();
        // We should still get AlgoFloatMultiplication
        assertEquals(2, params.size());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_1", params.get(0).getParameterValue().getParameter().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/AlgoFloatMultiplication", params.get(1).getParameterValue().getParameter().getQualifiedName());
    }
    
    @Test
    public void testEnumCalibration() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoCalibrationEnum").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT16(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getParameterValue().getRawValue().getUint32Value());
        assertEquals("one_why not", params.get(0).getParameterValue().getEngValue().getStringValue());
    }

    @Test
    public void testBooleanAlgorithms() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoBooleanTrueOutcome").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoBooleanFalseOutcome").build()
        ), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT19();
        assertEquals(2, params.size());
        assertEquals(true, params.get(0).getParameterValue().getRawValue().getBooleanValue());
        assertEquals(true, params.get(0).getParameterValue().getEngValue().getBooleanValue());
        
        assertEquals(false, params.get(1).getParameterValue().getRawValue().getBooleanValue());
        assertEquals(false, params.get(1).getParameterValue().getEngValue().getBooleanValue());
    }
    
    @Test
    public void testFloatCalibration() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoCalibrationFloat").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT16(1, 1);
        assertEquals(1, params.size());
        assertEquals(1, params.get(0).getParameterValue().getRawValue().getUint32Value());
        assertEquals(0.0001672918, params.get(0).getParameterValue().getEngValue().getFloatValue(), 1e-8);
    }
    
    @Test
    public void testSeparateUpdate() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoSeparateUpdateOutcome").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(0.1672918, params.get(0).getParameterValue().getEngValue().getFloatValue(), 1e-8);
        
        params.clear();
        tmGenerator.generate_PKT16(5, 6);
        assertEquals(1, params.size());
        assertEquals(5.167291, params.get(0).getParameterValue().getEngValue().getFloatValue(), 1e-6);
        
        params.clear();
        tmGenerator.generate_PKT16(4, 6);
        assertEquals(1, params.size());
        assertEquals(4.167291, params.get(0).getParameterValue().getEngValue().getFloatValue(), 1e-6);
    }
    
    @Test
    public void testMarkedNotUpdated() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoUpdatedOut").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoUnupdatedOut").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        int pIntegerPara16_1 = 5;
        tmGenerator.generate_PKT16(pIntegerPara16_1, 0);
        assertEquals(1, params.size());
        assertEquals("/REFMDB/SUBSYS1/AlgoUpdatedOut", params.get(0).getParameterValue().getParameter().getQualifiedName());
        assertEquals(pIntegerPara16_1, params.get(0).getParameterValue().getEngValue().getUint32Value());
    }
    
    
    @Test
    public void testSelectiveRun() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoSelectiveOut").build()), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                        params.addAll(items);
                    }
        });

        c.start();
        int pIntegerPara16_1 = 5;
        tmGenerator.generate_PKT16(pIntegerPara16_1, 0);
        assertEquals(1, params.size());
        assertEquals("/REFMDB/SUBSYS1/AlgoSelectiveOut", params.get(0).getParameterValue().getParameter().getQualifiedName());
        assertEquals(pIntegerPara16_1, params.get(0).getParameterValue().getEngValue().getFloatValue(), 1e-6);
        
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size()); // No change, not in OnParameterUpdate list
        
        pIntegerPara16_1 = 7;
        tmGenerator.generate_PKT16(pIntegerPara16_1, 0);
        assertEquals(2, params.size()); // Now change, also with updated float from PKT11
        assertEquals(pIntegerPara16_1+tmGenerator.pFloatPara11_3, params.get(1).getParameterValue().getEngValue().getFloatValue(), 1e-6);
    }
}

