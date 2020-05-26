package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchGetParameterValuesResponse;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.BatchSetParameterValuesRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.AggregateValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueHelper;

import io.netty.handler.codec.http.HttpMethod;

public class ParameterTest extends AbstractIntegrationTest {

    @Ignore
    @Test
    public void testParameterSubscriptionPerformance() throws Exception {
        long t0 = System.currentTimeMillis();

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .build();
        yamcsClient.createParameterSubscription().sendMessage(request);

        for (int i = 0; i < 1000000; i++) {
            packetGenerator.generate_PKT1_1();
        }
        System.out.println("total time: " + (System.currentTimeMillis() - t0));
    }

    @Test
    public void testSimpleSubscription() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        assertTrue(captor.isEmpty());
        packetGenerator.generate_PKT1_1();

        List<ParameterValue> values = captor.expectTimely();
        checkPvals(2, values, packetGenerator);

        captor.assertSilence();
    }

    @Test
    public void testWithAnInvalidIdentifier() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        /*
         * Subscribe to three parameters, one of which is invalid
         */
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/InvalidParaName"))
                .setSendFromCache(false)
                .setAbortOnInvalid(false)
                .build();
        subscription.sendMessage(request);

        assertEquals("/REFMDB/SUBSYS1/InvalidParaName", captor.expectTimelyInvalidIdentifier().getName());

        /*
         * Emit a packet, and expect to receive one update.
         */
        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        checkPvals(values, packetGenerator);
        captor.assertSilence();

        /*
         * Unsubscribe from both of the valid parameters.
         */
        subscription.remove(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build()));
        captor.assertSilence();

        /*
         * Subscribe again, and expect to receive cached values
         */
        captor.clear();
        subscription.add(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7").build(),
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build()));

        values = captor.expectTimely();
        checkPvals(values, packetGenerator);
    }

    @Test
    public void testAggregatesAndArrays() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        // The array has only 150 elements (0-149), the [150] is subscribed but never received
        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.member2"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/array_para1[3].member3"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/array_para1[150].member3"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);

        // Give the subscription some time to establish before emitting a packet
        Thread.sleep(2000);

        packetGenerator.generate_PKT7();
        List<ParameterValue> values = captor.expectTimely();
        ParameterValue value = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member2", value.getId().getName());
        assertEquals(30, value.getEngValue().getUint32Value());

        packetGenerator.generate_PKT8();
        values = captor.expectTimely();

        value = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/array_para1[3].member3", value.getId().getName());
        assertEquals(1.5, value.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void testInvalidAggregateMember() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.invalid_member"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        captor.expectTimelyInvalidIdentifier();
    }

    @Test
    public void testParameterExpiration() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .setSendFromCache(false)
                .setUpdateOnExpiration(true)
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        checkPvals(values, packetGenerator);

        // After 1500*1.9 millisec we should get a set of expired parameters
        values = captor.poll(4500);
        assertNotNull(values);
        assertEquals(2, values.size());
        for (ParameterValue pv : values) {
            assertEquals(AcquisitionStatus.EXPIRED, pv.getAcquisitionStatus());
        }
    }

    @Test
    public void testSubscriptionModification() throws Exception {
        ParameterSubscription subscription = yamcsClient.createParameterSubscription();
        ParameterCaptor captor = ParameterCaptor.of(subscription);

        SubscribeParametersRequest request = SubscribeParametersRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setNamespace("MDB:AliasParam").setName("para6alias"))
                .setSendFromCache(false)
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_1();
        List<ParameterValue> values = captor.expectTimely();
        checkPvals(3, values, packetGenerator);

        subscription.remove(Arrays.asList(
                NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build()));
        Thread.sleep(2000);

        packetGenerator.generate_PKT1_1();
        values = captor.expectTimely();
        checkPvals(2, values, packetGenerator);
    }

    @Test
    public void testBatchGet() throws Exception {
        /*
         *  Include one invalid parameter
         */
        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/InvalidParaName"))
                .build();
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

        /*
         * From cache, with all valid identifiers
         */
        req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .build();

        byte[] response = restClient
                .doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                .get();
        BatchGetParameterValuesResponse bulkPvals = BatchGetParameterValuesResponse.parseFrom(response);
        checkPvals(bulkPvals.getValueList(), packetGenerator);

        /*
         * Waiting for an update. first test the timeout in case no update is coming
         */
        long t0 = System.currentTimeMillis();
        req = BatchGetParameterValuesRequest.newBuilder()
                .setFromCache(false)
                .setTimeout(2000)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_7"))
                .build();

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
    public void testBatchGetAggregateMembers() throws Exception {
        packetGenerator.generate_PKT7();
        packetGenerator.generate_PKT8();

        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder()
                .setFromCache(true)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.member1"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.member3"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/array_para1[105].member2"))
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
                .setTimeout(2000)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.member1"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/aggregate_para1.member3"))
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/array_para1[105].member2"))
                .build();

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
    public void testBatchGetArraysAndAggregates() throws Exception {
        packetGenerator.generate_PKT8();

        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder()
                .setFromCache(true)
                .addId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/array_para1"))
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
    public void testSetInvalidParameterValue() throws Exception {
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
    public void testSetParameterWithInvalidType() throws Exception {
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
    public void testSetParameter() throws Exception {
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
    public void testSetParameter2() throws Exception {
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
    public void testSetAggregateParameter_Invalid() throws Exception {
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
    public void testSetArrayParameter() throws Exception {
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
    public void testSetParameter_Aggregate() throws Exception {
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
    public void testSetParameter_AggregateElement() throws Exception {
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
    public void testSetParameter_ArrayElement() throws Exception {
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
    public void testSetParameter_ArrayElement2() throws Exception {
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

    private void checkPvals(List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        checkPvals(2, pvals, packetProvider);
    }

    private void checkPvals(int expectedNumParams, List<ParameterValue> pvals, RefMdbPacketGenerator packetProvider) {
        assertNotNull(pvals);
        assertEquals(expectedNumParams, pvals.size());

        for (ParameterValue p : pvals) {
            // Due to unit tests waiting for certain events, it's quite plausible to
            // receive expired parameter values.
            assertTrue(AcquisitionStatus.ACQUIRED == p.getAcquisitionStatus()
                    || AcquisitionStatus.EXPIRED == p.getAcquisitionStatus());
            Value praw = p.getRawValue();
            assertNotNull(praw);
            Value peng = p.getEngValue();
            NamedObjectId id = p.getId();
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
                fail("Unknown parameter '" + id + "'");
            }
        }
    }
}
