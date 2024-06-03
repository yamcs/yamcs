package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.alarms.AlarmReporter;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.protobuf.Db.Event;

public class AlarmTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
    }

    private Mdb db;
    private Processor processor;
    private RefMdbPacketGenerator tmGenerator;
    private ParameterRequestManager prm;
    private Queue<Event> q;
    private AlarmReporter alarmReporter;

    @BeforeEach
    public void beforeEachTest() throws ConfigurationException, ProcessorException {
        String yamcsInstance = "refmdb";
        EventProducerFactory.setMockup(true);
        q = EventProducerFactory.getMockupQueue();
        db = MdbFactory.getInstance(yamcsInstance);
        assertNotNull(db.getParameter("/REFMDB/SUBSYS1/FloatPara1_1_2"));

        tmGenerator = new RefMdbPacketGenerator();
        try {
            processor = ProcessorFactory.create(yamcsInstance, "AlarmTest", tmGenerator);
        } catch (Exception e) {
            e.printStackTrace();
        }
        prm = processor.getParameterRequestManager();

        Map<String, Object> config = new HashMap<>();
        config.put("processor", "AlarmTest");
        alarmReporter = new AlarmReporter();
        alarmReporter.init(processor, YConfiguration.wrap(config), null);
    }

    @AfterEach
    public void afterEachTest() { // Prevents us from wrapping our code in try-finally
        processor.quit();
    }

    @Test
    public void testIntegerLimits() throws InvalidIdentification {
        Parameter p = db.getParameter("/REFMDB/SUBSYS1/IntegerPara1_10_1");

        final ArrayList<ParameterValue> params = new ArrayList<>();
        prm.addRequest(p,
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        processor.start();
        alarmReporter.startAsync();

        tmGenerator.generate_PKT1_10(30, 7, 0);

        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, params.get(0).getWatchRange().getMin(), 1e-17);
        assertEquals(30, params.get(0).getWatchRange().getMax(), 1e-17);
        assertTrue(params.get(0).getWatchRange().isMaxInclusive());
        assertEquals(-22, params.get(0).getWarningRange().getMin(), 1e-17);
        assertEquals(40, params.get(0).getWarningRange().getMax(), 1e-17);
        assertEquals(-33, params.get(0).getDistressRange().getMin(), 1e-17);
        assertEquals(50, params.get(0).getDistressRange().getMax(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getCriticalRange().getMin(), 1e-17);
        assertEquals(60, params.get(0).getCriticalRange().getMax(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, params.get(0).getSevereRange().getMin(), 1e-17);
        assertEquals(70, params.get(0).getSevereRange().getMax(), 1e-17);

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
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm.getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_10_3").build());
        prm.addRequest(p,
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        processor.start();
        alarmReporter.startAsync();

        tmGenerator.generate_PKT1_10(0, 1, 30);
        ParameterValue pv = params.get(0);

        // Check whether spreadsheet loads all levels ok
        assertEquals(-11, pv.getWatchRange().getMin(), 1e-17);
        assertEquals(30, pv.getWatchRange().getMax(), 1e-17);
        assertEquals(-22, pv.getWarningRange().getMin(), 1e-17);
        assertEquals(40, pv.getWarningRange().getMax(), 1e-17);
        assertEquals(-33, pv.getDistressRange().getMin(), 1e-17);
        assertEquals(50, pv.getDistressRange().getMax(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, pv.getCriticalRange().getMin(), 1e-17);
        assertEquals(60, pv.getCriticalRange().getMax(), 1e-17);
        assertEquals(Double.NEGATIVE_INFINITY, pv.getSevereRange().getMin(), 1e-17);
        assertEquals(70, pv.getSevereRange().getMax(), 1e-17);

        assertEquals(MonitoringResult.IN_LIMITS, pv.getMonitoringResult());
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
        final ArrayList<ParameterValue> params = new ArrayList<>();
        Parameter p = prm
                .getParameter(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/EnumerationPara1_10_2").build());
        prm.addRequest(p,
                (ParameterConsumer) (subscriptionId, items) -> params.addAll(items));
        processor.start();
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
        processor.start();
        alarmReporter.startAsync();

        tmGenerator.generate_PKT1_10(30, 1, 0);
        assertEquals(0, q.size());

        tmGenerator.generate_PKT1_10(42, 1, 0);
        assertEquals(1, q.size()); // Message for changed MonitoringResult
    }

    @Test
    public void testOnValueChangeReport() {
        processor.start();
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
