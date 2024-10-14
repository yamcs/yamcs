package org.yamcs.client;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.base.HttpMethodHandler;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.base.RestClient;
import org.yamcs.client.base.ServerURL;
import org.yamcs.client.base.SpnegoInfo;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.client.base.WebSocketClientCallback;
import org.yamcs.client.mdb.MissionDatabaseClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.client.storage.StorageClient;
import org.yamcs.client.timeline.TimelineClient;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.CreateProcessorRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.EventsApiClient;
import org.yamcs.protobuf.FileTransferApiClient;
import org.yamcs.protobuf.FileTransferServiceInfo;
import org.yamcs.protobuf.GetInstanceRequest;
import org.yamcs.protobuf.GetServerInfoResponse;
import org.yamcs.protobuf.IamApiClient;
import org.yamcs.protobuf.InstancesApiClient;
import org.yamcs.protobuf.LeapSecondsTable;
import org.yamcs.protobuf.ListFileTransferServicesRequest;
import org.yamcs.protobuf.ListFileTransferServicesResponse;
import org.yamcs.protobuf.ListInstancesRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.ListProcessorsRequest;
import org.yamcs.protobuf.ListProcessorsResponse;
import org.yamcs.protobuf.ListServicesRequest;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ProcessingApiClient;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ReconfigureInstanceRequest;
import org.yamcs.protobuf.RestartInstanceRequest;
import org.yamcs.protobuf.ServerApiClient;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServicesApiClient;
import org.yamcs.protobuf.StartInstanceRequest;
import org.yamcs.protobuf.StartServiceRequest;
import org.yamcs.protobuf.StopInstanceRequest;
import org.yamcs.protobuf.StopServiceRequest;
import org.yamcs.protobuf.TimeApiClient;
import org.yamcs.protobuf.UserInfo;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.alarms.AlarmsApiClient;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.ListAlarmsRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsRequest;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsResponse;
import org.yamcs.protobuf.links.DisableLinkRequest;
import org.yamcs.protobuf.links.EnableLinkRequest;
import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.protobuf.links.LinksApiClient;

import com.google.protobuf.Empty;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;

public class YamcsClient {

    private static final Logger log = Logger.getLogger(YamcsClient.class.getName());

    private final ServerURL serverURL;
    private boolean verifyTls;
    private int connectionAttempts;
    private long retryDelay;

    private final RestClient baseClient;
    private final WebSocketClient websocketClient;

    private volatile boolean closed = false;

    private List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private MethodHandler methodHandler;

    private AlarmsApiClient alarmService;
    private TimeApiClient timeService;
    private ServicesApiClient serviceService;
    private InstancesApiClient instanceService;
    private LinksApiClient linkService;
    private EventsApiClient eventService;
    private ProcessingApiClient processingService;
    private IamApiClient iamService;
    private ServerApiClient serverService;

    private YamcsClient(ServerURL serverURL, boolean verifyTls, int connectionAttempts, long retryDelay) {
        this.serverURL = serverURL;
        this.verifyTls = verifyTls;
        this.connectionAttempts = connectionAttempts;
        this.retryDelay = retryDelay;

        baseClient = new RestClient(serverURL);
        baseClient.setAutoclose(false);

        websocketClient = new WebSocketClient(serverURL, new WebSocketClientCallback() {
            @Override
            public void disconnected() {
                if (!closed) {
                    String msg = String.format("Connection to %s lost", serverURL);
                    connectionListeners.forEach(l -> l.log(msg));
                    log.warning(msg);
                }
                connectionListeners.forEach(l -> l.disconnected());
            }
        });

        methodHandler = new HttpMethodHandler(this, baseClient, websocketClient);

        alarmService = new AlarmsApiClient(methodHandler);
        eventService = new EventsApiClient(methodHandler);
        linkService = new LinksApiClient(methodHandler);
        iamService = new IamApiClient(methodHandler);
        instanceService = new InstancesApiClient(methodHandler);
        timeService = new TimeApiClient(methodHandler);
        processingService = new ProcessingApiClient(methodHandler);
        serverService = new ServerApiClient(methodHandler);
        serviceService = new ServicesApiClient(methodHandler);
    }

    public static Builder newBuilder(String serverUrl) {
        return new Builder(ServerURL.parse(serverUrl));
    }

    public static Builder newBuilder(String host, int port) {
        return new Builder(ServerURL.parse("http://" + host + ":" + port));
    }

    public synchronized void loginWithKerberos() throws ClientException {
        loginWithKerberos(System.getProperty("user.name"));
    }

    public synchronized void loginWithKerberos(String principal) throws ClientException {
        pollServer();
        SpnegoInfo spnegoInfo = new SpnegoInfo(serverURL, verifyTls, principal);
        String authorizationCode;
        try {
            authorizationCode = baseClient.authorizeKerberos(spnegoInfo);
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.connectionFailed(e);
            }
            logConnectionFailed(e);
            throw new UnauthorizedException();
        }

        try {
            baseClient.loginWithAuthorizationCode(authorizationCode);
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.connectionFailed(e);
            }
            logConnectionFailed(e);
            throw e;
        }
        var creds = (OAuth2Credentials) baseClient.getCredentials();
        creds.setSpnegoInfo(spnegoInfo); // Can get reused when the access token expires
    }

    public synchronized void login(String username, char[] password) throws ClientException {
        pollServer();
        try {
            baseClient.login(username, password);
        } catch (ClientException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.connectionFailed(e);
            }
            logConnectionFailed(e);
            throw e;
        }
    }

    /**
     * Polls the server, to see if it is ready.
     */
    public void pollServer() throws ClientException {
        for (int i = 0; i < connectionAttempts; i++) {
            synchronized (this) {
                try {
                    // Use an endpoint that does not require auth
                    baseClient.doBaseRequest("/auth", HttpMethod.GET, null).get(5, TimeUnit.SECONDS);
                    return; // Server up!
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof UnauthorizedException) {
                        for (ConnectionListener cl : connectionListeners) {
                            cl.connectionFailed((UnauthorizedException) cause);
                        }
                        logConnectionFailed(cause);
                        throw (UnauthorizedException) cause; // Jump out
                    } else {
                        for (ConnectionListener cl : connectionListeners) {
                            cl.connectionFailed(cause);
                        }
                        logConnectionFailed(cause);
                    }
                } catch (TimeoutException e) {
                    for (ConnectionListener cl : connectionListeners) {
                        cl.connectionFailed(e);
                    }
                    logConnectionFailed(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    for (ConnectionListener cl : connectionListeners) {
                        cl.connectionFailed(new ClientException("Thread interrupted", e));
                    }
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
                cl.connectionFailed(e);
            }
            log.log(Level.WARNING, connectionAttempts + " connection attempts failed, giving up.");
            throw e;
        } else {
            throw new ClientException("Server is not available");
        }
    }

    /**
     * Establish a live communication channel.
     */
    public synchronized void connectWebSocket() throws ClientException {
        Credentials creds = baseClient.getCredentials();
        if (creds == null) {
            connect(null, false);
        } else if (creds instanceof OAuth2Credentials) {
            String accessToken = ((OAuth2Credentials) creds).getAccessToken();
            String authorization = "Bearer " + accessToken;
            connect(authorization, true);
        } else if (creds instanceof BasicAuthCredentials) {
            String authorization = ((BasicAuthCredentials) creds).getAuthorizationHeader();
            connect(authorization, true);
        } else {
            throw new IllegalStateException("Unexpected credentials of type " + creds.getClass());
        }
    }

    /**
     * Establish a live communication channel using a previously acquired access token.
     */
    private synchronized void connect(String authorization, boolean bypassUpCheck) throws ClientException {
        if (!bypassUpCheck) {
            pollServer();
        }

        for (ConnectionListener cl : connectionListeners) {
            cl.connecting();
        }

        try {
            websocketClient.connect(authorization).get(5000, TimeUnit.MILLISECONDS);

            for (ConnectionListener cl : connectionListeners) {
                cl.connected();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (SSLException | GeneralSecurityException | TimeoutException e) {
            for (ConnectionListener cl : connectionListeners) {
                cl.connectionFailed(e);
            }
            logConnectionFailed(e);
            throw new ClientException("Cannot connect WebSocket client", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            for (ConnectionListener cl : connectionListeners) {
                cl.connectionFailed(cause);
            }
            logConnectionFailed(cause);
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
        instanceService.createInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> reconfigureInstance(ReconfigureInstanceRequest request) {
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        instanceService.reconfigureInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<YamcsInstance>> listInstances() {
        CompletableFuture<ListInstancesResponse> f = new CompletableFuture<>();
        instanceService.listInstances(null, ListInstancesRequest.getDefaultInstance(), new ResponseObserver<>(f));
        return f.thenApply(response -> response.getInstancesList());
    }

    public CompletableFuture<YamcsInstance> getInstance(String instance) {
        GetInstanceRequest request = GetInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        instanceService.getInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ListInstancesResponse> listInstances(InstanceFilter filter) {
        ListInstancesRequest.Builder requestb = ListInstancesRequest.newBuilder();
        for (String expression : filter.getFilterExpressions()) {
            requestb.addFilter(expression);
        }
        CompletableFuture<ListInstancesResponse> f = new CompletableFuture<>();
        instanceService.listInstances(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> startInstance(String instance) {
        StartInstanceRequest request = StartInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        instanceService.startInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> stopInstance(String instance) {
        StopInstanceRequest request = StopInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        instanceService.stopInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<YamcsInstance> restartInstance(String instance) {
        RestartInstanceRequest request = RestartInstanceRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<YamcsInstance> f = new CompletableFuture<>();
        instanceService.restartInstance(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<ProcessorInfo>> listProcessors(String instance) {
        ListProcessorsRequest request = ListProcessorsRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<ListProcessorsResponse> f = new CompletableFuture<>();
        processingService.listProcessors(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getProcessorsList());
    }

    public CompletableFuture<GetServerInfoResponse> getServerInfo() {
        CompletableFuture<GetServerInfoResponse> f = new CompletableFuture<>();
        serverService.getServerInfo(null, Empty.getDefaultInstance(), new ResponseObserver<>(f));
        return f;
    }

    public String getServerURL() {
        return serverURL.toString();
    }

    public CompletableFuture<UserInfo> getOwnUserInfo() {
        CompletableFuture<UserInfo> f = new CompletableFuture<>();
        iamService.getOwnUser(null, Empty.getDefaultInstance(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<List<ServiceInfo>> listServices(String instance) {
        ListServicesRequest request = ListServicesRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<ListServicesResponse> f = new CompletableFuture<>();
        serviceService.listServices(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getServicesList());
    }

    public CompletableFuture<Void> startService(String instance, String service) {
        StartServiceRequest request = StartServiceRequest.newBuilder()
                .setInstance(instance)
                .setName(service)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        serviceService.startService(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> null);
    }

    public CompletableFuture<LinkInfo> enableLink(String instance, String link) {
        EnableLinkRequest request = EnableLinkRequest.newBuilder()
                .setInstance(instance)
                .setLink(link)
                .build();
        CompletableFuture<LinkInfo> f = new CompletableFuture<>();
        linkService.enableLink(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<LinkInfo> disableLink(String instance, String link) {
        DisableLinkRequest request = DisableLinkRequest.newBuilder()
                .setInstance(instance)
                .setLink(link)
                .build();
        CompletableFuture<LinkInfo> f = new CompletableFuture<>();
        linkService.disableLink(null, request, new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Void> stopService(String instance, String service) {
        StopServiceRequest request = StopServiceRequest.newBuilder()
                .setInstance(instance)
                .setName(service)
                .build();
        CompletableFuture<Empty> f = new CompletableFuture<>();
        serviceService.stopService(null, request, new ResponseObserver<>(f));
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

    public CompletableFuture<List<FileTransferServiceInfo>> getFileTransferServices(String instance) {
        ListFileTransferServicesRequest request = ListFileTransferServicesRequest.newBuilder().setInstance(instance)
                .build();
        CompletableFuture<ListFileTransferServicesResponse> f = new CompletableFuture<>();

        FileTransferApiClient ftService = new FileTransferApiClient(methodHandler);
        ftService.listFileTransferServices(null, request, new ResponseObserver<>(f));
        return f.thenApply(r -> r.getServicesList());
    }

    public StorageClient createStorageClient() {
        return new StorageClient(methodHandler);
    }

    public ArchiveClient createArchiveClient(String instance) {
        instance = Objects.requireNonNull(instance);
        return new ArchiveClient(methodHandler, instance);
    }

    public MissionDatabaseClient createMissionDatabaseClient(String instance) {
        instance = Objects.requireNonNull(instance);
        return new MissionDatabaseClient(methodHandler, instance);
    }

    public ProcessorClient createProcessorClient(String instance, String processor) {
        instance = Objects.requireNonNull(instance);
        processor = Objects.requireNonNull(processor);
        return new ProcessorClient(methodHandler, instance, processor);
    }

    public TimelineClient createTimelineClient(String instance, String processor) {
        instance = Objects.requireNonNull(instance);
        return new TimelineClient(methodHandler, instance);
    }

    public String getHost() {
        return serverURL.getHost();
    }

    public int getPort() {
        return serverURL.getPort();
    }

    public boolean isTLS() {
        return serverURL.isTLS();
    }

    public String getContext() {
        return serverURL.getContext();
    }

    public boolean isVerifyTLS() {
        return verifyTls;
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.add(connectionListener);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.remove(connectionListener);
    }

    public WebSocketClient getWebSocketClient() {
        return websocketClient;
    }

    public MethodHandler getMethodHandler() {
        return methodHandler;
    }

    public String getUrl() {
        return serverURL.toString();
    }

    public TimeSubscription createTimeSubscription() {
        return new TimeSubscription(methodHandler);
    }

    public ClearanceSubscription createClearanceSubscription() {
        return new ClearanceSubscription(methodHandler);
    }

    public EventSubscription createEventSubscription() {
        return new EventSubscription(methodHandler);
    }

    public AlarmSubscription createAlarmSubscription() {
        return new AlarmSubscription(methodHandler);
    }

    public GlobalAlarmStatusSubscription createGlobalAlarmStatusSubscription() {
        return new GlobalAlarmStatusSubscription(methodHandler);
    }

    public PacketSubscription createPacketSubscription() {
        return new PacketSubscription(methodHandler);
    }

    public ProcessorSubscription createProcessorSubscription() {
        return new ProcessorSubscription(methodHandler);
    }

    public CommandSubscription createCommandSubscription() {
        return new CommandSubscription(methodHandler);
    }

    public QueueEventSubscription createQueueEventSubscription() {
        return new QueueEventSubscription(methodHandler);
    }

    public QueueStatisticsSubscription createQueueStatisticsSubscription() {
        return new QueueStatisticsSubscription(methodHandler);
    }

    public ParameterSubscription createParameterSubscription() {
        return new ParameterSubscription(methodHandler);
    }

    public LinkSubscription createLinkSubscription() {
        return new LinkSubscription(methodHandler);
    }

    public ContainerSubscription createContainerSubscription() {
        return new ContainerSubscription(methodHandler);
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (websocketClient.isConnected()) {
            websocketClient.disconnect();
        }
        baseClient.close();
        websocketClient.shutdown();
    }

    public static class Builder {

        private ServerURL serverURL;
        private boolean verifyTls = true;
        private Path caCertFile;
        private String userAgent;
        private Credentials credentials;
        private int maxResponseLength = 10 * 1024 * 1024;
        private int maxFramePayloadLength = 10 * 1024 * 1024;

        private int connectionAttempts = 1;
        private long retryDelay = 5000;

        private Builder(ServerURL serverURL) {
            this.serverURL = serverURL;
        }

        /**
         * Deprecated: append any context to the server URL instead.
         */
        @Deprecated
        public Builder withContext(String context) {
            serverURL.setContext(context);
            return this;
        }

        /**
         * Deprecated: use either http:// or https:// on the server URL instead.
         */
        @Deprecated
        public Builder withTls(boolean tls) {
            serverURL.setTLS(tls);
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

        public Builder withCredentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder withMaxResponseLength(int maxResponseLength) {
            this.maxResponseLength = maxResponseLength;
            return this;
        }

        public Builder withMaxFramePayloadLength(int maxFramePayloadLength) {
            this.maxFramePayloadLength = maxFramePayloadLength;
            return this;
        }

        public YamcsClient build() {
            YamcsClient client = new YamcsClient(serverURL, verifyTls, connectionAttempts, retryDelay);
            client.baseClient.setInsecureTls(!verifyTls);
            client.websocketClient.setInsecureTls(!verifyTls);
            client.baseClient.setCredentials(credentials);
            if (caCertFile != null) {
                try {
                    client.baseClient.setCaCertFile(caCertFile.toString());
                    client.websocketClient.setCaCertFile(caCertFile.toString());
                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException("Cannot set CA Cert file", e);
                }
            }
            if (userAgent != null) {
                client.baseClient.setUserAgent(userAgent);
                client.websocketClient.setUserAgent(userAgent);
            }
            client.baseClient.setMaxResponseLength(maxResponseLength);
            client.websocketClient.setMaxFramePayloadLength(maxFramePayloadLength);
            return client;
        }
    }

    private void logConnectionFailed(Throwable cause) {
        if (cause instanceof SocketException) {
            log.log(Level.WARNING, "Connection to " + serverURL + " failed: " + cause.getMessage());
        } else {
            log.log(Level.WARNING, "Connection to " + serverURL + " failed", cause);
        }
    }
}
