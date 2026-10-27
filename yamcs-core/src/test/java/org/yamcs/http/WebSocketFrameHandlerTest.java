package org.yamcs.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.api.Observer;
import org.yamcs.api.WebSocketTopic;
import org.yamcs.protobuf.AbstractTimeApi;
import org.yamcs.protobuf.CancelOptions;
import org.yamcs.protobuf.LeapSecondsTable;
import org.yamcs.protobuf.ServerMessage;
import org.yamcs.protobuf.SetTimeRequest;
import org.yamcs.protobuf.State;
import org.yamcs.protobuf.SubscribeTimeRequest;
import org.yamcs.security.User;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WebSocketFrameHandlerTest {

    private StubTimeApi api = new StubTimeApi();
    private EmbeddedChannel channel;

    @BeforeEach
    public void setUp() {
        var descriptor = mock(RpcDescriptor.class);
        when(descriptor.getMethod()).thenReturn("SubscribeTime");
        var topic = new Topic(api, WebSocketTopic.newBuilder().setTopic("time").build(), descriptor);

        var httpServer = mock(HttpServer.class);
        when(httpServer.getTopics()).thenReturn(List.of(topic));
        when(httpServer.getJsonParser()).thenReturn(JsonFormat.parser()
                .usingTypeRegistry(TypeRegistry.newBuilder().add(CancelOptions.getDescriptor()).build()));

        channel = new EmbeddedChannel(new WebSocketFrameHandler(httpServer, mock(HttpRequest.class),
                mock(User.class), WriteBufferWaterMark.DEFAULT));
    }

    @Test
    public void testClientCancel() throws Exception {
        int call = subscribe();
        send("{\"type\": \"cancel\", \"options\": {\"call\": " + call + "}}");

        assertTrue(api.observers.get(0).isCancelled());
        assertEquals(0, dumpState().getCallsCount());
    }

    @Test
    public void testServerCompletion() throws Exception {
        subscribe();
        api.observers.get(0).complete();

        assertEquals(0, dumpState().getCallsCount());
    }

    @Test
    public void testSubscribeFails() throws Exception {
        api.fail = true;
        subscribe();

        assertEquals(0, dumpState().getCallsCount());
    }

    @Test
    public void testDisconnectCancelsAllCalls() {
        subscribe();
        subscribe();
        channel.close();

        assertTrue(api.observers.get(0).isCancelled());
        assertTrue(api.observers.get(1).isCancelled());
    }

    private int subscribe() {
        send("{\"type\": \"time\"}");
        ServerMessage reply = channel.readOutbound();
        return reply.getCall();
    }

    private State dumpState() throws InvalidProtocolBufferException {
        send("{\"type\": \"state\"}");
        ServerMessage message = channel.readOutbound();
        return message.getData().unpack(State.class);
    }

    private void send(String json) {
        channel.runPendingTasks();
        channel.writeInbound(new TextWebSocketFrame(json));
        channel.runPendingTasks();
    }

    private static class StubTimeApi extends AbstractTimeApi<Context> {

        List<Observer<Timestamp>> observers = new ArrayList<>();
        boolean fail;

        @Override
        public void getLeapSeconds(Context ctx, Empty request, Observer<LeapSecondsTable> observer) {
        }

        @Override
        public void setTime(Context ctx, SetTimeRequest request, Observer<Empty> observer) {
        }

        @Override
        public void subscribeTime(Context ctx, SubscribeTimeRequest request, Observer<Timestamp> observer) {
            if (fail) {
                throw new NotFoundException();
            }
            observers.add(observer);
        }
    }
}
