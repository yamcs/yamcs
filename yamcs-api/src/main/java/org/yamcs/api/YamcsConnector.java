package org.yamcs.api;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.api.core.client.SessionFailureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;


/**
 * this is like a yamcssession but performs reconnection and implements some callbacks to announce the state.
 * to be used by all the gui data receivers (event viewer, archive browser, etc)
 * @author nm
 *
 */
public class YamcsConnector implements SessionFailureListener {
    CopyOnWriteArrayList<ConnectionListener> connectionListeners=new CopyOnWriteArrayList<ConnectionListener>();
    volatile boolean connected, connecting;
    protected YamcsSession yamcsSession;
    protected YamcsConnectData connectionParams;
    static Logger log= LoggerFactory.getLogger(YamcsConnector.class);
    private boolean retry = true;
    private boolean reconnecting = false;
    final private ExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public YamcsConnector() {}
    public YamcsConnector(boolean retry) {
        this.retry = retry;
    }


    public void addConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.add(connectionListener);
    }

    public List<String> getYamcsInstances() {
        try{
            YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(connectionParams).build();
            YamcsClient mc=ys.newClientBuilder().setRpc(true).build();
            YamcsInstances ainst=(YamcsInstances)mc.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getYamcsInstances", null, YamcsInstances.newBuilder());
            mc.close();
            List<String> instances=new ArrayList<String>(ainst.getInstanceCount());
            for(YamcsInstance ai:ainst.getInstanceList()) {
                instances.add(ai.getName());
            }
            ys.close();
            return instances;
        } catch ( HornetQException hqe ) {
            // If we don't have permissions, treat as a failed connection
            if( hqe.getType() == HornetQExceptionType.SECURITY_EXCEPTION || hqe.getType() == HornetQExceptionType.SESSION_CREATION_REJECTED ) {
                String message = "Connection failed with security exception: " + hqe.getMessage();
                log.warn( message );
                if( connected ) {
                    disconnect();
                }
                for(ConnectionListener cl:connectionListeners)
                    cl.connectionFailed(connectionParams.getUrl(), new YamcsException( message ));
            } else {
                // Other errors may not be fatal
                for(ConnectionListener cl:connectionListeners)
                    cl.log("failed to retrieve instances: "+hqe);
            }
        } catch (Exception e) {
            for(ConnectionListener cl:connectionListeners)
                cl.log("failed to retrieve instances: "+e);
        }
        return null;
    }

    public Future<String> connect(YamcsConnectData cp) {
        this.connectionParams=cp;
        return doConnect();
    }

    private FutureTask<String> doConnect() {
        if(connected) disconnect();
        final String url=connectionParams.getUrl();

        FutureTask<String> future=new FutureTask<String>(new Runnable() {
            @Override
            public void run() {

                //connect to yamcs
                int maxAttempts=10;
                try {
                    if(reconnecting && !retry)
                    {
                        log.warn("Retries are disabled, cancelling reconnection");
                        reconnecting = false;
                        return;
                    }

                    connecting=true;
                    for(ConnectionListener cl:connectionListeners) {
                        cl.connecting(url);
                    }
                    for(int i=0;i<maxAttempts;i++) {
                        try {
                            log.debug("Connecting to {} attempt {}", url, i);
                            yamcsSession=YamcsSession.newBuilder().setConnectionParams(connectionParams).build();
                            log.debug("Connection successful");
                            yamcsSession.session.addFailureListener(YamcsConnector.this);
                            connected=true;
                            for(ConnectionListener cl:connectionListeners) {
                                cl.connected(url);
                            }
                            return;
                        } catch (YamcsApiException e) {
                            // If we don't have permissions, treat as a failed connection and don't re-try
                            Throwable cause = e.getCause();
                            if( cause != null && cause instanceof HornetQException && ((HornetQException)cause).getType() == HornetQExceptionType.SECURITY_EXCEPTION ) {
                                String message = "Connection failed with security exception: " + e.getMessage();
                                log.warn( message );
                                if( connected ) {
                                    disconnect();
                                }
                                for(ConnectionListener cl:connectionListeners) {
                                    cl.connectionFailed(url, new YamcsException( message ));
                                }
                                return;
                            }

                            // For anything other than a security exception, re-try
                            for(ConnectionListener cl:connectionListeners) {
                                cl.log("Connection to "+url+" failed :"+e.getMessage());
                            }
                            log.warn("Connection to "+url+" failed :!", e);
                            Thread.sleep(5000);
                        }
                    }
                    connecting=false;
                    for(ConnectionListener cl:connectionListeners) {
                        cl.log(maxAttempts+" connection attempts failed, giving up.");
                        cl.connectionFailed(url, new YamcsException( maxAttempts+" connection attempts failed, giving up." ));
                    }
                    log.warn(maxAttempts+" connection attempts failed, giving up.");
                } catch(InterruptedException e){
                    for(ConnectionListener cl:connectionListeners)
                        cl.connectionFailed(url, new YamcsException( "Thread interrupted", e ));
                }
            };
        }, url);
        executor.submit(future);
        return future;
    }

    public void disconnect() {
        log.warn("Disconnection requested");
        if(!connected)
            return;

        try {
            yamcsSession.close();
            connected=false;
        } catch (HornetQException e) {
            for(ConnectionListener cl:connectionListeners)
                cl.log(e.toString());
        }
    }

    public String getUrl() {
        return connectionParams.getUrl();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    @Override
    public void connectionFailed(HornetQException e, boolean failedOver) {
        connected=false;
        for(ConnectionListener cl:connectionListeners) {
            cl.disconnected();
        }
        log.warn("Connection to Yamcs lost: ", e);
        doConnect();
    }

    @Override
    public void beforeReconnect(HornetQException e) {
        //should not be called because reconnection is not configured in the factory
        //log.warn("Before reconnect: ", creatorContext);
        log.warn("Before reconnect: ", e);
        reconnecting = true;
    }

    public YamcsSession getSession() {
        return yamcsSession;
    }

    public YamcsConnectData getConnectionParams() {
        return connectionParams;
    }

    public void close() throws HornetQException {
        yamcsSession.close();
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
