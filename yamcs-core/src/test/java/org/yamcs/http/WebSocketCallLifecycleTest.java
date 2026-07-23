package org.yamcs.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.api.Api;
import org.yamcs.api.Observer;
import org.yamcs.protobuf.ClientMessage;
import org.yamcs.protobuf.ServerMessage;
import org.yamcs.protobuf.State;
import org.yamcs.security.User;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.ImmediateEventExecutor;

public class WebSocketCallLifecycleTest {

    @Test
    public void testStateDumpOnlyIncludesActiveCalls() throws Exception {
        WebSocketFrameHandler handler = newHandler();
        TopicContext active = newTopicContext("parameters");
        TopicContext cancelled = newTopicContext("time");
        TopicContext completed = newTopicContext("events");

        cancelled.close();
        completed.requestFuture.complete(null);

        activeContexts(handler).add(active);
        activeContexts(handler).add(cancelled);
        activeContexts(handler).add(completed);

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        dumpState(handler, channel.pipeline().firstContext());

        ServerMessage serverMessage = channel.readOutbound();
        State state = serverMessage.getData().unpack(State.class);
        assertEquals(1, state.getCallsCount());
        assertEquals(active.getId(), state.getCalls(0).getCall());

        channel.finishAndReleaseAll();
    }

    @Test
    public void testCancelRemovesContextFromActiveCalls() throws Exception {
        WebSocketFrameHandler handler = newHandler();
        TopicContext ctx = newTopicContext("parameters");

        activeContexts(handler).add(ctx);
        clientObservers(handler).put(ctx.getId(), mockObserver());

        cancelCall(handler, ctx.getId());

        assertTrue(ctx.isCancelled());
        assertFalse(activeContexts(handler).contains(ctx));
        assertFalse(clientObservers(handler).containsKey(ctx.getId()));
    }

    @Test
    public void testObserverCompletionCompletesRequestFuture() {
        TopicContext ctx = newTopicContext("parameters");
        WebSocketObserver observer = new WebSocketObserver(ctx);

        observer.complete();

        assertTrue(ctx.isDone());
    }

    @Test
    public void testObserverExceptionalCompletionCompletesRequestFuture() {
        TopicContext ctx = newTopicContext("parameters");
        WebSocketObserver observer = new WebSocketObserver(ctx);

        observer.completeExceptionally(new RuntimeException("boom"));

        assertTrue(ctx.isDone());
    }

    private static WebSocketFrameHandler newHandler() {
        return new WebSocketFrameHandler(mockHttpServer(), mock(HttpRequest.class), mock(User.class),
                WriteBufferWaterMark.DEFAULT);
    }

    private static TopicContext newTopicContext(String type) {
        return new TopicContext(mockHttpServer(), mockNettyContext(), mock(User.class),
                ClientMessage.newBuilder().setType(type).build(), mockTopic(type));
    }

    private static HttpServer mockHttpServer() {
        HttpServer httpServer = mock(HttpServer.class);
        when(httpServer.getReverseLookup()).thenReturn(false);
        when(httpServer.getJsonParser()).thenReturn(JsonFormat.parser());
        when(httpServer.getJsonPrinter()).thenReturn(JsonFormat.printer());
        return httpServer;
    }

    private static ChannelHandlerContext mockNettyContext() {
        ChannelHandlerContext nettyContext = mock(ChannelHandlerContext.class);
        when(nettyContext.executor()).thenReturn(ImmediateEventExecutor.INSTANCE);
        return nettyContext;
    }

    private static Topic mockTopic(String name) {
        Topic topic = mock(Topic.class);
        when(topic.getName()).thenReturn(name);
        when(topic.getApi()).thenReturn(mockApi());
        return topic;
    }

    @SuppressWarnings("unchecked")
    private static Api<Context> mockApi() {
        return mock(Api.class);
    }

    @SuppressWarnings("unchecked")
    private static Observer<Message> mockObserver() {
        return mock(Observer.class);
    }

    private static void cancelCall(WebSocketFrameHandler handler, int callId) throws Exception {
        Method method = WebSocketFrameHandler.class.getDeclaredMethod("cancelCall", ChannelHandlerContext.class,
                int.class);
        method.setAccessible(true);
        method.invoke(handler, mockNettyContext(), callId);
    }

    private static void dumpState(WebSocketFrameHandler handler, ChannelHandlerContext nettyContext) throws Exception {
        Method method = WebSocketFrameHandler.class.getDeclaredMethod("dumpState", ChannelHandlerContext.class);
        method.setAccessible(true);
        method.invoke(handler, nettyContext);
    }

    @SuppressWarnings("unchecked")
    private static List<TopicContext> activeContexts(WebSocketFrameHandler handler) throws Exception {
        Field field = WebSocketFrameHandler.class.getDeclaredField("contexts");
        field.setAccessible(true);
        return (List<TopicContext>) field.get(handler);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Observer<Message>> clientObservers(WebSocketFrameHandler handler) throws Exception {
        Field field = WebSocketFrameHandler.class.getDeclaredField("clientObserversByCall");
        field.setAccessible(true);
        return (Map<Integer, Observer<Message>>) field.get(handler);
    }
}
