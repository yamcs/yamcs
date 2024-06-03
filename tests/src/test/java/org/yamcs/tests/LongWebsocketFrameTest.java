package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.YamcsClient;
import org.yamcs.protobuf.SubscribeParametersData;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tests.AbstractIntegrationTest.MyConnectionListener;

public class LongWebsocketFrameTest {

    @BeforeAll
    public static void beforeClass() throws Exception {
        YConfiguration.setupTest("LongWebsocketFrameTest");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();
    }

    @AfterAll
    public static void shutDownYamcs() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @Test
    public void testWithSmallFrame() {
        assertThrows(TimeoutException.class, () -> {
            runIt(65536);
        });
    }

    @Test
    public void testWithBigFrame() throws Exception {
        try {
            runIt(1024 * 1024);
        } catch (TimeoutException e) {
            fail();
        }
    }

    private void runIt(int maxFrameSize) throws Exception {
        MyConnectionListener connectionListener = new MyConnectionListener();
        YamcsClient client = YamcsClient.newBuilder("localhost", 9191).build();
        client.getWebSocketClient().setMaxFramePayloadLength(maxFrameSize);
        client.getWebSocketClient().setAllowCompression(false);
        client.addConnectionListener(connectionListener);

        try {
            client.connectWebSocket();
            assertTrue(connectionListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));

            SubscribeParametersRequest.Builder requestb = SubscribeParametersRequest.newBuilder()
                    .setAbortOnInvalid(false)
                    .setInstance("LongWebsocketFrameTest")
                    .setProcessor("realtime");
            for (int i = 0; i < 10000; i++) {
                requestb.addId(NamedObjectId.newBuilder().setName("/very/long/parameter/name" + i));
            }

            SubscribeParametersRequest request = requestb.build();
            assertTrue(request.toByteArray().length > 65535);

            ParameterSubscription subscription = client.createParameterSubscription();
            MessageCaptor<SubscribeParametersData> captor = MessageCaptor.of(subscription);
            subscription.sendMessage(request);

            // If our frame is sufficiently large, we should get a first message
            // (all parameters being invalid, is irrelevant to this)
            // Else this will throw a TimeoutException
            captor.expectTimely();
        } finally {
            client.close();
        }
    }
}
