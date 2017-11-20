package org.yamcs.api.artemis;

import java.util.EventListener;

import org.yamcs.YamcsException;

/**
 * A connection listener interface for clients connecting to a yamcs server.
 */
public interface ConnectionListener extends EventListener {

  /**
   * Called right before the initial connection to yamcs is being made.
   * 
   * @param url
   *          the url of the yamcs server
   */
  public void connecting(String url);

  /**
   * Called after a successful connection to the yamcs server has been established.
   * <p>
   * <b>Note:</b> when using a re-connecting YamcsConnector, this method is also called after a
   * successful re-connection attempt.
   * </p>
   * 
   * @param url
   *          the url of the yamcs server
   */
  public void connected(String url);

  /**
   * Called when the initial connection to the yamcs server has failed, e.g. the maximum number of
   * retry attempts has exceeded.
   * 
   * @param url
   *          the url of the yamcs server
   * @param exception
   *          Optional cause of the connection failure, may be null.
   */
  public void connectionFailed(String url, YamcsException exception);

  /**
   * Called when the connection to the yamcs server is closed.
   */
  public void disconnected();

  /**
   * Used to log messages.
   * 
   * @param message
   *          the messages to be logged
   */
  public void log(String message);

}