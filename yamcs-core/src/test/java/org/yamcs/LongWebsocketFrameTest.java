package org.yamcs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.AbstractIntegrationTest.MyWsListener;
import org.yamcs.client.WebSocketClient;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.http.HttpServer;
import org.yamcs.protobuf.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class LongWebsocketFrameTest {
    YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9191, "LongWebsocketFrameTest");

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
        Logger.getLogger("org.yamcs.client.WebSocketClientHandler").setLevel(Level.OFF);
    }

    @AfterClass
    public static void shutDownYamcs() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @Test
    public void testWithSmallFrame() throws Exception {
        assertNotNull(testit(65536));
    }

    @Test
    public void testWithBigFrame() throws Exception {
        assertNull(testit(1024 * 1024));
    }

    private TimeoutException testit(int frameSize) throws Exception {
        MyWsListener wsListener = new MyWsListener();
        WebSocketClient wsClient = new WebSocketClient(ycp, wsListener);
        wsClient.enableReconnection(false);
        wsClient.setMaxFramePayloadLength(frameSize);
        wsClient.connect(null);

        assertTrue(wsListener.onConnect.tryAcquire(5, TimeUnit.SECONDS));
        ParameterSubscriptionRequest req = getRequest();

        assertTrue(req.toByteArray().length > 65535);

        WebSocketRequest wsr = new WebSocketRequest("parameter", "subscribe", req);
        TimeoutException timeout = null;
        try {
            wsClient.sendRequest(wsr).get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeout = e;
        }

        wsClient.disconnect();

        return timeout;
    }

    private ParameterSubscriptionRequest getRequest() {

        ParameterSubscriptionRequest.Builder b = ParameterSubscriptionRequest.newBuilder();
        for (int i = 0; i < 10000; i++) {
            b.addId(NamedObjectId.newBuilder().setName("/very/long/parameter/name" + i).build());
        }
        b.setAbortOnInvalid(false);
        return b.build();
    }

}
