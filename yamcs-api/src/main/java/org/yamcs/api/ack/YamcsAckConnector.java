package org.yamcs.api.ack;

import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.client.SessionFailureListener;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.YamcsConnectData;


/**
 * Creates and manages the connection to a yamcs server. Supports automatic re-connection and
 * listeners for the current connection state. Also supports client-side message acknowledgment via
 * a {@link ClientAckMessageHandler} interface.
 * 
 * @author Thomas Neidhart
 */
public class YamcsAckConnector implements SessionFailureListener {
  private static final Logger logger = LoggerFactory.getLogger(YamcsAckConnector.class);

  /** the connection parameter details for the yamcs instance */
  private YamcsConnectData connParams;
  /** the instance name */
  private String instance;
  /** the hornetq server locator */
  private ServerLocator locator;
  /** the hornetq session factory */
  private ClientSessionFactory sessionFactory;
  /** the hornetq client session */
  private ClientSession session;

  /** the number of initial connect attempts */
  private int initialConnectAttempts;
  /** the number of reconnect attempts */
  private int reconnectAttempts;
  /** the retry interval in milliseconds */
  private int retryInterval;
  /** the retry interval multiplier */
  private double retryIntervalMultiplier;
  /** the maximum retry interval in milliseconds */
  private int maxRetryInterval;
  /** the acknowledge batch size */
  private int ackBatchSize;
  /** pre acknowledge received messages on the server, set to false for client-side ack */
  private boolean preAcknowledge;

  /** the list of data consumers */
  private List<ClientConsumer> dataConsumers;
  /** the list of connection listeners */
  private List<ConnectionListener> connectionListeners;

  /**
   * Creates a new connector to a yamcs server.
   * 
   * @param yamcsUrl
   *          the url to connect to yamcs
   * @param instance
   *          the yamcs instance to use
   * @throws Exception
   *           if the provided url could not be parsed or contains an invalid hostname
   */
  public YamcsAckConnector(final String yamcsUrl, final String instance) throws Exception {
    this.connParams = YamcsConnectData.parse(yamcsUrl);
    this.instance = instance;

    // default values for reconnection attempts
    initialConnectAttempts = -1;
    reconnectAttempts = -1;
    retryInterval = 3000;
    retryIntervalMultiplier = 2.0d;
    maxRetryInterval = 60000;
    // send acknowledges immediately
    ackBatchSize = 0;
    // do client-side acknowledgement
    preAcknowledge = false;

    dataConsumers = new LinkedList<ClientConsumer>();
    connectionListeners = new LinkedList<ConnectionListener>();

    // check for invalid hostnames, as it may be hidden in hornetq otherwise when the number of
    // reconnection attempts is set to unlimited
    try {
      Socket socket = new Socket(connParams.host, connParams.port);
      socket.close();
    } catch (UnknownHostException e) {
      throw new Exception("invalid hostname in yamcs url: " + yamcsUrl);
    } catch (Exception e) {
      // ignore other exceptions
    }
  }

  /**
   * Add a {@link ConnectionListener} to the connector.
   * 
   * @param listener
   *          the listener to be added
   */
  public void addConnectionListener(final ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  /**
   * Remove a {@link ConnectionListener} from the connector.
   * 
   * @param listener
   *          the listener to be removed
   */
  public void removeConnectionListener(final ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  /**
   * Returns the number of initial connection attempts this connector uses.
   * 
   * @return the number of initial connection attempts
   */
  public int getInitialConnectAttempts() {
    return initialConnectAttempts;
  }

  /**
   * Sets the number of initial connection attempts this connector shall use. A value of
   * <code>-1</code> indicates unlimited attempts.
   * 
   * @param initialConnectAttempts
   *          the number of initial connection attempts
   */
  public void setInitialConnectAttempts(int initialConnectAttempts) {
    this.initialConnectAttempts = initialConnectAttempts;
  }

  /**
   * Returns the number of reconnect attempts this connector uses.
   * 
   * @return the number of reconnect attempts
   */
  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  /**
   * Sets the number of reconnection attempts this connector shall use. A value of <code>-1</code>
   * indicates unlimited attempts.
   * 
   * @param reconnectAttempts
   *          the number of reconnection attempts
   */
  public void setReconnectAttempts(int reconnectAttempts) {
    this.reconnectAttempts = reconnectAttempts;
  }

  /**
   * Returns the retry interval this connector uses.
   * 
   * @return the retry interval in milliseconds
   */
  public int getRetryInterval() {
    return retryInterval;
  }

  /**
   * Sets the retry interval to be used when connecting.
   * 
   * @param retryInterval
   *          the retry interval to be used in milliseconds
   */
  public void setRetryInterval(int retryInterval) {
    this.retryInterval = retryInterval;
  }

  /**
   * Returns the retry interval multiplier this connector uses.
   * 
   * @return the retry interval multiplier
   */
  public double getRetryIntervalMultiplier() {
    return retryIntervalMultiplier;
  }

  /**
   * Sets the retry interval multiplier to be used when connecting.
   * 
   * @param retryIntervalMultiplier
   *          the retry interval multiplier to be used
   */
  public void setRetryIntervalMultiplier(double retryIntervalMultiplier) {
    this.retryIntervalMultiplier = retryIntervalMultiplier;
  }

  /**
   * Returns the maximum retry interval this connector uses.
   * 
   * @return the maximum retry interval
   */
  public int getMaxRetryInterval() {
    return maxRetryInterval;
  }

  /**
   * Sets the maximum retry interval to be used when connecting.
   * 
   * @param maxRetryInterval
   *          the maximum retry interval to be used in milliseconds
   */
  public void setMaxRetryInterval(int maxRetryInterval) {
    this.maxRetryInterval = maxRetryInterval;
  }

  /**
   * Returns the acknowledge batch size in bytes.
   * 
   * @return the acknowledge batch size
   */
  public int getAckBatchSize() {
    return ackBatchSize;
  }

  /**
   * Sets the acknowledgment batch size to be used when acknowledging received messages. A value of
   * <code>0</code> indicates that acknowledge messages are sent immediately.
   * 
   * @param ackBatchSize
   *          the batch size in bytes
   */
  public void setAckBatchSize(int ackBatchSize) {
    this.ackBatchSize = ackBatchSize;
  }

  /**
   * Returns the pre-acknowledge mode for this connector.
   * 
   * @return <code>true</code> if pre-acknowledge is active, <code>false</code> otherwise
   */
  public boolean isPreAcknowledge() {
    return preAcknowledge;
  }

  /**
   * Set the pre-acknowledge mode for the client session. If pre-acknowledge is activated, the
   * messages are already acknowledged on the server side, otherwise they have to be acknowledged on
   * the client side.
   * 
   * @param preAcknowledge
   *          the pre-acknowledge mode to be used
   */
  public void setPreAcknowledge(boolean preAcknowledge) {
    this.preAcknowledge = preAcknowledge;
  }

  /**
   * Connects to the yamcs server. This method blocks until a connection is established or some
   * error has occurred, thus this should be called in a separate thread. Hornetq will try
   * indefinitely to establish the connection and also provides automatic re-connection afterwards.
   * 
   * @throws Exception
   *           if the hornetq session could not be established due to some error
   */
  public void connect() throws Exception {
    for (ConnectionListener cl : connectionListeners) {
      cl.connecting(connParams.getUrl());
    }

    Map<String, Object> tcpConfig = new HashMap<String, Object>();
    tcpConfig.put(TransportConstants.HOST_PROP_NAME, connParams.host);
    tcpConfig.put(TransportConstants.PORT_PROP_NAME, connParams.port);

    locator =
      HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(
          NettyConnectorFactory.class.getName(), tcpConfig));

    locator.setInitialConnectAttempts(initialConnectAttempts);
    locator.setReconnectAttempts(reconnectAttempts);
    locator.setRetryInterval(retryInterval);
    locator.setRetryIntervalMultiplier(retryIntervalMultiplier);
    locator.setMaxRetryInterval(maxRetryInterval);
    locator.setAckBatchSize(ackBatchSize);

    sessionFactory = locator.createSessionFactory();

    // TODO Use hornetq auth (like YamcsConnector), or keep anonymous connection?
    session = sessionFactory.createSession(false, true, true, preAcknowledge);
    session.addFailureListener(YamcsAckConnector.this);
    session.start();

    for (ConnectionListener cl : connectionListeners) {
      cl.connected(connParams.getUrl());
    }
  }

  /**
   * Add a consumer for a given queue at the yamcs server. The final queue address will be composed
   * in the following manner: "instance.queueName", where the instance was provided when creating
   * this connector.
   * 
   * @param queueName
   *          the name of the queue
   * @param handler
   *          the handler used to process receiving messages
   * @throws HornetQException
   *           if anything went wrong, e.g. the queue is not available
   */
  public void addConsumer(final String queueName, final MessageHandler handler)
      throws HornetQException {
    addConsumer(queueName, false, handler);
  }

  /**
   * Add a consumer for a given queue at the yamcs server. The final queue address will be composed
   * in the following manner: "instance.queueName", where the instance was provided when creating
   * this connector.
   * 
   * @param queueName
   *          the name of the queue
   * @param temporaryQueue
   *          if <code>true</code>, a new temporary queue for the given address is created 
   * @param handler
   *          the handler used to process receiving messages
   * @throws HornetQException
   *           if anything went wrong, e.g. the queue is not available
   */
  public void addConsumer(final String queueName, boolean temporaryQueue,
      final MessageHandler handler) throws HornetQException {
    SimpleString address = new SimpleString(String.format("%1$s.%2$s", instance, queueName));
    
    if (temporaryQueue) {
      SimpleString name = new SimpleString("tempDataQueue." + UUID.randomUUID().toString());

      // create a temporary queue with this name
      if (!session.queueQuery(name).isExists()) {
        session.createTemporaryQueue(address, name);
      }
      
      // use the name as new address when creating the consumer
      address = name;
    }

    ClientConsumer consumer = session.createConsumer(address, false);
    consumer.setMessageHandler(handler);
    dataConsumers.add(consumer);
  }

  /**
   * Cleans up all resources that are still in use.
   */
  public synchronized void close() {
    for (ClientConsumer consumer : dataConsumers) {
      try {
        consumer.close();
      } catch (HornetQException e) {
        // ignore exception
      }
    }

    dataConsumers.clear();

    try {
      if (session != null) {
        session.close();
      }
    } catch (Exception e) {
      // ignore exception
    } finally {
      if (sessionFactory != null) {
        sessionFactory.close();
      }
      if (locator != null) {
        locator.close();
      }
    }

    for (ConnectionListener cl : connectionListeners) {
      cl.disconnected();
    }
  }

  public void connectionFailed(HornetQException e, boolean failedOver) {
    if (failedOver) {
      logger.info("reconnected to yamcs: {}", e.getMessage());
      for (ConnectionListener cl : connectionListeners) {
        cl.connected(connParams.getUrl());
      }
    } else {
      logger.warn("connection to yamcs failed: {}", e.getMessage());
      for (ConnectionListener cl : connectionListeners) {
        cl.connectionFailed(connParams.getUrl(), new YamcsException( e.getMessage(), e ));
        cl.log(e.getMessage());
      }
    }
  }

  public void beforeReconnect(HornetQException e) {
    logger.warn("disconnected from yamcs: {}", e.getMessage());
    for (ConnectionListener cl : connectionListeners) {
      cl.disconnected();
      cl.log(e.getMessage());
    }

    // clear all pending messages in the data consumers, as they will be re-submitted
    // by yamcs upon re-connect
    for (ClientConsumer consumer : dataConsumers) {
      try {
        MessageHandler handler = consumer.getMessageHandler();
        if (handler instanceof ClientAckMessageHandler) {
          ((ClientAckMessageHandler) handler).clearPendingMessages();
        }
      } catch (HornetQException e1) {
        // ignore
      }
    }
  }
}