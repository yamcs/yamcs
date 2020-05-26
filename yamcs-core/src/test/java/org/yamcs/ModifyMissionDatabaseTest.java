package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
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
import org.yamcs.protobuf.Mdb.UpdateAlgorithmRequest;
import org.yamcs.protobuf.Mdb.UpdateParameterRequest;
import org.yamcs.protobuf.Mdb.UpdateParameterRequest.ActionType;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

import io.netty.handler.codec.http.HttpMethod;

public class ModifyMissionDatabaseTest extends AbstractIntegrationTest {

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
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(packetGenerator.pFloatPara1_1_2 * 0.0001672918,
                values.get(0).getEngValue().getFloatValue(), 1e-5);

        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder()
                .setAction(ActionType.SET_DEFAULT_CALIBRATOR)
                .setDefaultCalibrator(CalibratorInfo.newBuilder()
                        .setType(CalibratorInfo.Type.POLYNOMIAL)
                        .setPolynomialCalibrator(
                                PolynomialCalibratorInfo.newBuilder().addCoefficient(1).addCoefficient(2)))
                .build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_1_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(1 + packetGenerator.pFloatPara1_1_2 * 2, values.get(0).getEngValue().getFloatValue(), 1e-5);

        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_1_2",
                        HttpMethod.PATCH, cpr)
                .get();

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
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_10(5, 0, 30);
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(3, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // Remove the context calibrators
        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_CALIBRATORS).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/FloatPara1_10_3",
                        HttpMethod.PATCH, cpr)
                .get();

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
        cpr = UpdateParameterRequest.newBuilder()
                .setAction(ActionType.SET_CALIBRATORS)
                .addContextCalibrator(cci)
                .build();

        restClient.doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_10_3",
                HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(10, 0, 40);
        values = captor.expectTimely();
        assertEquals(8, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // remove all overrides
        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_10_3",
                        HttpMethod.PATCH, cpr)
                .get();

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
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_10(0, 3, 0);
        List<ParameterValue> values = captor.expectTimely();

        assertEquals(MonitoringResult.WARNING, values.get(0).getMonitoringResult());

        EnumerationAlarm ea = EnumerationAlarm.newBuilder().setLevel(AlarmLevelType.CRITICAL).setLabel("three_ok")
                .build();
        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_DEFAULT_ALARMS)
                .setDefaultAlarm(AlarmInfo.newBuilder().addEnumerationAlarm(ea).build()).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/EnumerationPara1_10_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.CRITICAL, values.get(0).getMonitoringResult());

        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/EnumerationPara1_10_2",
                        HttpMethod.PATCH, cpr)
                .get();

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
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_10(80, 3, 0);
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(MonitoringResult.SEVERE, values.get(0).getMonitoringResult());

        // add a context calibrator for EnumerationPara1_10_2=3
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

        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder()
                .setAction(ActionType.SET_ALARMS)
                .addContextAlarm(cai).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/IntegerPara1_10_1",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.DISTRESS, values.get(0).getMonitoringResult());

        // set the context using a string rather than comparison
        ai = AlarmInfo.newBuilder().addStaticAlarmRange(
                AlarmRange.newBuilder().setLevel(AlarmLevelType.SEVERE).setMaxExclusive(10).build()).build();
        cai = ContextAlarmInfo.newBuilder().setContext("EnumerationPara1_10_2==five_yes").setAlarm(ai).build();

        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_ALARMS).addContextAlarm(cai).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/IntegerPara1_10_1",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(11, 5, 0);
        values = captor.expectTimely();
        assertEquals(MonitoringResult.SEVERE, values.get(0).getMonitoringResult());

        // reset to the original MDB value
        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).addContextAlarm(cai).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/IntegerPara1_10_1",
                        HttpMethod.PATCH, cpr)
                .get();

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
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/AlgoFloatAddition"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        assertEquals(2.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // change the algorithm
        AlgorithmInfo ai = AlgorithmInfo.newBuilder()
                .setText("AlgoFloatAddition.value = 10 + f0.value + f1.value")
                .build();

        UpdateAlgorithmRequest car = UpdateAlgorithmRequest.newBuilder()
                .setAction(UpdateAlgorithmRequest.ActionType.SET).setAlgorithm(ai).build();
        restClient.doRequest("/mdb/IntegrationTest/realtime/algorithms/REFMDB/SUBSYS1/float_add",
                HttpMethod.PATCH, car).get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(12.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);

        // reset back to MDB version
        car = UpdateAlgorithmRequest.newBuilder().setAction(UpdateAlgorithmRequest.ActionType.RESET).build();
        restClient.doRequest("/mdb/IntegrationTest/realtime/algorithms/REFMDB/SUBSYS1/float_add",
                HttpMethod.PATCH, car).get();

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        assertEquals(2.16729187, values.get(0).getEngValue().getFloatValue(), 1e-5);
    }
}
