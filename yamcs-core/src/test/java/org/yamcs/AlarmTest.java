package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class AlarmTest {
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
    private AlarmReporter alarmReporter;
    
    @Before
    public void beforeEachTest() throws ConfigurationException, ProcessorException {
        String yamcsInstance="refmdb";
        EventProducerFactory.setMockup(true);
        q=EventProducerFactory.getMockupQueue();
        db=XtceDbFactory.getInstance(yamcsInstance);
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator=new RefMdbPacketGenerator();
        SimpleTcTmService tctms = new SimpleTcTmService(tmGenerator, null, null);
        try {
            c=ProcessorFactory.create(yamcsInstance, "AlarmTest", "refmdb", tctms, "test");
        } catch (Exception e) {
            e.printStackTrace();
        }
        prm=c.getParameterRequestManager();
        alarmReporter = new AlarmReporter(yamcsInstance, "AlarmTest");
    }
    
    @After
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        c.quit();
    }
    
    @Test
    public void testIntegerLimits() throws InvalidIdentification {
        Parameter p = db.getParameter("/REFMDB/SUBSYS1/IntegerPara1_10_1");
        
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        prm.addRequest(p,
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, List<ParameterValue> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        alarmReporter.startAsync();
        
        tmGenerator.generate_PKT1_10(30, 7, 0);
        
        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, params.get(0).watchRange.getMinInclusive(), 1e-17);
        assertEquals(30, params.get(0).watchRange.getMaxInclusive(), 1e-17);
        assertEquals(-22, params.get(0).warningRange.getMinInclusive(), 1e-17);
        assertEquals(40, params.get(0).warningRange.getMaxInclusive(), 1e-17);
        assertEquals(-33, params.get(0).distressRange.getMinInclusive(), 1e-17);
        assertEquals(50, params.get(0).distressRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).criticalRange.getMinInclusive(), 1e-17);
        assertEquals(60, params.get(0).criticalRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).severeRange.getMinInclusive(), 1e-17);
        assertEquals(70, params.get(0).severeRange.getMaxInclusive(), 1e-17);
        
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(42, 7, 0);
        assertEquals(MonitoringResult.WARNING, params.get(1).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(1).getRangeCondition());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(52, 7, 0);
        assertEquals(MonitoringResult.DISTRESS, params.get(2).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(2).getRangeCondition());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(62, 7, 0);
        assertEquals(MonitoringResult.CRITICAL, params.get(3).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(3).getRangeCondition());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(72, 7, 0);
        assertEquals(MonitoringResult.SEVERE, params.get(4).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(4).getRangeCondition());
        assertEquals(4, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(74, 7, 0);
        assertEquals(MonitoringResult.SEVERE, params.get(5).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(5).getRangeCondition());
        assertEquals(4 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(15, 7, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(6).getMonitoringResult());
        assertEquals(5, q.size()); // Message for back to normal
        
        // Now, change context
        tmGenerator.generate_PKT1_10(71, 0 /* ! */, 0);
        assertEquals(MonitoringResult.CRITICAL, params.get(7).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(7).getRangeCondition());
        assertEquals(6, q.size()); // Message for changed MonitoringResult
        
        
        // Test minViolations of 3 under context 6
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING, params.get(8).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(8).getRangeCondition());
        
        assertEquals(6, q.size()); // No message, violations=1
        
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING, params.get(9).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(9).getRangeCondition());
        assertEquals(6, q.size()); // No message, violations=2
        
        tmGenerator.generate_PKT1_10(40, 6, 0);
        assertEquals(MonitoringResult.WARNING, params.get(10).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(10).getRangeCondition());
        assertEquals(7, q.size()); // Message because violations=3
    }
    
    @Test
    public void testFloatLimits() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_10_3").build());
        prm.addRequest(p, 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, List<ParameterValue> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        alarmReporter.startAsync();
        
        tmGenerator.generate_PKT1_10(0, 1, 30);
        
        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, params.get(0).watchRange.getMinInclusive(), 1e-17);
        assertEquals(30, params.get(0).watchRange.getMaxInclusive(), 1e-17);
        assertEquals(-22, params.get(0).warningRange.getMinInclusive(), 1e-17);
        assertEquals(40, params.get(0).warningRange.getMaxInclusive(), 1e-17);
        assertEquals(-33, params.get(0).distressRange.getMinInclusive(), 1e-17);
        assertEquals(50, params.get(0).distressRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).criticalRange.getMinInclusive(), 1e-17);
        assertEquals(60, params.get(0).criticalRange.getMaxInclusive(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).severeRange.getMinInclusive(), 1e-17);
        assertEquals(70, params.get(0).severeRange.getMaxInclusive(), 1e-17);
        
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(0, 1, 42);
        assertEquals(MonitoringResult.WARNING, params.get(1).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(1).getRangeCondition());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 52);
        assertEquals(MonitoringResult.DISTRESS, params.get(2).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(2).getRangeCondition());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 62);
        assertEquals(MonitoringResult.CRITICAL, params.get(3).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(3).getRangeCondition());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 72);
        assertEquals(MonitoringResult.SEVERE, params.get(4).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(4).getRangeCondition());
        assertEquals(4, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 1, 74);
        assertEquals(MonitoringResult.SEVERE, params.get(5).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(5).getRangeCondition());
        assertEquals(4 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(0, 1, 15);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(6).getMonitoringResult());
        assertEquals(5, q.size()); // Message for back to normal
        

        // Now, change context
        tmGenerator.generate_PKT1_10(0, 0 /* ! */, 71);
        
        assertEquals(MonitoringResult.CRITICAL, params.get(7).getMonitoringResult());
        assertEquals(RangeCondition.HIGH, params.get(7).getRangeCondition());
        assertEquals(6, q.size()); // Message for changed MonitoringResult
    }

    @Test
    public void testEnumerationAlarms() throws InvalidIdentification {
        final ArrayList<ParameterValue> params=new ArrayList<ParameterValue>();
        Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/EnumerationPara1_10_2").build());
        prm.addRequest(p, 
                new ParameterConsumer() {
                    @Override
                    public void updateItems(int subscriptionId, List<ParameterValue> items) {
                        params.addAll(items);
                    }
                });
        c.start();
        alarmReporter.startAsync();
        
        tmGenerator.generate_PKT1_10(0, 1, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(0).getMonitoringResult());
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(0, 2, 0);
        assertEquals(MonitoringResult.WATCH, params.get(1).getMonitoringResult());
        assertEquals(1, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 3, 0);
        assertEquals(MonitoringResult.WARNING, params.get(2).getMonitoringResult());
        assertEquals(2, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 4, 0);
        assertEquals(MonitoringResult.WARNING, params.get(3).getMonitoringResult());
        assertEquals(2 /* ! */, q.size()); // No message, since nothing changed
        
        tmGenerator.generate_PKT1_10(0, 5, 0);
        assertEquals(MonitoringResult.CRITICAL, params.get(4).getMonitoringResult());
        assertEquals(3, q.size()); // Message for changed MonitoringResult
        
        tmGenerator.generate_PKT1_10(0, 0, 0);
        assertEquals(MonitoringResult.IN_LIMITS, params.get(5).getMonitoringResult());
        assertEquals(4, q.size()); // Message for back to normal
    }
    
    @Test
    public void testAlarmReportingWithoutSubscription() {
        c.start();
        alarmReporter.startAsync();
        
        tmGenerator.generate_PKT1_10(30, 1, 0);
        assertEquals(0, q.size());
        
        tmGenerator.generate_PKT1_10(42, 1, 0);
        assertEquals(1, q.size()); // Message for changed MonitoringResult
    }
    
    @Test
    public void testOnValueChangeReport() {
        c.start();
        alarmReporter.startAsync();
        
        tmGenerator.generate_PKT1_10(20, 1, 0);
        assertEquals(0, q.size());

        tmGenerator.generate_PKT1_10(20, 1, 0);
        assertEquals(0, q.size()); // No change
        
        tmGenerator.generate_PKT1_10(21, 1, 0);
        assertEquals(1, q.size()); // Change
        
        tmGenerator.generate_PKT1_10(21, 1, 0);
        assertEquals(1, q.size()); // No Change
        
        tmGenerator.generate_PKT1_10(20, 1, 0);
        assertEquals(2, q.size()); // Change
    }
}
