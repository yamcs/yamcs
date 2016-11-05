package org.yamcs.ui;


import io.netty.channel.ChannelFuture;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;


/**
 * This is like a WebsocketClient but performs reconnection and implements some callbacks to announce the state.
 * to be used by all the gui data receivers (event viewer, archive browser, etc)
 * 
 * @author nm
 *
 */
public class YamcsConnector implements WebSocketClientCallback {
    CopyOnWriteArrayList<ConnectionListener> connectionListeners=new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<WebSocketClientCallback> subscribers = new CopyOnWriteArrayList<>();
    
    volatile boolean connected, connecting;
    protected WebSocketClient wsClient;
    protected RestClient restClient;
    
    protected YamcsConnectionProperties connectionParams;
    static Logger log= LoggerFactory.getLogger(YamcsConnector.class);
    private boolean retry = true;
    private boolean reconnecting = false;
    final private ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    List<YamcsInstance> instances;
    final String aplicationName;
    
    /**
     * 
     * @param aplicationName - application name that is passed on via web socket (as UserAgent) to be displayed in the YamcsMonitor
     */
    public YamcsConnector(String aplicationName) {
        this(true, aplicationName);
    }
    public YamcsConnector(boolean retry, String aplicationName) {
        this.retry = retry;
        this.aplicationName = aplicationName;
    }


    public void addConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.add(connectionListener);
    }

    public List<String> getYamcsInstances() {
        if(instances==null) return null;
        return instances.stream().map(r-> r.getName()).collect(Collectors.toList());
    }

    public Future<YamcsConnectionProperties> connect(YamcsConnectionProperties cp) {
        System.out.println("connecting to yamcs");
        this.connectionParams = cp;
        return doConnect();
    }

    
    
    private FutureTask<YamcsConnectionProperties> doConnect() {
        if(connected) disconnect();
        
        restClient = new RestClient(connectionParams);
        restClient.setAutoclose(false);
        wsClient = new WebSocketClient(connectionParams, this);
        wsClient.setUserAgent(aplicationName);
        
        FutureTask<YamcsConnectionProperties> future=new FutureTask<>(new Runnable() {
            @Override
            public void run() {
                String connectingTo = connectionParams.getHost()+":"+connectionParams.getPort();
                //connect to yamcs
                int maxAttempts=10;
                try {
                    if(reconnecting && !retry)  {
                        log.warn("Retries are disabled, cancelling reconnection");
                        reconnecting = false;
                        return;
                    }

                    connecting=true;
                   
                    for(ConnectionListener cl:connectionListeners) {
                        cl.connecting(connectingTo);
                    }
                    for(int i=0;i<maxAttempts;i++) {
                        try {
                            log.debug("Connecting to {} attempt {}", connectingTo, i);
                            instances = restClient.blockingGetYamcsInstances();
                            if(instances==null || instances.isEmpty()) {
                                log.warn("No configured yamcs instance");
                                return;
                            }
                            String defaultInstanceName = instances.get(0).getName();
                            String instanceName = defaultInstanceName;
                            if(connectionParams.getInstance()!=null){ //check if the instance saved in properties exists, otherwise use the default one
                                instanceName  = instances.stream().map(yi->yi.getName()).filter(s -> s.equals(connectionParams.getInstance())).findFirst().orElse(defaultInstanceName);
                            }
                            connectionParams.setInstance(instanceName);
                            
                            ChannelFuture future = wsClient.connect();
                            future.get(5000, TimeUnit.MILLISECONDS);
                            //now the TCP connection is established but we have to wait for the websocket to be setup
                            // the connected callback will handle that
                          
                            return;
                        } catch (Exception e) {
                            // For anything other than a security exception, re-try
                            for(ConnectionListener cl:connectionListeners) {
                                cl.log("Connection to "+connectionParams.getHost()+":"+connectionParams.getPort()+" failed :"+e.getMessage());
                            }
                            log.warn("Connection to "+connectionParams.getHost()+":"+connectionParams.getPort()+" failed :", e);
                            Thread.sleep(5000);
                        }
                    }
                    connecting=false;
                    for(ConnectionListener cl:connectionListeners) {
                        cl.log(maxAttempts+" connection attempts failed, giving up.");
                        cl.connectionFailed(connectingTo, new YamcsException( maxAttempts+" connection attempts failed, giving up." ));
                    }
                    log.warn(maxAttempts+" connection attempts failed, giving up.");
                } catch(InterruptedException e){
                    for(ConnectionListener cl:connectionListeners)
                        cl.connectionFailed(connectingTo, new YamcsException( "Thread interrupted", e ));
                }
            };
        }, connectionParams);
        executor.submit(future);
        return future;
    }

    @Override
    public void disconnected() {
        String msg ="Connection to "+connectionParams.getHost()+":"+connectionParams.getPort()+" lost";
        if(connected) log.warn(msg);
        for(ConnectionListener cl:connectionListeners) {
            if(connected) cl.log(msg);
            cl.disconnected();
        }
    }
    
    public void disconnect() {
        log.info("Disconnection requested");
        if(!connected)
            return;
        wsClient.disconnect();
        connected=false;
    }

    public String getUrl() {
        return connectionParams.webSocketURI().toString();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public YamcsConnectionProperties getConnectionParams() {
        return connectionParams;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        for(WebSocketClientCallback client: subscribers) {
            client.onMessage(data);
        }
    
    }
    //called when the websocket has been setup
    @Override
    public void connected() {
        connected=true;
        String connectingTo = connectionParams.getHost()+":"+connectionParams.getPort();
        for(ConnectionListener cl:connectionListeners) {
            cl.connected(connectingTo);
        }
    }

    public WebSocketClient getWebSocketClient() {
        return wsClient;
    }
    public RestClient getRestClient() {
        return restClient;
    }
    
    /**
     * Sends the subscription via websocket and register the client to the subscriber lists
     * 
     * Note that currently Yamcs does not send back the original requestId with the subscription data so it's not possible to send to clients only the data they subscribed to.
     * So the clients have to sort out the data they need and drop the data they don't need.
     * 
     * @param wsr
     * @param client
     * @param wsrh - any error related to the request will be sent here
     */
    public void performSubscription(WebSocketRequest wsr, WebSocketClientCallback client, WebSocketResponseHandler wsrh) {
        if(!subscribers.contains(client)){
            subscribers.add(client);
        }
        wsClient.sendRequest(wsr,  wsrh);
    }
}
