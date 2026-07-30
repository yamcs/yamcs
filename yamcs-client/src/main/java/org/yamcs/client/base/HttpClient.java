package org.yamcs.client.base;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.yamcs.api.ExceptionMessage;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.Credentials;
import org.yamcs.client.OAuth2Credentials;
import org.yamcs.client.UnauthorizedException;
import org.yamcs.client.base.SpnegoUtils.SpnegoException;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.ErrorDataEncoderException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class HttpClient {

    private static final Logger log = Logger.getLogger(HttpClient.class.getName());

    public static final String MT_PROTOBUF = "application/protobuf";

    // Refresh an OAuth2 access token once its remaining lifetime drops to this
    // fraction of its original time-to-live.
    private static final double TOKEN_REFRESH_MARGIN_FRACTION = 0.2;
    private static final long TOKEN_REFRESH_POLL_INTERVAL_MS = 10_000;

    private String sendMediaType = MT_PROTOBUF;
    private String acceptMediaType = MT_PROTOBUF;
    private EventLoopGroup group;
    private List<Cookie> cookies;
    private SslContext sslCtx;

    // if set, do not verify server certificate
    private boolean insecureTls;

    private KeyStore caKeyStore;
    private int maxResponseLength = 1024 * 1024;// max length of the expected response

    private String tokenUrl;
    private volatile Credentials credentials;
    private String userAgent;

    private ScheduledExecutorService refreshExecutor;
    private ScheduledFuture<?> refreshTask;

    public synchronized void login(String tokenUrl, String username, char[] password) throws ClientException {
        this.tokenUrl = tokenUrl;
        Map<String, String> attrs = new HashMap<>();
        attrs.put("grant_type", "password");
        attrs.put("username", username);
        attrs.put("password", new String(password));

        try {
            credentials = requestTokens(tokenUrl, attrs).get();
            maybeStartTokenRefresh();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ClientException) {
                throw (ClientException) e.getCause();
            } else {
                throw new ClientException(e.getCause());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new ClientException(e);
        }
    }

    public synchronized void loginWithAuthorizationCode(String tokenUrl, String authorizationCode)
            throws ClientException {
        this.tokenUrl = tokenUrl;
        Map<String, String> attrs = new HashMap<>();
        attrs.put("grant_type", "authorization_code");
        attrs.put("code", authorizationCode);

        try {
            credentials = requestTokens(tokenUrl, attrs).get();
            maybeStartTokenRefresh();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ClientException) {
                throw (ClientException) e.getCause();
            } else {
                throw new ClientException(e.getCause());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new ClientException(e);
        }
    }

    public synchronized String authorizeKerberos(SpnegoInfo info) throws ClientException {
        try {
            return SpnegoUtils.fetchAuthenticationCode(info);
        } catch (SpnegoException e) {
            throw new ClientException(e);
        }
    }

    private synchronized void refreshAccessToken(OAuth2Credentials credentials) throws ClientException {
        if (credentials.getRefreshToken() != null) {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("grant_type", "refresh_token");
            attrs.put("refresh_token", credentials.getRefreshToken());

            try {
                this.credentials = requestTokens(tokenUrl, attrs).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ClientException) {
                    throw (ClientException) e.getCause();
                } else {
                    throw new ClientException(e.getCause());
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new ClientException(e);
            }
        } else if (credentials.getSpnegoInfo() != null) {
            SpnegoInfo spnegoInfo = credentials.getSpnegoInfo();
            String authorizationCode = authorizeKerberos(spnegoInfo);
            loginWithAuthorizationCode(tokenUrl, authorizationCode);
            ((OAuth2Credentials) this.credentials).setSpnegoInfo(spnegoInfo); // We have a new credentials object
        } else {
            throw new ClientException("No refresh token available");
        }
    }

    /**
     * Refreshes the given credentials if they are already expired.
     */
    public synchronized void refreshIfNeeded(OAuth2Credentials credentials) throws ClientException {
        if (credentials.isExpired()) {
            refreshAccessToken(credentials);
        }
    }

    /**
     * Starts a background task that periodically checks whether the current OAuth2 access token is close to expiring,
     * and refreshes it proactively if so.
     */
    private synchronized void maybeStartTokenRefresh() {
        if (!(credentials instanceof OAuth2Credentials)) {
            return;
        }
        if (refreshTask != null) {
            return; // Already running
        }
        if (refreshExecutor == null) {
            refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "yamcs-client-token-refresh");
                t.setDaemon(true);
                return t;
            });
        }
        refreshTask = refreshExecutor.scheduleWithFixedDelay(this::checkAndRefreshToken,
                TOKEN_REFRESH_POLL_INTERVAL_MS, TOKEN_REFRESH_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void checkAndRefreshToken() {
        try {
            Credentials creds = credentials;
            if (creds instanceof OAuth2Credentials oauth2 && oauth2.isExpiringWithin(TOKEN_REFRESH_MARGIN_FRACTION)) {
                refreshAccessToken(oauth2);
                log.fine("Proactively refreshed access token");
            }
        } catch (ClientException e) {
            log.log(Level.WARNING, "Proactive token refresh failed; will retry", e);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Unexpected error during proactive token refresh", e);
        }
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
        maybeStartTokenRefresh();
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    private CompletableFuture<OAuth2Credentials> requestTokens(String url, Map<String, String> attrs)
            throws ClientException, IOException, GeneralSecurityException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ClientException(e);
        }

        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(maxResponseLength);

        ResponseHandler respHandler = new ResponseHandler(responseFuture);

        HttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, uri.getRawPath());
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
        if (userAgent != null) {
            request.headers().set(HttpHeaderNames.USER_AGENT, userAgent);
        }

        try {
            HttpPostRequestEncoder formEncoder = new HttpPostRequestEncoder(request, false);
            for (Entry<String, String> attr : attrs.entrySet()) {
                formEncoder.addBodyAttribute(attr.getKey(), attr.getValue());
            }
            formEncoder.finalizeRequest();
        } catch (ErrorDataEncoderException e) {
            throw new ClientException(e);
        }

        ChannelFuture channelFuture = setupChannel(uri, aggregator, respHandler);
        channelFuture.addListener(f -> {
            if (!f.isSuccess()) {
                responseFuture.completeExceptionally(f.cause());
                return;
            }
            channelFuture.channel().writeAndFlush(request);
        });

        return responseFuture.thenApply(data -> OAuth2Credentials.fromJsonTokenResponse(new String(data)));
    }

    public CompletableFuture<byte[]> doAsyncRequest(String url, HttpMethod httpMethod, byte[] body)
            throws ClientException, IOException, GeneralSecurityException {
        return doAsyncRequest(url, httpMethod, body, null);
    }

    public CompletableFuture<byte[]> doAsyncRequest(String url, HttpMethod httpMethod, byte[] body,
            HttpHeaders extraHeaders) throws ClientException, IOException, GeneralSecurityException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ClientException(e);
        }
        HttpObjectAggregator aggregator = new HttpObjectAggregator(maxResponseLength);

        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();

        HttpRequest request = setupRequest(uri, httpMethod, body);
        if (extraHeaders != null) {
            request.headers().add(extraHeaders);
        }

        ResponseHandler respHandler = new ResponseHandler(responseFuture);

        ChannelFuture channelFuture = setupChannel(uri, aggregator, respHandler);
        channelFuture.addListener(f -> {
            if (!f.isSuccess()) {
                responseFuture.completeExceptionally(f.cause());
                return;
            }
            channelFuture.channel().writeAndFlush(request);
        });
        return responseFuture;
    }

    public CompletableFuture<BulkRestDataSender> doBulkSendRequest(String url, HttpMethod httpMethod)
            throws ClientException, IOException, GeneralSecurityException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ClientException(e);
        }
        CompletableFuture<BulkRestDataSender> cf = new CompletableFuture<>();
        BulkRestDataSender.ContinuationHandler chandler = new BulkRestDataSender.ContinuationHandler(cf);

        ChannelFuture chf = setupChannel(uri, chandler);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getPathWithQuery(uri));
        fillInHeaders(request, uri);
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        HttpUtil.set100ContinueExpected(request, true);

        chf.addListener(f -> {
            if (!f.isSuccess()) {
                cf.completeExceptionally(f.cause());
                return;
            }
            chf.channel().writeAndFlush(request);
        });

        return cf;
    }

    static ClientException decodeException(HttpObject httpObj) throws IOException {
        if (!(httpObj instanceof HttpResponse)) {
            return getInvalidHttpResponseException(httpObj.toString());
        }
        if (!(httpObj instanceof FullHttpResponse)) {
            return getInvalidHttpResponseException(((HttpResponse) httpObj).status().toString());
        }

        FullHttpResponse fullResp = (FullHttpResponse) httpObj;

        if (fullResp.status() == HttpResponseStatus.UNAUTHORIZED) {
            return new UnauthorizedException();
        }

        byte[] data = getByteArray(fullResp.content());
        String contentType = fullResp.headers().get(HttpHeaderNames.CONTENT_TYPE);

        if (contentType != null && MT_PROTOBUF.equals(contentType)) {
            ExceptionMessage msg = ExceptionMessage.parseFrom(data);
            ExceptionData excData = new ExceptionData(msg.getType(), msg.getMsg(), msg.getDetail());
            return new ClientException(excData);
        } else {
            return new ClientException(fullResp.status() + ": " + new String(data));
        }
    }

    private static ClientException getInvalidHttpResponseException(String resp) {
        return new ClientException("Received http response: " + resp);
    }

    /**
     * Sets the maximum size of the responses - this is not applicable to bulk requests whose response is practically
     * unlimited and delivered piece by piece
     * 
     * @param length
     */
    public void setMaxResponseLength(int length) {
        this.maxResponseLength = length;
    }

    /**
     * Perform a request that potentially retrieves large amount of data. The data is forwarded to the client.
     * 
     * @param receiver
     *            send all the data to this receiver. To find out when the request has been finished, the Future has to
     *            be used
     * @return a future indicating when the operation is completed.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public CompletableFuture<Void> doBulkReceiveRequest(String url, HttpMethod httpMethod, byte[] body,
            BulkRestDataReceiver receiver) throws ClientException, IOException, GeneralSecurityException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ClientException(e);
        }
        BulkChannelHandler channelHandler = new BulkChannelHandler(receiver);

        ChannelFuture chf = setupChannel(uri, channelHandler);
        HttpRequest request = setupRequest(uri, httpMethod, body);
        CompletableFuture<Void> cf = new CompletableFuture<>();

        chf.addListener(f -> {
            if (!f.isSuccess()) {
                cf.completeExceptionally(f.cause());
                return;
            }
            Channel ch = chf.channel();
            ch.writeAndFlush(request);
            ChannelFuture closeFuture = ch.closeFuture();
            closeFuture.addListener(f1 -> {
                if (channelHandler.exception != null) {
                    cf.completeExceptionally(channelHandler.exception);
                } else {
                    cf.complete(null);
                }
            });

        });
        cf.whenComplete((v, t) -> {
            if (t instanceof CancellationException) {
                chf.channel().close();
            }
        });
        return cf;
    }

    public CompletableFuture<Void> doBulkRequest(String url, HttpMethod httpMethod, String body,
            BulkRestDataReceiver receiver) throws ClientException, IOException, GeneralSecurityException {
        return doBulkReceiveRequest(url, httpMethod, body.getBytes(), receiver);
    }

    private ChannelFuture setupChannel(URI uri, ChannelHandler... channelHandler)
            throws IOException, GeneralSecurityException {
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(uri.getScheme()) ? 443 : 80;
        }

        if ("https".equalsIgnoreCase(scheme)) {
            sslCtx = getSslContext();
        } else if (!"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only HTTP and HTTPS are supported.");
        }

        if (group == null) {
            group = new NioEventLoopGroup();
        }

        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        if (sslCtx != null) {
                            p.addLast(sslCtx.newHandler(ch.alloc()));
                        }
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpContentDecompressor(0));
                        p.addLast(channelHandler);
                    }
                });
        return b.connect(host, port);
    }

    private SslContext getSslContext() throws GeneralSecurityException, SSLException {
        if (insecureTls) {
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        if (caKeyStore != null) {
            tmf.init(caKeyStore);
        } // else the default trustStore configured with -Djavax.net.ssl.trustStore is used

        return SslContextBuilder.forClient().trustManager(tmf).build();
    }

    /**
     * In case of https connections, this file contains the CA certificates that are used to verify server certificate
     * 
     * @param caCertFile
     */
    public void setCaCertFile(String caCertFile) throws IOException, GeneralSecurityException {
        caKeyStore = CertUtil.loadCertFile(caCertFile);
    }

    public boolean isInsecureTls() {
        return insecureTls;
    }

    /**
     * if true and https connections are used, do not verify server certificate
     * 
     * @param insecureTls
     */
    public void setInsecureTls(boolean insecureTls) {
        this.insecureTls = insecureTls;
    }

    private void fillInHeaders(HttpRequest request, URI uri) throws ClientException {
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, sendMediaType);
        request.headers().set(HttpHeaderNames.ACCEPT, acceptMediaType);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        if (userAgent != null) {
            request.headers().set(HttpHeaderNames.USER_AGENT, userAgent);
        }
        if (cookies != null) {
            String c = ClientCookieEncoder.STRICT.encode(cookies);
            request.headers().set(HttpHeaderNames.COOKIE, c);
        }
        if (credentials != null) {
            if (credentials.isExpired() && credentials instanceof OAuth2Credentials) {
                refreshAccessToken((OAuth2Credentials) credentials); // This blocks
            }
            credentials.modifyRequest(request);
        }
    }

    private HttpRequest setupRequest(URI uri, HttpMethod httpMethod, byte[] body) throws ClientException {
        ByteBuf content = (body == null) ? Unpooled.EMPTY_BUFFER : Unpooled.copiedBuffer(body);
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getPathWithQuery(uri),
                content);
        fillInHeaders(request, uri);
        int length = body == null ? 0 : body.length;
        HttpUtil.setContentLength(request, length);
        return request;
    }

    private String getPathWithQuery(URI uri) {
        String r = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            r += "?" + uri.getRawQuery();
        }
        return r;
    }

    public void addCookie(Cookie c) {
        if (cookies == null) {
            cookies = new ArrayList<>();
        }
        cookies.add(c);
    }

    public List<Cookie> getCookies() {
        return Collections.unmodifiableList(cookies);
    }

    public String getSendMediaType() {
        return sendMediaType;
    }

    public void setSendMediaType(String sendMediaType) {
        this.sendMediaType = sendMediaType;
    }

    public String getAcceptMediaType() {
        return acceptMediaType;
    }

    public void setAcceptMediaType(String acceptMediaType) {
        this.acceptMediaType = acceptMediaType;
    }

    public synchronized void close() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        if (refreshExecutor != null) {
            // Daemon threads, so a caller that forgets to close() (e.g. a leaked YamcsClient)
            // is no worse off than it already is due to the Netty EventLoopGroup below.
            refreshExecutor.shutdownNow();
            refreshExecutor = null;
        }
        if (group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    static byte[] getByteArray(ByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        return b;
    }

    class ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        Throwable exception;
        CompletableFuture<byte[]> cf;

        public ResponseHandler(CompletableFuture<byte[]> cf) {
            this.cf = cf;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse fullHttpResp) {
            if (fullHttpResp.status().code() != HttpResponseStatus.OK.code()) {
                try {
                    exception = decodeException(fullHttpResp);
                } catch (IOException e) {
                    exception = e;
                }
                cf.completeExceptionally(exception);
            } else {
                cf.complete(getByteArray(fullHttpResp.content()));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            exception = cause;
            ctx.close();
            cf.completeExceptionally(cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!cf.isDone()) {
                cf.completeExceptionally(new IOException("connection closed: empty response received"));
            }
        }
    }

    static class BulkChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
        final BulkRestDataReceiver receiver;
        Throwable exception;

        BulkChannelHandler(BulkRestDataReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws IOException {
            if (msg instanceof HttpResponse) {
                HttpResponse resp = (HttpResponse) msg;
                if (resp.status().code() != HttpResponseStatus.OK.code()) {
                    exception = decodeException(msg);
                    receiver.receiveException(exception);
                    ctx.close();
                }
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                try {
                    receiver.receiveData(getByteArray(content.content()));
                } catch (ClientException e) {
                    exceptionCaught(ctx, e);
                }
                if (content instanceof LastHttpContent) {
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            receiver.receiveException(cause);
            exception = cause;
            ctx.close();
        }
    }
}
