package org.yamcs.client;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import org.yamcs.api.Observer;
import org.yamcs.client.SpnegoUtils.SpnegoException;
import org.yamcs.protobuf.ConnectionInfo;
import org.yamcs.protobuf.SubscribeTimeRequest;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.YamcsInstance;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Timestamp;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;

public class YamcsClient {

    private static final int MAX_FRAME_PAYLOAD_LENGTH = 10 * 1024 * 1024;
    private static final Logger log = Logger.getLogger(YamcsClient.class.getName());

    private final String host;
    private final int port;
    private boolean tls;

    private String initialInstance;
    private boolean requireInitialInstance;
    private boolean exactInitialInstance;

    private int connectionAttempts;
    private long retryDelay;

    private final RestClient restClient;
    private final WebSocketClient websocketClient;
    private final WebSocketClient2 websocketClient2;

    private volatile boolean connected;
    private volatile boolean closed = false;

    // Latch that resets when the connection handshake protocol was finished
    // (that is: initial ConnectionInfo is set)
    private CountDownLatch connectionDone;

    private volatile ConnectionInfo connectionInfo;

    private List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private List<WebSocketClientCallback> subscribers = new CopyOnWriteArrayList<>();

    private YamcsClient(String host, int port, boolean tls, String initialInstance, boolean requireInitialInstance,
            boolean exactInitialInstance, int connectionAttempts, long retryDelay) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.initialInstance = initialInstance;
        this.requireInitialInstance = requireInitialInstance;
        this.exactInitialInstance = exactInitialInstance;
        this.connectionAttempts = connectionAttempts;
        this.retryDelay = retryDelay;

        YamcsConnectionProperties yprops = new YamcsConnectionProperties();
        yprops.setHost(host);
        yprops.setPort(port);
        yprops.setTls(tls);
        restClient = new RestClient(yprops);
        restClient.setAutoclose(false);

        websocketClient = new WebSocketClient(null, new WebSocketCallbackHandler());
        websocketClient.enableReconnection(false);
        websocketClient.setMaxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH);

        websocketClient2 = new WebSocketClient2(host, port, tls, new WebSocketClient2Callback() {
        });
        websocketClient2.setMaxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH);
    }

    public static Builder newBuilder(String host, int port) {
        return new Builder(host, port);
    }

    /**
     * Establish a live communication channel without logging in to the server.
     */
    public synchronized ConnectionInfo connectAnonymously() throws ClientException {
        return connect(null, false);
    }

    public synchronized ConnectionInfo connectWithKerberos() throws ClientException {
        pollServer();
        try {
            String authorizationCode = SpnegoUtils.fetchAuthenticationCode(host, port, tls);
            restClient.loginWithAuthorizationCode(authorizationCode);
        } catch (SpnegoException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            throw new UnauthorizedException();
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            throw e;
        }
        String accessToken = restClient.httpClient.getCredentials().getAccessToken();
        return connect(accessToken, true);
    }

    /**
     * Login to the server with user/password credential, and establish a live communication channel.
     */
    public synchronized ConnectionInfo connect(String username, char[] password) throws ClientException {
        pollServer();
        try {
            restClient.login(username, password);
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            throw e;
        }
        String accessToken = restClient.httpClient.getCredentials().getAccessToken();
        return connect(accessToken, true);
    }

    /**
     * Polls the server, to see if it is ready.
     */
    public synchronized void pollServer() throws ClientException {
        for (int i = 0; i < connectionAttempts; i++) {
            try {
                // Use an endpoint that does not require auth
                restClient.doBaseRequest("/auth", HttpMethod.GET, null).get(5, TimeUnit.SECONDS);
                return; // Server up!
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UnauthorizedException) {
                    for (ConnectionListener cl : connectionListeners) {
                        cl.log("Connection to " + host + ":" + port + " failed: " + cause.getMessage());
                        cl.connectionFailed(host + ":" + port, (UnauthorizedException) cause);
                    }
                    log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", cause);
                    throw (UnauthorizedException) cause; // Jump out
                } else {
                    for (ConnectionListener cl : connectionListeners) {
                        cl.log("Connection to " + host + ":" + port + " failed: " + cause.getMessage());
                    }
                    log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", cause);
                }
            } catch (TimeoutException e) {
                for (ConnectionListener cl : connectionListeners) {
                    cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
                }
                log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                for (ConnectionListener cl : connectionListeners) {
                    cl.connectionFailed(host + ":" + port, new ClientException("Thread interrupted", e));
                }
            }

            if (i + 1 < connectionAttempts) {
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        connectionDone = null;
        if (connectionAttempts > 1) {
            ClientException e = new ClientException(connectionAttempts + " connection attempts failed, giving up.");
            for (ConnectionListener cl : connectionListeners) {
                cl.log(connectionAttempts + " connection attempts failed, giving up.");
                cl.connectionFailed(host + ":" + port, e);
            }
            log.log(Level.WARNING, connectionAttempts + " connection attempts failed, giving up.");
            throw e;
        } else {
            throw new ClientException("Server is not available");
        }
    }

    /**
     * Establish a live communication channel using a previously acquired access token.
     */
    public synchronized ConnectionInfo connect(String accessToken, boolean bypassUpCheck) throws ClientException {
        if (!bypassUpCheck) {
            pollServer();
        }

        connectionDone = new CountDownLatch(1);

        for (ConnectionListener cl : connectionListeners) {
            cl.connecting(host + ":" + port);
        }

        YamcsConnectionProperties yprops = new YamcsConnectionProperties();
        yprops.setHost(host);
        yprops.setPort(port);
        yprops.setTls(tls);

        if (initialInstance != null) {
            if (exactInitialInstance) {
                yprops.setInstance(initialInstance);
            } else {
                String autoInstance = negotiateInitialInstance(initialInstance);
                yprops.setInstance(autoInstance);
            }
        } else if (requireInitialInstance) {
            String autoInstance = negotiateInitialInstance(null);
            yprops.setInstance(autoInstance);
        }
        websocketClient.setConnectionProperties(yprops);
        try {
            websocketClient.connect(accessToken).get(5000, TimeUnit.MILLISECONDS);
            websocketClient2.connect(accessToken).get(5000, TimeUnit.MILLISECONDS);
            // now the TCP connection is established but we have to wait for the websocket protocol to
            // finish its initial setup.

            connectionDone.await();
            return connectionInfo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (SSLException | GeneralSecurityException | TimeoutException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            connectionDone = null;
            throw new ClientException("Cannot connect WebSocket client", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + cause.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", cause);
            connectionDone = null;
            if (cause instanceof WebSocketHandshakeException && cause.getMessage().contains("401")) {
                throw new UnauthorizedException();
            } else if (cause instanceof ClientException) {
                throw (ClientException) cause;
            } else {
                throw new ClientException(cause);
            }
        }
    }

    private String negotiateInitialInstance(String preferredInstance) throws ClientException {
        List<YamcsInstance> instances = restClient.blockingGetYamcsInstances();
        if (instances.isEmpty()) {
            throw new ClientException("No instance named '" + preferredInstance + "'");
        }

        return instances.stream().map(yi -> yi.getName())
                .filter(s -> s.equals(preferredInstance))
                .findFirst()
                .orElse(instances.get(0).getName());
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connectionDone != null && connectionDone.getCount() > 0;
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.add(connectionListener);
    }

    public void addWebSocketListener(WebSocketClientCallback websocketListener) {
        if (!subscribers.contains(websocketListener)) {
            subscribers.add(websocketListener);
        }
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public WebSocketClient getWebSocketClient() {
        return websocketClient;
    }

    public WebSocketClient2 getWebSocketClient2() {
        return websocketClient2;
    }

    public String getUrl() {
        if (tls) {
            return String.format("https://%s:%s", host, port);
        } else {
            return String.format("http://%s:%s", host, port);
        }
    }

    public Observer<SubscribeTimeRequest> subscribeTime(Observer<Timestamp> observer) {
        return websocketClient2.call("time", new DataObserver<Timestamp>() {

            @Override
            public Class<Timestamp> getMessageClass() {
                return Timestamp.class;
            }

            @Override
            public void next(Timestamp message) {
                observer.next(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void complete() {
                observer.complete();
            }
        });
    }

    public CompletableFuture<byte[]> get(String uri) {
        return requestAsync(HttpMethod.GET, uri, null);
    }

    public CompletableFuture<byte[]> get(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.GET, uri, msg);
    }

    public CompletableFuture<Void> streamGet(String uri, MessageLite msg, BulkRestDataReceiver receiver) {
        return doRequestWithDelimitedResponse(HttpMethod.GET, uri, msg, receiver);
    }

    public CompletableFuture<Void> streamPost(String uri, MessageLite msg, BulkRestDataReceiver receiver) {
        return doRequestWithDelimitedResponse(HttpMethod.POST, uri, msg, receiver);
    }

    public CompletableFuture<byte[]> post(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.POST, uri, msg);
    }

    public CompletableFuture<byte[]> patch(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.PATCH, uri, msg);
    }

    public CompletableFuture<byte[]> put(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.PUT, uri, msg);
    }

    public CompletableFuture<byte[]> delete(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.DELETE, uri, msg);
    }

    private <S extends MessageLite> CompletableFuture<byte[]> requestAsync(HttpMethod method, String uri,
            MessageLite requestBody) {
        if (requestBody != null) {
            return restClient.doRequest(uri, method, requestBody.toByteArray());
        } else {
            return restClient.doRequest(uri, method);
        }
    }

    private <S extends MessageLite> CompletableFuture<Void> doRequestWithDelimitedResponse(HttpMethod method,
            String uri, MessageLite requestBody, BulkRestDataReceiver receiver) {
        if (requestBody != null) {
            return restClient.doBulkRequest(method, uri, requestBody.toByteArray(), receiver);
        } else {
            return restClient.doBulkRequest(method, uri, receiver);
        }
    }

    public CompletableFuture<WebSocketReplyData> sendWebSocketMessage(WebSocketRequest req) {
        return websocketClient.sendRequest(req);
    }

    /**
     * Sends the subscription via websocket and register the client to the subscriber lists
     * 
     * Note that currently Yamcs does not send back the original requestId with the subscription data so it's not
     * possible to send to clients only the data they subscribed to. So the clients have to sort out the data they need
     * and drop the data they don't need.
     * 
     * @param request
     * @param callbackHandler
     * @param responseHandler
     *            any error related to the request will be sent here
     */
    public void performSubscription(WebSocketRequest request, WebSocketClientCallback callbackHandler,
            WebSocketResponseHandler responseHandler) {
        addWebSocketListener(callbackHandler);
        websocketClient.sendRequest(request, responseHandler);
    }

    public CompletableFuture<WebSocketReplyData> performSubscription(WebSocketRequest request,
            WebSocketClientCallback callbackHandler) {
        addWebSocketListener(callbackHandler);
        return websocketClient.sendRequest(request);
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connected) {
            websocketClient.disconnect();
            websocketClient2.disconnect();
        }
        restClient.close();
        websocketClient.shutdown();
        websocketClient2.shutdown();
    }

    private class WebSocketCallbackHandler implements WebSocketClientCallback {

        @Override
        public void disconnected() {
            if (!closed) {
                String msg = String.format("Connection to %s:%s lost", host, port);
                connectionListeners.forEach(l -> {
                    l.log(msg);
                });
                log.warning(msg);
            }
            connected = false;
            connectionInfo = null;
            connectionListeners.forEach(l -> {
                l.disconnected();
            });
            subscribers.forEach(s -> s.disconnected());
        }

        @Override
        public void onMessage(WebSocketSubscriptionData data) {
            if (data.hasConnectionInfo()) {
                connectionInfo = data.getConnectionInfo();
                if (!connected) {
                    connected = true;
                    connectionDone.countDown();
                    connectionListeners.forEach(l -> l.connected(host + ":" + port));
                    subscribers.forEach(s -> s.connected());
                }
            }

            subscribers.forEach(s -> s.onMessage(data));
        }
    }

    public static class Builder {

        private String host;
        private int port;
        private boolean tls;
        private boolean verifyTls;
        private Path caCertFile;
        private String userAgent;

        private String initialInstance;
        private boolean requireInitialInstance;
        private boolean exactInitialInstance;
        private int connectionAttempts = 1;
        private long retryDelay = 5000;

        private Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public Builder withTls(boolean tls) {
            this.tls = tls;
            return this;
        }

        public Builder withVerifyTls(boolean verifyTls) {
            this.verifyTls = verifyTls;
            return this;
        }

        public Builder withCaCertFile(Path caCertFile) {
            this.caCertFile = caCertFile;
            return this;
        }

        public Builder withUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        // TODO consider deprecating this functionality, in favour of explicit subscription messages
        public Builder withInitialInstance(String initialInstance) {
            return withInitialInstance(initialInstance, true, true);
        }

        // TODO consider deprecating this functionality, in favour of explicit subscription messages
        public Builder withInitialInstance(String initialInstance, boolean require, boolean exact) {
            this.initialInstance = initialInstance;
            this.requireInitialInstance = require;
            this.exactInitialInstance = exact;
            return this;
        }

        public Builder withConnectionAttempts(int connectionAttempts) {
            this.connectionAttempts = connectionAttempts;
            return this;
        }

        public Builder withRetryDelay(long retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public YamcsClient build() {
            YamcsClient client = new YamcsClient(host, port, tls, initialInstance, requireInitialInstance,
                    exactInitialInstance, connectionAttempts, retryDelay);
            client.restClient.setInsecureTls(!verifyTls);
            client.websocketClient.setInsecureTls(!verifyTls);
            client.websocketClient2.setInsecureTls(!verifyTls);
            if (caCertFile != null) {
                try {
                    client.restClient.setCaCertFile(caCertFile.toString());
                    client.websocketClient.setCaCertFile(caCertFile.toString());
                    client.websocketClient2.setCaCertFile(caCertFile.toString());
                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException("Cannot set CA Cert file", e);
                }
            }
            if (userAgent != null) {
                client.websocketClient.setUserAgent(userAgent);
                client.websocketClient2.setUserAgent(userAgent);
            }
            return client;
        }
    }
}
