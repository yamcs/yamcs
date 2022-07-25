package org.yamcs.http;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.protobuf.ClientMessage;
import org.yamcs.protobuf.State.CallInfo;
import org.yamcs.security.User;

import com.google.protobuf.Descriptors.MethodDescriptor;

import io.netty.channel.ChannelHandlerContext;

/**
 * Context for a specific RPC call over a (shared) WebSocket connection.
 */
public class TopicContext extends Context {

    private final ClientMessage clientMessage;
    private final Topic topic;

    private boolean cancelled;
    private Throwable cancellationCause;

    private Set<ContextListener> listeners = new HashSet<>();

    TopicContext(HttpServer httpServer, ChannelHandlerContext nettyContext, User user, ClientMessage clientMessage,
            Topic topic) {
        super(httpServer, nettyContext, user, topic.getApi());
        this.clientMessage = clientMessage;
        this.topic = topic;
    }

    @Override
    public MethodDescriptor getMethod() {
        String methodName = topic.getDescriptor().getMethod();
        return api.getDescriptorForType().findMethodByName(methodName);
    }

    public void addListener(ContextListener listener) {
        listeners.add(listener);
    }

    public synchronized boolean cancel(Throwable cause) {
        if (!cancelled) {
            cancelled = true;
            cancellationCause = cause;
            listeners.forEach(l -> l.onCancel(cause));
            return true;
        }

        return false;
    }

    public Throwable getCancellationCause() {
        return cancellationCause;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public Topic getTopic() {
        return topic;
    }

    public boolean isLowPriority() {
        return clientMessage.getLowPriority();
    }

    public void close() {
        cancel(null);
    }

    public CallInfo dumpState() {
        CallInfo.Builder callb = CallInfo.newBuilder()
                .setType(clientMessage.getType())
                .setCall(getId());
        if (clientMessage.hasOptions()) {
            callb.setOptions(clientMessage.getOptions());
        }
        return callb.build();
    }
}
