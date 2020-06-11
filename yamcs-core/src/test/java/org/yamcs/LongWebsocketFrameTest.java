package org.yamcs;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.AbstractIntegrationTest.MyConnectionListener;
import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.WebSocketClientHandler;
import org.yamcs.http.HttpServer;
import org.yamcs.protobuf.SubscribeParametersData;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class LongWebsocketFrameTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        // LoggingUtils.enableLogging();
        // io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder debug FINE:Encoding WebSocket Frame opCode=2
        // length=328927

        // avoid printing stack traces in the unit tests run
        YConfiguration.setupTest("LongWebsocketFrameTest");
        Map<String, Object> options = new HashMap<>();
        options.put("port", 9191);
        Map<String, Object> wsOptions = new HashMap<>();
        wsOptions.put("maxFrameLength", 1048576);
        options.put("webSocket", wsOptions);
        HttpServer httpServer = new HttpServer();
        options = httpServer.getSpec().validate(options);
        httpServer.init(null, YConfiguration.wrap(options));
        httpServer.startServer();
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();
        Logger.getLogger(WebSocketClientHandler.class.getName()).setLevel(Level.OFF);
    }

    @AfterClass
    public static void shutDownYamcs() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @Test(expected = TimeoutException.class)
    public void testWithSmallFrame() throws Exception {
        runIt(65536);
    }

    @Test
    public void testWithBigFrame() throws Exception {
        try {
            runIt(1024 * 1024);
        } catch (TimeoutException e) {
            fail();
        }
    }

    private void runIt(int frameSize) throws Exception {
        MyConnectionListener connectionListener = new MyConnectionListener();
        YamcsClient client = YamcsClient.newBuilder("localhost", 9191).build();
        client.getWebSocketClient().setMaxFramePayloadLength(frameSize);
        client.addConnectionListener(connectionListener);

        try {
            client.connectAnonymously();
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
