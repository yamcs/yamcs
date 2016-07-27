package org.yamcs.api.ack;

import java.util.LinkedList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for message handlers that have to acknowledge received messages manually.
 * 
 * In case a message can not be acknowledge, e.g. due to some processing error, the message is kept
 * in a pending list, and processing is re-tried the next time a new message is received.
 * <p>
 * <b>Note:</b> To ensure proper delivery and processing of messages the client session to yamcs
 * should be configured to <b>not</b> pre-acknowledge messages. In that way, non-acknowledged
 * messages are kept by the server until the client acknowledges them and will be re-delivered upon
 * re-connect.
 * </p>
 * 
 * @author Thomas Neidhart
 * @see YamcsAckConnector
 * @see ClientAckMessageHandler
 */
public abstract class AbstractClientAckMessageHandler implements ClientAckMessageHandler {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private List<ClientMessage> pendingMessages;

  protected AbstractClientAckMessageHandler() {
    pendingMessages = new LinkedList<ClientMessage>();
  }

  /**
   * {@inheritDoc}
   */
  public void clearPendingMessages() {
    pendingMessages.clear();
  }

  /**
   * {@inheritDoc}
   */
  public void onMessage(ClientMessage message) {
    // first try to handle all pending messages
    while (pendingMessages.size() > 0) {
      ClientMessage m = pendingMessages.remove(0);

      m.getBodyBuffer().markReaderIndex();
      boolean success = handleMessage(m);

      // if the transfer was not successful, add the message again to the
      // list, and stop processing the pending messages

      // Rationale:
      // in case an error accumulates over a longer period of time
      // the list will grow bigger and bigger, and continue processing
      // the messages in case of a error will just create the same error
      // multiple times.
      //
      // If the processing works, all messages will be processed in one sweep
      if (!success) {
        m.getBodyBuffer().resetReaderIndex();
        pendingMessages.add(0, m);
        break;
      } else {
        try {
          m.acknowledge();
        } catch (ActiveMQException e) {
          logger.warn("failed to ack message received from archive, ignoring");
        }
      }
    }

    message.getBodyBuffer().markReaderIndex();
    boolean success = handleMessage(message);
    if (!success) {
      message.getBodyBuffer().resetReaderIndex();
      pendingMessages.add(message);
    } else {
      try {
        message.acknowledge();
      } catch (ActiveMQException e) {
        logger.warn("failed to ack message received from archive, ignoring");
      }
    }

    logger.debug("size of pending messages = " + pendingMessages.size());
  }
}
