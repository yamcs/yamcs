package org.yamcs.algorithms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValueWithId;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.DummyPpProvider;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

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
            c=ChannelFactory.create("refmdb", "AlgorithmManagerTest", "refmdb", "refmdb", new MyTcTmService(tmGenerator), "refmdb", null);
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
        NamedObjectId floatAddition=NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatAddition").build();

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
                assertEquals(2.1672918, pvwi.getParameterValue().getEngValue().getDoubleValue(), 0.001);
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
        paraList.add(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/YprFloat").build());
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
    public void testSlidingWindow() throws InvalidIdentification {
        List<NamedObjectId> subList = Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/WindowResult").build());
        final List<ParameterValueWithId> params = new ArrayList<ParameterValueWithId>();
        prm.addRequest(subList, new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT16(1, 2);
        assertEquals(0, params.size()); // Windows:  [*  *  *  1]  &&  [*  2]
        
        tmGenerator.generate_PKT16(2, 4);
        assertEquals(0, params.size()); // Windows:  [*  *  1  2]  &&  [2  4]
        
        tmGenerator.generate_PKT16(3, 6);
        assertEquals(0, params.size()); // Windows:  [*  1  2  3]  &&  [4  6]
        
        // Production starts only when all relevant values for the expression are present
        tmGenerator.generate_PKT16(5, 8);
        assertEquals(1, params.size()); // Windows:  [1  2  3  5]  &&  [6  8] => produce (1 + 5) * 6
        assertEquals(36, params.get(0).getParameterValue().getEngValue().getUint32Value());
        
        params.clear();
        tmGenerator.generate_PKT16(8, 10);
        assertEquals(1, params.size()); // Windows:  [2  3  5  8]  &&  [8 10] => produce (2 + 8) * 8
        assertEquals(80, params.get(0).getParameterValue().getEngValue().getUint32Value());
    }
    
    @Test
    public void testEvents() throws Exception {
        // No need to subscribe. This algorithm doesn't have any outputs
        // and is auto-activated for realtime

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
        prm.addRequest(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/Division").build()), new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId,   ArrayList<ParameterValueWithId> items) {
                params.addAll(items);
            }
        });

        c.start();
        tmGenerator.generate_PKT11();
        assertEquals(1, params.size());
        assertEquals(tmGenerator.pIntegerPara11_1, params.get(0).getParameterValue().getEngValue().getDoubleValue()*3, 0.001);
    }

    static class MyTcTmService extends AbstractService implements TcTmService {
        TmPacketProvider tm;
        ParameterProvider pp;

        public MyTcTmService(RefMdbPacketGenerator tmGenerator) throws ConfigurationException {
            this.tm=tmGenerator;
            pp=new DummyPpProvider("refmdb", "refmdb");
        }

        @Override
        public TmPacketProvider getTmPacketProvider() {
            return tm;
        }

        @Override
        public TcUplinker getTcUplinker() {
            return null;
        }

        @Override
        public ParameterProvider getParameterProvider() {
            return pp;
        }

        @Override
        protected void doStart() {
            tm.start();
            notifyStarted();
        }

        @Override
        protected void doStop() {
            tm.stop();
            notifyStopped();
        }
    }
}
