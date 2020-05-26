package org.yamcs.client;

import java.util.concurrent.Future;

import com.google.protobuf.Message;

/**
 * Top-level interface for any topic subscriptions to Yamcs that make use of the WebSocket API.
 * <p>
 * Topics are capable of bi-directional communication, where each topic has only a single client and server message
 * type. In practice most topics are one-directional, with the client issuing a single subscription request.
 * <p>
 * A topic subscription is usually long-running. The server keeps pushing updates until the client cancels the call, or
 * closes the connection. Instances of this class are also futures covering the lifecycle of the call (or any error
 * replies to sent message).
 * <p>
 * This base class adds general listener support for receiving the unprocessed data messages. Specific implementations
 * of this class sometimes add more customized functionalities, such as polling or processing.
 * 
 * @param <C>
 *            The client message
 * @param <S>
 *            The server message
 */
public interface Subscription<C extends Message, S extends Message> extends Future<Void> {

    /**
     * Get updated on received server messages.
     */
    void addMessageListener(MessageListener<S> listener);

    /**
     * Sends a message to Yamcs. Note that most topic subscriptions support only a single message to be sent.
     */
    void sendMessage(C message);
}
