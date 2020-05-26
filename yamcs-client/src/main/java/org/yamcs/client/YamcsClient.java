package org.yamcs.client;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import org.yamcs.client.SpnegoUtils.SpnegoException;

import com.google.protobuf.MessageLite;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;

public class YamcsClient {

    private static final int MAX_FRAME_PAYLOAD_LENGTH = 10 * 1024 * 1024;
    private static final Logger log = Logger.getLogger(YamcsClient.class.getName());

    private final String host;
    private final int port;
    private boolean tls;
    private String context;

    private int connectionAttempts;
    private long retryDelay;

    private final RestClient restClient;
    private final WebSocketClient websocketClient;

    private volatile boolean connected;
    private volatile boolean closed = false;

    private List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private YamcsClient(String host, int port, boolean tls, String context, int connectionAttempts, long retryDelay) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.context = context;
        this.connectionAttempts = connectionAttempts;
        this.retryDelay = retryDelay;

        YamcsConnectionProperties yprops = new YamcsConnectionProperties();
        yprops.setHost(host);
        yprops.setPort(port);
        yprops.setTls(tls);
        yprops.setContext(context);
        restClient = new RestClient(yprops);
        restClient.setAutoclose(false);

        websocketClient = new WebSocketClient(host, port, tls, context, new WebSocketClientCallback() {
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
                connectionListeners.forEach(l -> {
                    l.disconnected();
                });
            }
        });
        websocketClient.setMaxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH);
    }

    public static Builder newBuilder(String host, int port) {
        return new Builder(host, port);
    }

    /**
     * Establish a live communication channel without logging in to the server.
     */
    public synchronized void connectAnonymously() throws ClientException {
        connect(null, false);
    }

    public synchronized void connectWithKerberos() throws ClientException {
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
        connect(accessToken, true);
    }

    /**
     * Login to the server with user/password credential, and establish a live communication channel.
     */
    public synchronized void connect(String username, char[] password) throws ClientException {
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
        connect(accessToken, true);
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
    public synchronized void connect(String accessToken, boolean bypassUpCheck) throws ClientException {
        if (!bypassUpCheck) {
            pollServer();
        }

        for (ConnectionListener cl : connectionListeners) {
            cl.connecting(host + ":" + port);
        }

        try {
            websocketClient.connect(accessToken).get(5000, TimeUnit.MILLISECONDS);

            for (ConnectionListener cl : connectionListeners) {
                cl.connected(host + ":" + port);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (SSLException | GeneralSecurityException | TimeoutException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            throw new ClientException("Cannot connect WebSocket client", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + cause.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", cause);
            if (cause instanceof WebSocketHandshakeException && cause.getMessage().contains("401")) {
                throw new UnauthorizedException();
            } else if (cause instanceof ClientException) {
                throw (ClientException) cause;
            } else {
                throw new ClientException(cause);
            }
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getContext() {
        return context;
    }

    public boolean isConnected() {
        return connected;
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.add(connectionListener);
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public WebSocketClient getWebSocketClient() {
        return websocketClient;
    }

    public String getUrl() {
        if (tls) {
            return String.format("https://%s:%s", host, port);
        } else {
            return String.format("http://%s:%s", host, port);
        }
    }

    public TimeSubscription createTimeSubscription() {
        return new TimeSubscription(websocketClient);
    }

    public EventSubscription createEventSubscription() {
        return new EventSubscription(websocketClient);
    }

    public AlarmSubscription createAlarmSubscription() {
        return new AlarmSubscription(websocketClient);
    }

    public PacketSubscription createPacketSubscription() {
        return new PacketSubscription(websocketClient);
    }

    public ProcessorSubscription createProcessorSubscription() {
        return new ProcessorSubscription(websocketClient);
    }

    public CommandSubscription createCommandSubscription() {
        return new CommandSubscription(websocketClient);
    }

    public ParameterSubscription createParameterSubscription() {
        return new ParameterSubscription(websocketClient);
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

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connected) {
            websocketClient.disconnect();
        }
        restClient.close();
        websocketClient.shutdown();
    }

    public static class Builder {

        private String host;
        private int port;
        private boolean tls;
        private boolean verifyTls;
        private Path caCertFile;
        private String userAgent;
        private String context;

        private int connectionAttempts = 1;
        private long retryDelay = 5000;

        private Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public Builder withContext(String context) {
            this.context = context;
            return this;
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

        public Builder withConnectionAttempts(int connectionAttempts) {
            this.connectionAttempts = connectionAttempts;
            return this;
        }

        public Builder withRetryDelay(long retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public YamcsClient build() {
            YamcsClient client = new YamcsClient(host, port, tls, context, connectionAttempts, retryDelay);
            client.restClient.setInsecureTls(!verifyTls);
            client.websocketClient.setInsecureTls(!verifyTls);
            if (caCertFile != null) {
                try {
                    client.restClient.setCaCertFile(caCertFile.toString());
                    client.websocketClient.setCaCertFile(caCertFile.toString());
                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException("Cannot set CA Cert file", e);
                }
            }
            if (userAgent != null) {
                client.websocketClient.setUserAgent(userAgent);
            }
            return client;
        }
    }
}
