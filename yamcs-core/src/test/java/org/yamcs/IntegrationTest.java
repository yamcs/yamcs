package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.HttpClient;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.http.HttpServer;
import org.yamcs.http.StaticFileHandler;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSubscriptionRequest;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.BatchSetParameterValuesRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.CommandOptions;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.EventAlarmData;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.ListServicesResponse;
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
import org.yamcs.protobuf.ParameterSubscriptionRequest;
import org.yamcs.protobuf.ParameterSubscriptionResponse;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.SubscribedParameter;
import org.yamcs.protobuf.TimeInfo;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.AggregateValue;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

public class IntegrationTest extends AbstractIntegrationTest {

    @Ignore
    @Test
    public void testWsParameterSubscriPerformance() throws Exception {
        // subscribe to parameters
        long t0 = System.currentTimeMillis();
        ParameterSubscriptionRequest invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        wsClient.sendRequest(wsr);

        for (int i = 0; i < 1000000; i++) {
            packetGenerator.generate_PKT1_1();
        }
        System.out.println("total time: " + (System.currentTimeMillis() - t0));
    }

    @Test
    public void testWsParameter() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest invalidSubscrList = getSubscription(true, false, false,
                "/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/InvalidParaName");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", invalidSubscrList);
        CompletableFuture<WebSocketReplyData> cf = wsClient.sendRequest(wsr);

        WebSocketReplyData wsrd = cf.get();
        assertTrue(wsrd.hasData());
        assertEquals(ParameterSubscriptionResponse.class.getSimpleName(), wsrd.getType());
        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        assertEquals(1, psr.getInvalidCount());
        assertEquals("/REFMDB/SUBSYS1/InvalidParaName", psr.getInvalid(0).getName());

        // generate some TM packets and monitor realtime reception
        for (int i = 0; i < 10; i++) {
            packetGenerator.generate_PKT1_1();
        }
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);
        checkPvals(pdata.getParameterList(), packetGenerator);

        ParameterSubscriptionRequest subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr);

        // we subscribe again and should get the previous values from the cache
        wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr);
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPvals(pdata.getParameterList(), packetGenerator);
    }

    @Test
    public void testWsParameterWithNumericId() throws Exception {
        ParameterSubscriptionRequest subscrList = getSubscription(true, false, true, "/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        WebSocketReplyData wsrd = wsClient.sendRequest(wsr).get();
        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        assertEquals(2, psr.getSubscribedCount());

        packetGenerator.generate_PKT1_1();

        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        checkPvals(psr.getSubscribedList(), 2, pdata.getParameterList(), packetGenerator);
    }

    @Test
    public void testWsParameterAggrArrayMember() throws Exception {
        // note that the array has only 150 elements (0-149), the [150] is subscribed but never received
        ParameterSubscriptionRequest subscrList = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/aggregate_para1.member2",
                "/REFMDB/SUBSYS1/array_para1[3].member3", "/REFMDB/SUBSYS1/array_para1[150].member3");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT7();
        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);

        assertEquals(1, pdata.getParameterCount());

        ParameterValue pv = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member2", pv.getId().getName());
        assertEquals(30, pv.getEngValue().getUint32Value());

        packetGenerator.generate_PKT8();
        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertEquals(1, pdata.getParameterCount());

        pv = pdata.getParameter(0);
        assertEquals("/REFMDB/SUBSYS1/array_para1[3].member3", pv.getId().getName());
        assertEquals(1.5, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testWsParameterAggrArrayInvalidMember() throws Exception {
        ParameterSubscriptionRequest subscrList = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/aggregate_para1.invalid_member");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subscrList);
        WebSocketReplyData wsrd = wsClient.sendRequest(wsr).get();
        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        assertEquals(1, psr.getInvalidCount());
    }

    @Test
    public void testOnlineParameterCalibrationChange() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest subcr = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/FloatPara1_1_2");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subcr);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);
        assertEquals(packetGenerator.pFloatPara1_1_2 * 0.0001672918,
                pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_DEFAULT_CALIBRATOR)
                .setDefaultCalibrator(CalibratorInfo.newBuilder().setType(CalibratorInfo.Type.POLYNOMIAL)
                        .setPolynomialCalibrator(
                                PolynomialCalibratorInfo.newBuilder().addCoefficient(1).addCoefficient(2).build())
                        .build())
                .build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_1_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_1();
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(1 + packetGenerator.pFloatPara1_1_2 * 2, pdata.getParameter(0).getEngValue().getFloatValue(),
                1e-5);

        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_1_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_1();
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(packetGenerator.pFloatPara1_1_2 * 0.0001672918,
                pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testOnlineParameterContextCalibrationChange() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest subcr = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/FloatPara1_10_3");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subcr);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT1_10(5, 0, 30);
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(3, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        // this will remove the context calibrators
        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_CALIBRATORS).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/FloatPara1_10_3",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(5, 0, 30);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(30, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        // set a context calibrator based on the IntegerPara1_10_1

        ComparisonInfo cinfo = ComparisonInfo.newBuilder()
                .setParameter(ParameterInfo.newBuilder().setQualifiedName("/REFMDB/SUBSYS1/IntegerPara1_10_1").build())
                .setOperator(OperatorType.EQUAL_TO)
                .setValue("10")
                .build();
        SplineCalibratorInfo spi = SplineCalibratorInfo.newBuilder()
                .addPoint(SplinePointInfo.newBuilder().setRaw(30).setCalibrated(6).build())
                .addPoint(SplinePointInfo.newBuilder().setRaw(60).setCalibrated(12).build())
                .build();

        ContextCalibratorInfo cci = ContextCalibratorInfo.newBuilder().addComparison(cinfo)
                .setCalibrator(CalibratorInfo.newBuilder().setType(CalibratorInfo.Type.SPLINE)
                        .setSplineCalibrator(spi).build())
                .build();
        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_CALIBRATORS)
                .addContextCalibrator(cci)
                .build();

        restClient.doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_10_3",
                HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(10, 0, 40);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(8, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        // remove all overrides
        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters//REFMDB/SUBSYS1/FloatPara1_10_3",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(0, 0, 30);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(3, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testOnlineParameterAlarmChange() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest subcr = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/EnumerationPara1_10_2");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subcr);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(MonitoringResult.WARNING, pdata.getParameter(0).getMonitoringResult());

        EnumerationAlarm ea = EnumerationAlarm.newBuilder().setLevel(AlarmLevelType.CRITICAL).setLabel("three_ok")
                .build();
        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_DEFAULT_ALARMS)
                .setDefaultAlarm(AlarmInfo.newBuilder().addEnumerationAlarm(ea).build()).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/EnumerationPara1_10_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.CRITICAL, pdata.getParameter(0).getMonitoringResult());

        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/EnumerationPara1_10_2",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(0, 3, 0);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.WARNING, pdata.getParameter(0).getMonitoringResult());

    }

    @Test
    public void testOnlineParameterContextAlarmChange() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest subcr = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/IntegerPara1_10_1");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subcr);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.SEVERE, pdata.getParameter(0).getMonitoringResult());

        // add a context calibrator for EnumerationPara1_10_2=3
        ComparisonInfo cinfo = ComparisonInfo.newBuilder()
                .setParameter(
                        ParameterInfo.newBuilder().setQualifiedName("/REFMDB/SUBSYS1/EnumerationPara1_10_2").build())
                .setOperator(OperatorType.EQUAL_TO)
                .setValue("three_ok")
                .build();
        AlarmInfo ai = AlarmInfo.newBuilder().addStaticAlarmRange(
                AlarmRange.newBuilder().setLevel(AlarmLevelType.DISTRESS).setMaxExclusive(70).build()).build();
        ContextAlarmInfo cai = ContextAlarmInfo.newBuilder().addComparison(cinfo).setAlarm(ai).build();

        UpdateParameterRequest cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.SET_ALARMS)
                .addContextAlarm(cai).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/IntegerPara1_10_1",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.DISTRESS, pdata.getParameter(0).getMonitoringResult());

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
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.SEVERE, pdata.getParameter(0).getMonitoringResult());

        // reset to the original MDB value
        cpr = UpdateParameterRequest.newBuilder().setAction(ActionType.RESET).addContextAlarm(cai).build();
        restClient
                .doRequest("/mdb/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/IntegerPara1_10_1",
                        HttpMethod.PATCH, cpr)
                .get();

        packetGenerator.generate_PKT1_10(80, 3, 0);
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(MonitoringResult.SEVERE, pdata.getParameter(0).getMonitoringResult());
    }

    @Test
    public void testOnlineAlgorithmChange() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest subcr = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/AlgoFloatAddition");

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subcr);
        wsClient.sendRequest(wsr).get();

        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(2.16729187, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        // change the algorithm
        AlgorithmInfo ai = AlgorithmInfo.newBuilder().setText("AlgoFloatAddition.value = 10 + f0.value + f1.value")
                .build();

        UpdateAlgorithmRequest car = UpdateAlgorithmRequest.newBuilder()
                .setAction(UpdateAlgorithmRequest.ActionType.SET).setAlgorithm(ai).build();
        restClient.doRequest("/mdb/IntegrationTest/realtime/algorithms/REFMDB/SUBSYS1/float_add",
                HttpMethod.PATCH, car).get();

        packetGenerator.generate_PKT1_1();
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(12.16729187, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

        // reset back to MDB version
        car = UpdateAlgorithmRequest.newBuilder().setAction(UpdateAlgorithmRequest.ActionType.RESET).build();
        restClient.doRequest("/mdb/IntegrationTest/realtime/algorithms/REFMDB/SUBSYS1/float_add",
                HttpMethod.PATCH, car).get();

        packetGenerator.generate_PKT1_1();
        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertEquals(2.16729187, pdata.getParameter(0).getEngValue().getFloatValue(), 1e-5);

    }

    @Test
    public void testWsParameterExpiration() throws Exception {
        // subscribe to parameters
        ParameterSubscriptionRequest req = getSubscription(false, true, false, "/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", req);
        wsClient.sendRequest(wsr).get();
        assertTrue(wsListener.parameterDataList.isEmpty());

        // generate a TM packets and monitor realtime reception
        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(pdata);
        // assertEquals(2, pdata.getParameterCount());
        checkPvals(pdata.getParameterList(), packetGenerator);

        // after 1.5 sec we should get an set of expired parameters
        pdata = wsListener.parameterDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        for (ParameterValue pv : pdata.getParameterList()) {
            assertEquals(AcquisitionStatus.EXPIRED, pv.getAcquisitionStatus());
        }
    }

    @Test
    public void testWsTime() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("time", "subscribe");
        wsClient.sendRequest(wsr);
        TimeInfo ti = wsListener.timeInfoList.poll(2, TimeUnit.SECONDS);
        assertNotNull(ti);
    }

    @Test
    public void testWsParameterUnsubscription() throws Exception {
        ParameterSubscriptionRequest.Builder subscr1 = ParameterSubscriptionRequest.newBuilder()
                .setSendFromCache(false);
        subscr1.addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build());
        subscr1.addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7").build());
        subscr1.addId(NamedObjectId.newBuilder().setNamespace("MDB:AliasParam").setName("para6alias").build());

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", subscr1.build());
        WebSocketReplyData wsrd = wsClient.sendRequest(wsr).get();

        ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        int subscrId1 = psr.getSubscriptionId();

        packetGenerator.generate_PKT1_1();
        ParameterData pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        assertNotNull(pdata);
        checkPvals(3, pdata.getParameterList(), packetGenerator);

        ParameterSubscriptionRequest subscrList = getSubscription(false, false, false,
                "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        wsr = new WebSocketRequest("parameter", "unsubscribe", subscrList);
        wsClient.sendRequest(wsr).get();
        packetGenerator.generate_PKT1_1();

        pdata = wsListener.parameterDataList.poll(5, TimeUnit.SECONDS);
        checkPvals(2, pdata.getParameterList(), packetGenerator);

        wsr = new WebSocketRequest("parameter", "unsubscribeAll",
                ParameterSubscriptionRequest.newBuilder().setSubscriptionId(subscrId1).build());
        wsClient.sendRequest(wsr).get();

        // we subscribe again and should get a different subscription id
        wsr = new WebSocketRequest("parameter", "subscribe", subscr1.build());
        wsrd = wsClient.sendRequest(wsr).get();

        psr = ParameterSubscriptionResponse.parseFrom(wsrd.getData());
        int subscrId2 = psr.getSubscriptionId();
        assertTrue(subscrId1 != subscrId2);
    }

    @Test
    public void testRestParameterGet() throws Exception {
        ////// gets parameters from cache via REST - first attempt with one invalid parameter
        ParameterSubscriptionRequest invalidSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7",
                "/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/InvalidParaName");
        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addAllId(invalidSubscrList.getIdList()).build();

        try {
            restClient
                    .doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                    .get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            String err = e.getMessage();
            assertTrue(err.contains("Invalid parameters"));
            assertTrue(err.contains("/REFMDB/SUBSYS1/InvalidParaName"));
        }

        packetGenerator.generate_PKT1_1();
        Thread.sleep(1000);
        /////// gets parameters from cache via REST - second attempt with valid parameters
        ParameterSubscriptionRequest validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getIdList())
                .build();

        byte[] response = restClient
                .doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                .get();
        BatchGetParameterValuesResponse bulkPvals = BatchGetParameterValuesResponse.parseFrom(response);
        checkPvals(bulkPvals.getValueList(), packetGenerator);

        /////// gets parameters from via REST - waiting for update - first test the timeout in case no update is coming
        long t0 = System.currentTimeMillis();
        req = BatchGetParameterValuesRequest.newBuilder()
                .setFromCache(false)
                .setTimeout(2000).addAllId(validSubscrList.getIdList()).build();

        Future<byte[]> responseFuture = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchGet",
                HttpMethod.POST, req);

        bulkPvals = BatchGetParameterValuesResponse.parseFrom(responseFuture.get());
        long t1 = System.currentTimeMillis();
        assertEquals(2000, t1 - t0, 200);
        assertEquals(0, bulkPvals.getValueCount());
        //////// gets parameters from via REST - waiting for update - now with some parameters updated
        packetGenerator.pIntegerPara1_1_6 = 10;
        packetGenerator.pIntegerPara1_1_7 = 5;
        responseFuture = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchGet",
                HttpMethod.POST, req);
        Thread.sleep(1000); // wait to make sure that the subscription request has reached the server

        packetGenerator.generate_PKT1_1();

        bulkPvals = BatchGetParameterValuesResponse.parseFrom(responseFuture.get());

        checkPvals(bulkPvals.getValueList(), packetGenerator);
    }

    @Test
    public void testRestParameterGetAggregateMember() throws Exception {
        packetGenerator.generate_PKT7();
        packetGenerator.generate_PKT8();
        /////// gets parameters from cache via REST
        ParameterSubscriptionRequest subscrList = getSubscription("/REFMDB/SUBSYS1/aggregate_para1.member1",
                "/REFMDB/SUBSYS1/aggregate_para1.member3", "/REFMDB/SUBSYS1/array_para1[105].member2");
        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addAllId(subscrList.getIdList())
                .build();

        byte[] response = restClient
                .doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                .get();

        BatchGetParameterValuesResponse bulkPvals = BatchGetParameterValuesResponse.parseFrom(response);

        assertEquals(3, bulkPvals.getValueCount());
        ParameterValue pv = bulkPvals.getValue(0);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member1", pv.getId().getName());
        assertEquals(2, pv.getEngValue().getUint32Value());

        pv = bulkPvals.getValue(2);
        assertEquals("/REFMDB/SUBSYS1/array_para1[105].member2", pv.getId().getName());
        assertEquals(210, pv.getRawValue().getUint32Value());

        /////// gets parameters from via REST - waiting for update
        req = BatchGetParameterValuesRequest.newBuilder()
                .setFromCache(false)
                .setTimeout(2000).addAllId(subscrList.getIdList()).build();

        Future<byte[]> responseFuture = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchGet",
                HttpMethod.POST, req);

        Thread.sleep(1000); // wait to make sure that the subscription request has reached the server

        packetGenerator.generate_PKT7();
        packetGenerator.generate_PKT8();

        bulkPvals = BatchGetParameterValuesResponse.parseFrom(responseFuture.get());
        assertEquals(3, bulkPvals.getValueCount());
        pv = bulkPvals.getValue(1);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member3", pv.getId().getName());
        assertEquals(2.72, pv.getEngValue().getFloatValue(), 1e-5);

        ///// get the value with the single get, we have to URL encode [ and ]
        byte[] resp = restClient.doRequest(
                "/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/array_para1%5B149%5D.member2",
                HttpMethod.GET).get();
        pv = ParameterValue.parseFrom(resp);
        assertEquals("/REFMDB/SUBSYS1", pv.getId().getNamespace());
        assertEquals("array_para1[149].member2", pv.getId().getName());
        assertEquals(298, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testRestParameterGetArrayAggregate() throws Exception {
        packetGenerator.generate_PKT8();

        ParameterSubscriptionRequest subscrList = getSubscription("/REFMDB/SUBSYS1/array_para1");
        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addAllId(subscrList.getIdList())
                .build();

        byte[] response = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchGet",
                HttpMethod.POST, req).get();
        BatchGetParameterValuesResponse pvals = BatchGetParameterValuesResponse.parseFrom(response);
        assertEquals(1, pvals.getValueCount());
        ParameterValue pv = pvals.getValue(0);
        Value v = pv.getEngValue();
        assertEquals(Value.Type.ARRAY, v.getType());

        Value v1 = v.getArrayValue(10);
        assertEquals(Value.Type.AGGREGATE, v1.getType());
        AggregateValue av = v1.getAggregateValue();
        assertEquals("member1", av.getName(0));
        assertEquals(5.0, av.getValue(2).getFloatValue(), 1e-5);
    }

    @Test
    public void testRestParameterSetInvalidParam() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"));
        requestb.setValue(ValueHelper.newValue(3.14));
        bulkb.addRequest(requestb);

        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                    bulkb.build()).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClientException);
        }
    }

    @Test
    public void testRestParameterSetInvalidType() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue("blablab"));
        bulkb.addRequest(requestb);

        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                    bulkb.build()).get();
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClientException);
        }
    }

    @Test
    public void testRestParameterSet() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"));
        requestb.setValue(ValueHelper.newValue(5));
        bulkb.addRequest(requestb);

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                bulkb.build()).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara1",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);
        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSet2() throws Exception {
        // test simple set just for the value
        Value v = ValueHelper.newValue(3.14);
        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2",
                HttpMethod.POST, v).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalPara2",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);
        assertEquals(v, pv.getEngValue());
    }

    @Test
    public void testRestParameterSetAggregate_Invalid() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalArray1"));

        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newValue(10),
                "member2", ValueHelper.newValue(1300));
        requestb.setValue(ValueHelper.newArrayValue(v0));
        bulkb.addRequest(requestb);
        try {
            restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                    bulkb.build()).get();
        } catch (ExecutionException e) {
            ClientException e1 = (ClientException) e.getCause();
            assertTrue(e1.getMessage().contains("members don't match"));
            return;
        }

        fail("should have got an exception");
    }

    @Test
    public void testRestParameterSetArray() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalArray1"));

        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newValue(10),
                "member2", ValueHelper.newValue(1300),
                "member3", ValueHelper.newValue(3.14));

        requestb.setValue(ValueHelper.newArrayValue(v0, v0));
        bulkb.addRequest(requestb);

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                bulkb.build()).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalArray1",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);
        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSetAggregate() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalAggregate1"));

        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newValue(10),
                "member2", ValueHelper.newValue(1300),
                "member3", ValueHelper.newValue(3.14));

        requestb.setValue(v0);
        bulkb.addRequest(requestb);

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                bulkb.build()).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalAggregate1",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);
        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSetAggregateElement() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalParaWithInitialValue6.member1"));

        Value v0 = ValueHelper.newValue(55);

        requestb.setValue(v0);
        bulkb.addRequest(requestb);

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                bulkb.build()).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest(
                "/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue6.member1",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);

        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSetArrayElement() throws Exception {
        BatchSetParameterValuesRequest.Builder bulkb = BatchSetParameterValuesRequest.newBuilder();
        SetParameterValueRequest.Builder requestb = SetParameterValueRequest.newBuilder();
        requestb.setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalParaWithInitialValue8[2]"));

        Value v0 = ValueHelper.newValue((float) 55.2);

        requestb.setValue(v0);
        bulkb.addRequest(requestb);

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                bulkb.build()).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest(
                "/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue8%5B2%5D",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);

        assertEquals(requestb.getValue(), pv.getEngValue());
    }

    @Test
    public void testRestParameterSetArrayElement2() throws Exception {
        // test simple set just for the value
        Value v = ValueHelper.newValue((float) 89.3);
        byte[] resp = restClient.doRequest(
                "/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue8%5B2%5D",
                HttpMethod.POST, v).get();
        assertNotNull(resp);

        Thread.sleep(1000); // the software parameter manager sets the parameter in another thread so it might not be
        // immediately avaialble
        resp = restClient.doRequest(
                "/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/LocalParaWithInitialValue8%5B2%5D",
                HttpMethod.GET).get();
        ParameterValue pv = ParameterValue.parseFrom(resp);

        assertEquals(v, pv.getEngValue());
    }

    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        // first subscribe to command history
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);
        wsListener.cmdHistoryDataList.clear();

        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }

    /*-@Test
    public void testValidateCommand() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);
    
        ValidateCommandRequest cmdreq = getValidateCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 10, "p1", "2");
        String resp = doRequest("/commanding/validator", HttpMethod.POST, cmdreq, SchemaRest.ValidateCommandRequest.WRITE);
        ValidateCommandResponse vcr = (fromJson(resp, SchemaRest.ValidateCommandResponse.MERGE)).build();
        assertEquals(1, vcr.getCommandSignificanceCount());
        CommandSignificance significance = vcr.getCommandSignificance(0);
        assertEquals(10, significance.getSequenceNumber());
        assertEquals(SignificanceLevelType.CRITICAL, significance.getSignificance().getConsequenceLevel());
        assertEquals("this is a critical command, pay attention", significance.getSignificance().getReasonForWarning());
    
    }*/

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);

        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NOK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "NOK");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Message",
                "Transmission constraints check failed");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "NOK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Message",
                "Transmission constraints check failed");
    }

    @Test
    public void testSendCommandDisableTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);

        CommandOptions co = CommandOptions.newBuilder().setDisableTransmissionConstraints(true).build();
        IssueCommandRequest cmdreq = getCommand(6, "p1", "2").toBuilder().setCommandOptions(co).build();
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);

        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC2", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "PENDING");

        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
        assertNull(cmdhist);
        Value v = ValueHelper.newValue(true);
        restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/AllowCriticalTC2",
                HttpMethod.POST, v).get();

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testUpdateCommandHistory() throws Exception {

        // Send a command a store its commandId
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        // insert two values in the command history
        String commandId = commandResponse.getId();
        UpdateCommandHistoryRequest.Builder updateHistoryRequest = UpdateCommandHistoryRequest.newBuilder()
                .setId(commandId);
        updateHistoryRequest.addAttributes(CommandHistoryAttribute.newBuilder()
                .setName("testKey1")
                .setValue(Value.newBuilder().setType(Type.STRING).setStringValue("testValue1")));
        updateHistoryRequest.addAttributes(CommandHistoryAttribute.newBuilder()
                .setName("testKey2")
                .setValue(Value.newBuilder().setType(Type.STRING).setStringValue("testValue2")));
        doRealtimeRequest("/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST,
                updateHistoryRequest.build());

        // Query command history and check that we can retreive the inserted values
        byte[] respDl = restClient.doRequest("/archive/IntegrationTest/commands", HttpMethod.GET).get();
        ListCommandsResponse lastPage = ListCommandsResponse.parseFrom(respDl);
        CommandHistoryEntry lastEntry = lastPage.getEntry(0);
        boolean foundKey1 = false, foundKey2 = false;
        for (CommandHistoryAttribute cha : lastEntry.getAttrList()) {
            if (cha.getName().equals("testKey1") &&
                    cha.getValue().getStringValue().equals("testValue1")) {
                foundKey1 = true;
            }
            if (cha.getName().equals("testKey2") &&
                    cha.getValue().getStringValue().equals("testValue2")) {
                foundKey2 = true;
            }
        }
        assertTrue(foundKey1);
        assertTrue(foundKey2);
    }

    /*
     * private ValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) { NamedObjectId cmdId
     * = NamedObjectId.newBuilder().setName(cmdName).build();
     * 
     * CommandType.Builder cmdb =
     * CommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq); for(int i =0
     * ;i<args.length; i+=2) {
     * cmdb.addArguments(ArgumentAssignmentType.newBuilder().setName(args[i]).setValue(args[i+1]).build()); }
     * 
     * return ValidateCommandRequest.newBuilder().addCommand(cmdb.build()).build(); }
     */

    private void checkPvals(List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        checkPvals(2, pvals, packetProvider);
    }

    private void checkPvals(int expectedNumParams, List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        checkPvals(Collections.emptyList(), expectedNumParams, pvals, packetProvider);
    }

    private void checkPvals(List<SubscribedParameter> list, int expectedNumParams, List<ParameterValue> pvals,
            RefMdbPacketGenerator packetProvider) {

        assertNotNull(pvals);

        assertEquals(expectedNumParams, pvals.size());
        Map<Integer, NamedObjectId> numericId = new HashMap<>();

        for (SubscribedParameter sp : list) {
            numericId.put(sp.getNumericId(), sp.getId());
        }

        for (ParameterValue p : pvals) {
            assertEquals(AcquisitionStatus.ACQUIRED, p.getAcquisitionStatus());
            Value praw = p.getRawValue();
            assertNotNull(praw);
            Value peng = p.getEngValue();
            NamedObjectId id;

            if (p.hasNumericId()) {
                id = numericId.get(p.getNumericId());
                if (id == null) {
                    fail("Unknown numeric id " + p.getNumericId());
                }
            } else if (p.hasId()) {
                id = p.getId();
            } else {
                fail("Parameter has neither id nor numericId");
                return;
            }
            if ("/REFMDB/SUBSYS1/IntegerPara1_1_6".equals(id.getName())
                    || "para6alias".equals(p.getId().getName())) {
                assertEquals(Type.UINT32, praw.getType());
                assertEquals(packetProvider.pIntegerPara1_1_6, praw.getUint32Value());

                assertEquals(Type.UINT32, peng.getType());
                assertEquals(packetProvider.pIntegerPara1_1_6, peng.getUint32Value());

            } else if ("/REFMDB/SUBSYS1/IntegerPara1_1_7".equals(id.getName())) {
                assertEquals(Type.UINT32, praw.getType());
                assertEquals(packetProvider.pIntegerPara1_1_7, praw.getUint32Value());

                assertEquals(Type.UINT32, peng.getType());
                assertEquals(packetProvider.pIntegerPara1_1_7, peng.getUint32Value());
            } else {
                fail("Unkonwn parameter '" + id + "'");
            }
        }
    }

    @Test
    public void testServicesStopStart() throws Exception {
        String serviceClass = "org.yamcs.archive.CommandHistoryRecorder";

        byte[] resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        ListServicesResponse r = ListServicesResponse.parseFrom(resp);
        assertEquals(9, r.getServicesList().size());

        ServiceInfo servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + servInfo.getName() + ":stop", HttpMethod.POST)
                .get();

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        r = ListServicesResponse.parseFrom(resp);
        servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.TERMINATED, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + servInfo.getName() + ":start", HttpMethod.POST)
                .get();

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        r = ListServicesResponse.parseFrom(resp);
        servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());
    }

    @Test
    public void testRestEvents() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("events", "subscribe");
        wsClient.sendRequest(wsr).get();

        long now = TimeEncoding.getWallclockTime();
        CreateEventRequest request = CreateEventRequest.newBuilder()
                .setTime(TimeEncoding.toString(now))
                .setMessage("event1")
                .build();
        restClient.doRequest("/archive/" + yamcsInstance + "/events", HttpMethod.POST, request);

        Event e2 = wsListener.eventList.poll(2, TimeUnit.SECONDS);
        assertNotNull(e2);
        assertEquals(now, TimeEncoding.parse(e2.getGenerationTimeUTC()));
        assertEquals("event1", e2.getMessage());
    }

    @Test
    public void testEventAlarms() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("alarms", "subscribe",
                AlarmSubscriptionRequest.newBuilder().setDetail(true).build());
        wsClient.sendRequest(wsr).get();

        Thread.sleep(2000);
        wsListener.alarmDataList.clear();

        CreateEventRequest request = CreateEventRequest.newBuilder()
                .setSeverity("warning")
                .setSource("IntegrationTest")
                .setType("Event-Alarm-Test")
                .setMessage("event1")
                .build();
        restClient.doRequest("/archive/" + yamcsInstance + "/events", HttpMethod.POST, request);

        AlarmData a1 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(a1);
        EventAlarmData ea1 = a1.getEventDetail();

        assertEquals(EventSeverity.WARNING, ea1.getTriggerEvent().getSeverity());
        assertEquals("IntegrationTest", ea1.getTriggerEvent().getSource());
        assertEquals("Event-Alarm-Test", ea1.getTriggerEvent().getType());
        assertEquals("event1", ea1.getTriggerEvent().getMessage());

        EditAlarmRequest ear = EditAlarmRequest.newBuilder().setState("shelved")
                .setComment("I will deal with this later")
                .setShelveDuration(500).build();
        restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms/" + a1.getId().getNamespace() + "/"
                + a1.getId().getName() + "/" + a1.getSeqNum(), HttpMethod.PATCH, ear).get();
        AlarmData a2 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        assertTrue(a2.hasShelveInfo());
        assertEquals("I will deal with this later", a2.getShelveInfo().getShelveMessage());

        // after 500 millisec, the shelving has expired
        AlarmData a3 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);
        assertEquals(AlarmNotificationType.UNSHELVED, a3.getNotificationType());

        // shelve it again
        ear = EditAlarmRequest.newBuilder().setState("shelved").setComment("I will deal with this later#2")
                .build();
        restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms/" + a1.getId().getNamespace() + "/"
                + a1.getId().getName() + "/" + a1.getSeqNum(), HttpMethod.PATCH, ear).get();
        a2 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        a3 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);
        assertNull(a3);

        // System.out.println("a1: " + a1);
        ear = EditAlarmRequest.newBuilder().setState("acknowledged").setComment("a nice ack explanation")
                .build();
        restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms/" + a1.getId().getNamespace() + "/"
                + a1.getId().getName() + "/" + a1.getSeqNum(), HttpMethod.PATCH, ear).get();
        AlarmData a4 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);

        assertNotNull(a4);
        assertEquals("a nice ack explanation", a4.getAcknowledgeInfo().getAcknowledgeMessage());

        byte[] resp = restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms", HttpMethod.GET)
                .get();
        ListAlarmsResponse lar = ListAlarmsResponse.parseFrom(resp);

        assertEquals(1, lar.getAlarmsCount());
        assertEquals("a nice ack explanation", lar.getAlarms(0).getAcknowledgeInfo().getAcknowledgeMessage());

        ear = EditAlarmRequest.newBuilder().setState("cleared").setComment("a nice clear explanation")
                .build();
        restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms/" + a1.getId().getNamespace() + "/"
                + a1.getId().getName() + "/" + a1.getSeqNum(), HttpMethod.PATCH, ear).get();
        AlarmData a5 = wsListener.alarmDataList.poll(2, TimeUnit.SECONDS);

        assertNotNull(a5);
        assertTrue(a5.hasClearInfo());
        assertEquals("a nice clear explanation", a5.getClearInfo().getClearMessage());

        resp = restClient.doRequest("/processors/" + yamcsInstance + "/realtime/alarms", HttpMethod.GET).get();

        lar = ListAlarmsResponse.parseFrom(resp);
        assertEquals(0, lar.getAlarmsCount());
    }

    @Test
    public void testStaticFile() throws Exception {
        YamcsServer.getServer().getGlobalServices(HttpServer.class).get(0).addStaticRoot(Paths.get("/tmp/yamcs-web"));

        HttpClient httpClient = new HttpClient();
        File dir = new File("/tmp/yamcs-web/");
        dir.mkdirs();

        File file1 = File.createTempFile("test1_", null, dir);
        FileOutputStream file1Out = new FileOutputStream(file1);
        Random rand = new Random();
        byte[] b = new byte[1932];
        for (int i = 0; i < 20; i++) {
            rand.nextBytes(b);
            file1Out.write(b);
        }
        file1Out.close();

        File file2 = File.createTempFile("test2_", null, dir);
        FileOutputStream file2Out = new FileOutputStream(file2);

        httpClient
                .doBulkReceiveRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null, data -> {
                    try {
                        file2Out.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).get();
        file2Out.close();
        assertTrue(com.google.common.io.Files.equal(file1, file2));

        // test if not modified since
        SimpleDateFormat dateFormatter = new SimpleDateFormat(StaticFileHandler.HTTP_DATE_FORMAT);

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified()));
        ClientException e1 = null;
        try {
            httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                    httpHeaders).get();
        } catch (ExecutionException e) {
            e1 = (ClientException) e.getCause();
        }
        assertNotNull(e1);
        assertTrue(e1.toString().contains("304"));

        httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified() - 1000));
        byte[] b1 = httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                httpHeaders).get();
        assertEquals(file1.length(), b1.length);

        file1.delete();
        file2.delete();
    }
}
