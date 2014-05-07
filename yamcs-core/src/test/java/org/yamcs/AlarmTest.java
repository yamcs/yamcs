package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlarmTest {
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
        String yamcsInstance="refmdb";
        EventProducerFactory.setMockup(true);
        q=EventProducerFactory.getMockupQueue();
        db=XtceDbFactory.getInstance(yamcsInstance);
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara11_2"));

        tmGenerator=new RefMdbPacketGenerator();
        try {
            c=ChannelFactory.create(yamcsInstance, "AlarmTest", "refmdb", "refmdb", new RefMdbTmService(tmGenerator), "refmdb", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        prm=c.getParameterRequestManager();
        new AlarmReporter(yamcsInstance, "AlarmTest");
    }
    
    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        c.quit();
    }
    
    @Test
    public void testIntegerLimits() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_10_1").build()), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        
        tmGenerator.generate_PKT1_10(30, 1, 0);
        
        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, params.get(0).getParameterValue().watchRange.getMinInclusive(), 1e-17);
        assertEquals(30, params.get(0).getParameterValue().watchRange.getMaxInclusive(), 1e-17);
        assertEquals(-22, params.get(0).getParameterValue().warningRange.getMinInclusive(), 1e-17);
        assertEquals(40, params.get(0).getParameterValue().warningRange.getMaxInclusive(), 1e-17);
        assertEquals(-33, params.get(0).getParameterValue().distressRange.getMinInclusive(), 1e-17);
        assertEquals(50, params.get(0).getParameterValue().distressRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getParameterValue().criticalRange.getMinInclusive(), 1e-17);
        assertEquals(60, params.get(0).getParameterValue().criticalRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getParameterValue().severeRange.getMinInclusive(), 1e-17);
        assertEquals(70, params.get(0).getParameterValue().severeRange.getMaxInclusive(), 1e-17);
        
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getParameterValue().getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(42, 1, 0);
        assertEquals(MonitoringResult.WARNING_HIGH, params.get(1).getParameterValue().getMonitoringResult());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(52, 1, 0);
        assertEquals(MonitoringResult.DISTRESS_HIGH, params.get(2).getParameterValue().getMonitoringResult());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(62, 1, 0);
        assertEquals(MonitoringResult.CRITICAL_HIGH, params.get(3).getParameterValue().getMonitoringResult());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(72, 1, 0);
        assertEquals(MonitoringResult.SEVERE_HIGH, params.get(4).getParameterValue().getMonitoringResult());
        assertEquals(4, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(74, 1, 0);
        assertEquals(MonitoringResult.SEVERE_HIGH, params.get(5).getParameterValue().getMonitoringResult());
        assertEquals(4 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(15, 1, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(6).getParameterValue().getMonitoringResult());
        assertEquals(5, q.size()); // Message for back to normal
        
        // Now, change context
        tmGenerator.generate_PKT1_10(71, 0 /* ! */, 0);
        assertEquals(MonitoringResult.CRITICAL_HIGH, params.get(7).getParameterValue().getMonitoringResult());
        assertEquals(6, q.size()); // Message for changed MonitoringResult
        
        // Test minViolations of 3 under context 6
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING_HIGH, params.get(8).getParameterValue().getMonitoringResult());
        assertEquals(6, q.size()); // No message, violations=1
        
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING_HIGH, params.get(9).getParameterValue().getMonitoringResult());
        assertEquals(6, q.size()); // No message, violations=2
        
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING_HIGH, params.get(10).getParameterValue().getMonitoringResult());
        assertEquals(7, q.size()); // Message because violations=3
    }
    
    @Test
    public void testFloatLimits() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_10_3").build()), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        
        tmGenerator.generate_PKT1_10(0, 1, 30);
        
        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, params.get(0).getParameterValue().watchRange.getMinInclusive(), 1e-17);
        assertEquals(30, params.get(0).getParameterValue().watchRange.getMaxInclusive(), 1e-17);
        assertEquals(-22, params.get(0).getParameterValue().warningRange.getMinInclusive(), 1e-17);
        assertEquals(40, params.get(0).getParameterValue().warningRange.getMaxInclusive(), 1e-17);
        assertEquals(-33, params.get(0).getParameterValue().distressRange.getMinInclusive(), 1e-17);
        assertEquals(50, params.get(0).getParameterValue().distressRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getParameterValue().criticalRange.getMinInclusive(), 1e-17);
        assertEquals(60, params.get(0).getParameterValue().criticalRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getParameterValue().severeRange.getMinInclusive(), 1e-17);
        assertEquals(70, params.get(0).getParameterValue().severeRange.getMaxInclusive(), 1e-17);
        
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getParameterValue().getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(0, 1, 42);
        assertEquals(MonitoringResult.WARNING_HIGH, params.get(1).getParameterValue().getMonitoringResult());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 52);
        assertEquals(MonitoringResult.DISTRESS_HIGH, params.get(2).getParameterValue().getMonitoringResult());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 62);
        assertEquals(MonitoringResult.CRITICAL_HIGH, params.get(3).getParameterValue().getMonitoringResult());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 72);
        assertEquals(MonitoringResult.SEVERE_HIGH, params.get(4).getParameterValue().getMonitoringResult());
        assertEquals(4, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 74);
        assertEquals(MonitoringResult.SEVERE_HIGH, params.get(5).getParameterValue().getMonitoringResult());
        assertEquals(4 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(0, 1, 15);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(6).getParameterValue().getMonitoringResult());
        assertEquals(5, q.size()); // Message for back to normal
        
        // Now, change context
        tmGenerator.generate_PKT1_10(0, 0 /* ! */, 71);
        assertEquals(MonitoringResult.CRITICAL_HIGH, params.get(7).getParameterValue().getMonitoringResult());
        assertEquals(6, q.size()); // Message for changed MonitoringResult
    }

    @Test
    public void testEnumerationAlarms() throws InvalidIdentification {
        final ArrayList<ParameterValueWithId> params=new ArrayList<ParameterValueWithId>();
        prm.addRequest(Arrays.asList(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/EnumerationPara1_10_2").build()), 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        
        tmGenerator.generate_PKT1_10(0, 1, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getParameterValue().getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(0, 2, 0);
        assertEquals(MonitoringResult.WATCH, params.get(1).getParameterValue().getMonitoringResult());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 3, 0);
        assertEquals(MonitoringResult.WARNING, params.get(2).getParameterValue().getMonitoringResult());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 4, 0);
        assertEquals(MonitoringResult.WARNING, params.get(3).getParameterValue().getMonitoringResult());
        assertEquals(2 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(0, 5, 0);
        assertEquals(MonitoringResult.CRITICAL, params.get(4).getParameterValue().getMonitoringResult());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 0, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(5).getParameterValue().getMonitoringResult());
        assertEquals(4, q.size()); // Message for back to normal
    }
    
    @Test
    public void testAlarmReportingWithoutSubscription() {
        c.start();
        
        tmGenerator.generate_PKT1_10(30, 1, 0);
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(42, 1, 0);
        assertEquals(1, q.size()); // Message for changed MonitoringResult
    }
}
