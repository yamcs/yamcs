package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.client.processor.ProcessorClient.GetOptions;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.AggregateValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;

public class ParameterTest extends AbstractIntegrationTest {

    private ProcessorClient processorClient;

    @BeforeEach
    public void prepare() {
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
    }

    @Test
    @Disabled
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
        subscription.awaitConfirmation();

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
        subscription.awaitConfirmation();

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
        subscription.awaitConfirmation();

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
        try {
            processorClient.getValues(Arrays.asList(
                    "/REFMDB/SUBSYS1/IntegerPara1_1_7",
                    "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                    "/REFMDB/SUBSYS1/InvalidParaName"),
                    GetOptions.fromCache(true))
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
        List<ParameterValue> values = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7"),
                GetOptions.fromCache(true))
                .get();
        checkPvals(values, packetGenerator);

        /*
         * Waiting for an update. first test the timeout in case no update is coming
         */
        long t0 = System.currentTimeMillis();
        values = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7"),
                GetOptions.fromCache(false),
                GetOptions.timeout(2000))
                .get();

        long t1 = System.currentTimeMillis();
        assertEquals(2000, t1 - t0, 200);
        assertEquals(0, values.size());

        packetGenerator.pIntegerPara1_1_6 = 10;
        packetGenerator.pIntegerPara1_1_7 = 5;

        /*
         * Test the timeout functionality
         */
        CompletableFuture<List<ParameterValue>> bulkPvalsFuture = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7"),
                GetOptions.fromCache(false),
                GetOptions.timeout(2000));
        Thread.sleep(1000); // wait to make sure that the subscription request has reached the server

        packetGenerator.generate_PKT1_1();
        values = bulkPvalsFuture.get();
        checkPvals(values, packetGenerator);
    }

    @Test
    public void testBatchGetAggregateMembers() throws Exception {
        packetGenerator.generate_PKT7();
        packetGenerator.generate_PKT8();

        List<ParameterValue> values = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/aggregate_para1.member1",
                "/REFMDB/SUBSYS1/aggregate_para1.member3",
                "/REFMDB/SUBSYS1/array_para1[105].member2"),
                GetOptions.fromCache(true))
                .get();

        assertEquals(3, values.size());
        ParameterValue pv = values.get(0);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member1", pv.getId().getName());
        assertEquals(2, pv.getEngValue().getUint32Value());

        pv = values.get(2);
        assertEquals("/REFMDB/SUBSYS1/array_para1[105].member2", pv.getId().getName());
        assertEquals(210, pv.getRawValue().getUint32Value());

        // Retrieve with a timeout
        CompletableFuture<List<ParameterValue>> valuesFuture = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/aggregate_para1.member1",
                "/REFMDB/SUBSYS1/aggregate_para1.member3",
                "/REFMDB/SUBSYS1/array_para1[105].member2"),
                GetOptions.fromCache(false),
                GetOptions.timeout(2000));

        Thread.sleep(1000); // wait to make sure that the subscription request has reached the server

        packetGenerator.generate_PKT7();
        packetGenerator.generate_PKT8();

        values = valuesFuture.get();
        assertEquals(3, values.size());
        pv = values.get(1);
        assertEquals("/REFMDB/SUBSYS1/aggregate_para1.member3", pv.getId().getName());
        assertEquals(2.72, pv.getEngValue().getFloatValue(), 1e-5);

        // Get the value with the single get
        pv = processorClient.getValue("/REFMDB/SUBSYS1/array_para1[149].member2").get();
        assertEquals("/REFMDB/SUBSYS1", pv.getId().getNamespace());
        assertEquals("array_para1[149].member2", pv.getId().getName());
        assertEquals(298, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testBatchGetArraysAndAggregates() throws Exception {
        packetGenerator.generate_PKT8();

        List<ParameterValue> values = processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/array_para1"),
                GetOptions.fromCache(true))
                .get();

        assertEquals(1, values.size());
        ParameterValue pv = values.get(0);
        Value v = pv.getEngValue();
        assertEquals(Value.Type.ARRAY, v.getType());

        Value v1 = v.getArrayValue(10);
        assertEquals(Value.Type.AGGREGATE, v1.getType());
        AggregateValue av = v1.getAggregateValue();
        assertEquals("member1", av.getName(0));
        assertEquals(5.0, av.getValue(2).getFloatValue(), 1e-5);
    }

    @Test
    public void testSetInvalidParameterValue() {
        assertThrows(ClientException.class, () -> {
            try {
                processorClient.setValue("/REFMDB/SUBSYS1/IntegerPara1_1_6", ValueHelper.newValue(3.14)).get();
            } catch (ExecutionException e) {
                throw (ClientException) e.getCause();
            }
        });
    }

    @Test
    public void testSetParameterWithInvalidType() {
        assertThrows(ClientException.class, () -> {
            try {
                processorClient.setValue("/REFMDB/SUBSYS1/LocalPara1", ValueHelper.newValue("blablab")).get();
            } catch (ExecutionException e) {
                throw (ClientException) e.getCause();
            }
        });
    }

    @Test
    public void testSetParameter() throws Exception {
        processorClient.setValue("/REFMDB/SUBSYS1/LocalPara1", ValueHelper.newValue(5)).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue value = processorClient.getValue("/REFMDB/SUBSYS1/LocalPara1").get();
        assertEquals(ValueHelper.newUnsignedValue(5), value.getEngValue());
    }

    @Test
    public void testSetParameter2() throws Exception {
        processorClient.setValue("/REFMDB/SUBSYS1/LocalPara2", ValueHelper.newValue(3.14)).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue value = processorClient.getValue("/REFMDB/SUBSYS1/LocalPara2").get();
        assertEquals(ValueHelper.newValue(3.14f), value.getEngValue());
    }

    @Test
    public void testSetParameter9() throws Exception {
        String ts = "2021-03-11T00:00:00.000Z";
        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaTime9", ValueHelper.newValue(ts)).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue value = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaTime9").get();
        Value tv = value.getEngValue();
        assertEquals(Yamcs.Value.Type.TIMESTAMP, tv.getType());
        assertEquals(ts, tv.getStringValue());
        assertEquals(TimeEncoding.parse(ts), tv.getTimestampValue());
    }

    @Test
    public void testSetParameter10() throws Exception {
        String ts = "2021-03-11T00:00:00.000Z";
        Value vsent = Value.newBuilder().setType(Type.TIMESTAMP).setStringValue(ts).build();
        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaTime9", vsent).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue value = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaTime9").get();
        Value tv = value.getEngValue();
        assertEquals(Yamcs.Value.Type.TIMESTAMP, tv.getType());
        assertEquals(ts, tv.getStringValue());
        assertEquals(TimeEncoding.parse(ts), tv.getTimestampValue());
    }

    @Test
    public void testSetAggregateParameter_Invalid() throws Exception {
        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newValue(10),
                "member2", ValueHelper.newValue(1300));
        try {
            processorClient.setValue("/REFMDB/SUBSYS1/LocalArray1",
                    ValueHelper.newArrayValue(v0)).get();
        } catch (ExecutionException e) {
            ClientException e1 = (ClientException) e.getCause();
            assertTrue(e1.getMessage().contains("no value for member member3"));
            return;
        }

        fail("should have thrown an exception");
    }

    @Test
    public void testSetArrayParameter() throws Exception {
        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newUnsignedValue(10),
                "member2", ValueHelper.newUnsignedValue(1300),
                "member3", ValueHelper.newValue(3.14f));
        v0 = ValueHelper.newArrayValue(v0, v0);

        processorClient.setValue("/REFMDB/SUBSYS1/LocalArray1", v0).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue pv = processorClient.getValue("/REFMDB/SUBSYS1/LocalArray1").get();
        assertEquals(v0, pv.getEngValue());
    }

    @Test
    public void testSetParameter_Aggregate() throws Exception {
        Value v0 = ValueHelper.newAggregateValue("member1", ValueHelper.newUnsignedValue(10),
                "member2", ValueHelper.newUnsignedValue(1300),
                "member3", ValueHelper.newValue(3.14f));

        processorClient.setValue("/REFMDB/SUBSYS1/LocalAggregate1", v0).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue pv = processorClient.getValue("/REFMDB/SUBSYS1/LocalAggregate1").get();
        assertEquals(v0, pv.getEngValue());
    }

    @Test
    public void testSetParameter_AggregateElement() throws Exception {
        Value v0 = ValueHelper.newValue(55);
        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue6.member1", v0).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue pv = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue6.member1").get();
        assertEquals(v0, pv.getEngValue());
    }

    @Test
    public void testSetParameter_ArrayElement() throws Exception {
        Value v0 = ValueHelper.newValue((float) 55.2);
        processorClient.setValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue8[2]", v0).get();

        // parameter is set in another thread so it might not be immediately available
        Thread.sleep(1000);

        ParameterValue pv = processorClient.getValue("/REFMDB/SUBSYS1/LocalParaWithInitialValue8[2]").get();
        assertEquals(v0, pv.getEngValue());
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
