package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo.OperatorType;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.EnumerationAlarm;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.PolynomialCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo.SplinePointInfo;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class ModifyMissionDatabaseTest extends AbstractIntegrationTest {

    private ProcessorClient processorClient;

    @BeforeEach
    public void prepare() {
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
    }

    @Test
    public void testModifyParameterCalibration() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_1_2"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(packetGenerator.pFloatPara1_1_2 * 0.0001672918,
                values.get(0).getEngValue().getFloatValue(), 1e-5);

        CalibratorInfo calibrator = CalibratorInfo.newBuilder()
                .setType(CalibratorInfo.Type.POLYNOMIAL)
                .setPolynomialCalibrator(PolynomialCalibratorInfo.newBuilder()
                        .addCoefficient(1)
                        .addCoefficient(2))
                .build();
        processorClient.setDefaultCalibrator("/REFMDB/SUBSYS1/FloatPara1_1_2", calibrator).get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(1 + packetGenerator.pFloatPara1_1_2 * 2, values.get(0).getEngValue().getFloatValue(), 1e-5);

        processorClient.revertCalibrators("/REFMDB/SUBSYS1/FloatPara1_1_2").get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(packetGenerator.pFloatPara1_1_2 * 0.0001672918, values.get(0).getEngValue().getFloatValue(), 1e-5);
        captor.assertSilence();
    }

    @Test
    public void testModifyParameterContextCalibration() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FloatPara1_10_3"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        packetGenerator.generate_PKT1_10(5, 0, 30);
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(3, values.get(0).getEngValue().getFloatValue(), 1e-5);

        processorClient.removeCalibrators("/REFMDB/SUBSYS1/FloatPara1_10_3").get();

        packetGenerator.generate_PKT1_10(5, 0, 30);
        values = captor.expectTimely();
        assertEquals(30, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // Set a context calibrator based on IntegerPara1_10_1

        ComparisonInfo cinfo = ComparisonInfo.newBuilder()
                .setParameter(ParameterInfo.newBuilder().setQualifiedName("/REFMDB/SUBSYS1/IntegerPara1_10_1"))
                .setOperator(OperatorType.EQUAL_TO)
                .setValue("10")
                .build();
        SplineCalibratorInfo spi = SplineCalibratorInfo.newBuilder()
                .addPoint(SplinePointInfo.newBuilder().setRaw(30).setCalibrated(6))
                .addPoint(SplinePointInfo.newBuilder().setRaw(60).setCalibrated(12))
                .build();

        ContextCalibratorInfo cci = ContextCalibratorInfo.newBuilder()
                .addComparison(cinfo)
                .setCalibrator(CalibratorInfo.newBuilder()
                        .setType(CalibratorInfo.Type.SPLINE)
                        .setSplineCalibrator(spi))
                .build();

        processorClient.setCalibrators("/REFMDB/SUBSYS1/FloatPara1_10_3", null, Arrays.asList(cci)).get();

        packetGenerator.generate_PKT1_10(10, 0, 40);
        values = captor.expectTimely();
        assertEquals(8, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // Remove all overrides
        processorClient.revertCalibrators("/REFMDB/SUBSYS1/FloatPara1_10_3").get();

        packetGenerator.generate_PKT1_10(0, 0, 30);
        values = captor.expectTimely();
        assertEquals(3, values.get(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testModifyParameterAlarm() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/EnumerationPara1_10_2"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        List<ParameterValue> values = captor.expectTimely();

        assertEquals(MonitoringResult.WARNING, values.get(0).getMonitoringResult());

        AlarmInfo alarm = AlarmInfo.newBuilder()
                .addEnumerationAlarm(EnumerationAlarm.newBuilder()
                        .setLevel(AlarmLevelType.CRITICAL)
                        .setLabel("three_ok"))
                .build();
        processorClient.setDefaultAlarm("/REFMDB/SUBSYS1/EnumerationPara1_10_2", alarm).get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.CRITICAL, values.get(0).getMonitoringResult());

        processorClient.revertAlarms("/REFMDB/SUBSYS1/EnumerationPara1_10_2").get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.WARNING, values.get(0).getMonitoringResult());
    }

    @Test
    public void testModifyParameterContextAlarm() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_10_1"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(MonitoringResult.SEVERE, values.get(0).getMonitoringResult());

        // add a context alarm for EnumerationPara1_10_2=3
        ComparisonInfo cinfo = ComparisonInfo.newBuilder()
                .setParameter(ParameterInfo.newBuilder()
                        .setQualifiedName("/REFMDB/SUBSYS1/EnumerationPara1_10_2"))
                .setOperator(OperatorType.EQUAL_TO)
                .setValue("three_ok")
                .build();
        AlarmInfo ai = AlarmInfo.newBuilder().addStaticAlarmRange(AlarmRange.newBuilder()
                .setLevel(AlarmLevelType.DISTRESS)
                .setMaxExclusive(70))
                .build();
        ContextAlarmInfo cai = ContextAlarmInfo.newBuilder().addComparison(cinfo).setAlarm(ai).build();

        processorClient.setAlarms("/REFMDB/SUBSYS1/IntegerPara1_10_1", null, Arrays.asList(cai)).get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.DISTRESS, values.get(0).getMonitoringResult());

        // set the context using a string rather than comparison
        ai = AlarmInfo.newBuilder().addStaticAlarmRange(
                AlarmRange.newBuilder().setLevel(AlarmLevelType.SEVERE).setMaxExclusive(10).build()).build();
        cai = ContextAlarmInfo.newBuilder().setContext("EnumerationPara1_10_2==five_yes").setAlarm(ai).build();

        processorClient.setAlarms("/REFMDB/SUBSYS1/IntegerPara1_10_1", null, Arrays.asList(cai)).get();

        packetGenerator.generate_PKT1_10(11, 5, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.SEVERE, values.get(0).getMonitoringResult());

        // reset to the original MDB value
        processorClient.revertAlarms("/REFMDB/SUBSYS1/IntegerPara1_10_1").get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.SEVERE, values.get(0).getMonitoringResult());
    }

    @Test
    public void testModifyAlgorithm() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAdditionJs"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(2.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // change the algorithm
        String text = "AlgoFloatAdditionJs.value = 10 + f0.value + f1.value";
        processorClient.updateAlgorithm("/REFMDB/SUBSYS1/float_add", text).get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(12.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // reset back to MDB version
        processorClient.revertAlgorithm("/REFMDB/SUBSYS1/float_add").get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(2.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);
    }
}
