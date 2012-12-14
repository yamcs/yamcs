package org.yamcs.api.ack;

import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

/**
 * Yamcs-specific {@link MessageHandler} that supports client-side acknowledgment of messages
 * received from yamcs.
 * 
 * @author Thomas Neidhart
 */
public interface ClientAckMessageHandler extends MessageHandler {
  /**
   * Processes a message. In case the message could not be successfully processed, it will be kept
   * and re-processed at a later time.
   * 
   * @param message
   *          the received message
   * @return <code>true</code> if the message could be processed successfully, <code>false</code>
   *         otherwise
   */
  public boolean handleMessage(final ClientMessage message);

  /**
   * Clears all pending messages. This method has to be called when the session to the messaging
   * server is re-established, as the server will re-send all pending messages himself.
   */
  public void clearPendingMessages();
}
