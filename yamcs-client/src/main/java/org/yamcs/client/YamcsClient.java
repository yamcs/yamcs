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

import org.yamcs.api.MethodHandler;
import org.yamcs.client.SpnegoUtils.SpnegoException;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.base.HttpMethodHandler;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.base.RestClient;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.client.base.WebSocketClientCallback;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.EditLinkRequest;
import org.yamcs.protobuf.EventsApiClient;
import org.yamcs.protobuf.GetInstanceRequest;
import org.yamcs.protobuf.LeapSecondsTable;
import org.yamcs.protobuf.LinkInfo;
import org.yamcs.protobuf.ListInstancesRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.ListServicesRequest;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ManagementApiClient;
import org.yamcs.protobuf.ProcessingApiClient;
import org.yamcs.protobuf.StartInstanceRequest;
import org.yamcs.protobuf.StartServiceRequest;
import org.yamcs.protobuf.StopInstanceRequest;
import org.yamcs.protobuf.StopServiceRequest;
import org.yamcs.protobuf.TimeApiClient;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.alarms.AlarmsApiClient;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.ListAlarmsRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsRequest;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsResponse;

import com.google.protobuf.Empty;

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

    private final RestClient baseClient;
    private final WebSocketClient websocketClient;

    private volatile boolean connected;
    private volatile boolean closed = false;

    private List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private MethodHandler methodHandler;

    private AlarmsApiClient alarmService;
    private TimeApiClient timeService;
    private ManagementApiClient managementService;
    private EventsApiClient eventService;
    private ProcessingApiClient processingService;

    private YamcsClient(String host, int port, boolean tls, String context, int connectionAttempts, long retryDelay) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.context = context;
        this.connectionAttempts = connectionAttempts;
        this.retryDelay = retryDelay;

        baseClient = new RestClient(host, port, tls, context);
        baseClient.setAutoclose(false);

        websocketClient = new WebSocketClient(host, port, tls, context, new WebSocketClientCallback() {
            @Override
            public void disconnected() {
                if (!closed) {
                    String msg = String.format("Connection to %s:%s lost", host, port);
                    connectionListeners.forEach(l -> l.log(msg));
                    log.warning(msg);
                }
                connected = false;
                connectionListeners.forEach(l -> l.disconnected());
            }
        });
        websocketClient.setMaxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH);

        methodHandler = new HttpMethodHandler(this);

        alarmService = new AlarmsApiClient(methodHandler);
        eventService = new EventsApiClient(methodHandler);
        timeService = new TimeApiClient(methodHandler);
        managementService = new ManagementApiClient(methodHandler);
        processingService = new ProcessingApiClient(methodHandler);
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
            baseClient.loginWithAuthorizationCode(authorizationCode);
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
        String accessToken = baseClient.getCredentials().getAccessToken();
        connect(accessToken, true);
    }

    /**
     * Login to the server with user/password credential, and establish a live communication channel.
     */
    public synchronized void connect(String username, char[] password) throws ClientException {
        pollServer();
        try {
            baseClient.login(username, password);
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.log("Connection to " + host + ":" + port + " failed: " + e.getMessage());
            }
            log.log(Level.WARNING, "Connection to " + host + ":" + port + " failed", e);
            throw e;
        }
        String accessToken = baseClient.getCredentials().getAccessToken();
        connect(accessToken, true);
    }

    /**
     * Polls the server, to see if it is ready.
     */
    public synchronized void pollServer() throws ClientException {
        for (int i = 0; i < connectionAttempts; i++) {
            try {
                // Use an endpoint that does not require auth
                baseClient.doBaseRequest("/auth", HttpMethod.GET, null).get(5, TimeUnit.SECONDS);
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

    public CompletableFuture<YamcsInstance> createInstance(CreateInstanceRequest request) {
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        managementService.createInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<YamcsInstance>> listInstances() {
        CompletableFuture<ListInstancesResponse> f = new CompletableFuture<>();
        managementService.listInstances(null, ListInstancesRequest.getDefaultInstance(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getInstancesList());
    }

    public CompletableFuture<YamcsInstance> getInstance(String instance) {
        GetInstanceRequest request = GetInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        managementService.getInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ListInstancesResponse> listInstances(InstanceFilter filter) {
        ListInstancesRequest.Builder requestb = ListInstancesRequest.newBuilder();
        for (String expression : filter.getFilterExpressions()) {
            requestb.addFilter(expression);
        }
        CompletableFuture<ListInstancesResponse> f = new CompletableFuture<>();
        managementService.listInstances(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> startInstance(String instance) {
        StartInstanceRequest request = StartInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        managementService.startInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> stopInstance(String instance) {
        StopInstanceRequest request = StopInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        managementService.stopInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ListServicesResponse> listServices(String instance) {
        ListServicesRequest request = ListServicesRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<ListServicesResponse> f = new CompletableFuture<>();
        managementService.listServices(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> startService(String instance, String service) {
        StartServiceRequest request = StartServiceRequest.newBuilder()
                .setInstance(instance)
                .setName(service)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        managementService.startService(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<LinkInfo> enableLink(String instance, String link) {
        EditLinkRequest request = EditLinkRequest.newBuilder()
                .setInstance(instance)
                .setName(link)
                .setState("enabled")
                .build();
        CompletableFuture<LinkInfo> f = new CompletableFuture<>();
        managementService.updateLink(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<LinkInfo> disableLink(String instance, String link) {
        EditLinkRequest request = EditLinkRequest.newBuilder()
                .setInstance(instance)
                .setName(link)
                .setState("disabled")
                .build();
        CompletableFuture<LinkInfo> f = new CompletableFuture<>();
        managementService.updateLink(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> stopService(String instance, String service) {
        StopServiceRequest request = StopServiceRequest.newBuilder()
                .setInstance(instance)
                .setName(service)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        managementService.stopService(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<LeapSecondsTable> getLeapSeconds() {
        CompletableFuture<LeapSecondsTable> f = new CompletableFuture<>();
        timeService.getLeapSeconds(null, Empty.getDefaultInstance(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ProcessorClient> createProcessor(CreateProcessorRequest request) {
        CompletableFuture<Empty> f = new CompletableFuture<>();
        processingService.createProcessor(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> new ProcessorClient(methodHandler, request.getInstance(), request.getName()));
    }

    public CompletableFuture<Event> createEvent(CreateEventRequest request) {
        CompletableFuture<Event> f = new CompletableFuture<>();
        eventService.createEvent(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ListAlarmsResponse> listAlarms(String instance) {
        ListAlarmsRequest request = ListAlarmsRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<ListAlarmsResponse> f = new CompletableFuture<>();
        alarmService.listAlarms(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ListProcessorAlarmsResponse> listAlarms(String instance, String processor) {
        ListProcessorAlarmsRequest request = ListProcessorAlarmsRequest.newBuilder()
                .setInstance(instance)
                .setProcessor(processor)
                .build();
        CompletableFuture<ListProcessorAlarmsResponse> f = new CompletableFuture<>();
        alarmService.listProcessorAlarms(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> editAlarm(EditAlarmRequest request) {
        CompletableFuture<Empty> f = new CompletableFuture<>();
        alarmService.editAlarm(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public ArchiveClient createArchiveClient(String instance) {
        return new ArchiveClient(methodHandler, instance);
    }

    public MissionDatabaseClient createMissionDatabaseClient(String instance) {
        return new MissionDatabaseClient(methodHandler, instance);
    }

    public ProcessorClient createProcessorClient(String instance, String processor) {
        return new ProcessorClient(methodHandler, instance, processor);
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
        return baseClient;
    }

    public WebSocketClient getWebSocketClient() {
        return websocketClient;
    }

    public MethodHandler getMethodHandler() {
        return methodHandler;
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

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connected) {
            websocketClient.disconnect();
        }
        baseClient.close();
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
            client.baseClient.setInsecureTls(!verifyTls);
            client.websocketClient.setInsecureTls(!verifyTls);
            if (caCertFile != null) {
                try {
                    client.baseClient.setCaCertFile(caCertFile.toString());
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
