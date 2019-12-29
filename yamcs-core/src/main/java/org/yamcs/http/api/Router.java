package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.yamcs.api.Api;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpContentToByteBufDecoder;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpUtils;
import org.yamcs.http.MethodNotAllowedException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.RpcDescriptor;
import org.yamcs.logging.Log;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.util.AttributeKey;

/**
 * Matches a request uri to a registered route handler. Stops on the first match.
 * <p>
 * The Router itself has the same granularity as HttpServer: one instance only.
 * <p>
 * When matching a route, priority is first given to built-in routes, only if none match the first matching
 * instance-specific dynamic route is matched. Dynamic routes often mention '{instance}' in their url, which will be
 * expanded upon registration into the actual yamcs instance.
 */
@Sharable
public class Router extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Log log = new Log(Router.class);

    public static final AttributeKey<Context> CTX_CONTEXT = AttributeKey.valueOf("routerContext");

    private String contextPath;
    private ProtobufRegistry protobufRegistry;

    private List<Api<Context>> apis = new ArrayList<>();
    private List<Route> routes = new ArrayList<>();

    private boolean logSlowRequests = true;
    private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService workerPool;

    public Router(ExecutorService executor, String contextPath, ProtobufRegistry protobufRegistry) {
        this.workerPool = executor;
        this.contextPath = contextPath;
        this.protobufRegistry = protobufRegistry;

        addApi(new AlarmsApi());
        addApi(new BucketsApi());
        addApi(new CfdpApi());
        addApi(new ClientsApi());
        addApi(new Cop1Api());
        addApi(new GeneralApi(this));
        addApi(new ExportApi());
        addApi(new IamApi());
        addApi(new IndexApi());
        addApi(new ManagementApi());
        addApi(new MdbApi());
        addApi(new ParameterArchiveApi());
        addApi(new ProcessingApi());
        addApi(new QueueApi());
        addApi(new StreamArchiveApi());
        addApi(new RocksDbApi());
        addApi(new TableApi());
        addApi(new TagApi());
    }

    public void addApi(Api<Context> api) {
        apis.add(api);

        for (MethodDescriptor method : api.getDescriptorForType().getMethods()) {
            RpcDescriptor descriptor = protobufRegistry.getRpc(method.getFullName());
            if (descriptor == null) {
                throw new UnsupportedOperationException("Unable to find rpc definition: " + method.getFullName());
            }

            routes.add(new Route(api, descriptor.getHttpRoute(), descriptor));
            for (HttpRoute route : descriptor.getAdditionalHttpRoutes()) {
                routes.add(new Route(api, route, descriptor));
            }
        }

        // Sort in a way that increases chances of a good URI match
        Collections.sort(routes);
    }

    /**
     * At this point we do not have the full request (only the header) so we have to configure the pipeline either for
     * receiving the full request or with route specific pipeline for receiving (large amounts of) data in case of
     * dataLoad routes.
     */
    public void scheduleExecution(ChannelHandlerContext nettyContext, HttpRequest nettyRequest, String uri) {

        RouteMatch match = matchRoute(nettyRequest.method(), uri);
        if (match == null) {
            throw new NotFoundException();
        }

        Context ctx = new Context(nettyContext, nettyRequest, match.route, match.regexMatch);
        log.debug("{}: Routing {} {}", ctx, nettyRequest.method(), nettyRequest.uri());

        nettyContext.channel().attr(CTX_CONTEXT).set(ctx);
        match.route.incrementRequestCount();

        // Track status for metric purposes
        ctx.requestFuture.whenComplete((channelFuture, e) -> {
            if (ctx.getStatusCode() == 0) {
                log.warn("{}: Status code not reported", ctx);
            } else if (ctx.getStatusCode() < 200 || ctx.getStatusCode() >= 300) {
                match.route.incrementErrorCount();
            }
        });

        ChannelPipeline pipeline = nettyContext.pipeline();
        if (ctx.isClientStreaming()) {
            System.out.println(ctx + " , setting up pipeline for client stream");
            pipeline.addLast(new HttpContentToByteBufDecoder());
            pipeline.addLast(new ProtobufVarint32FrameDecoder());

            String body = ctx.getBodySpecifier();
            Message bodyPrototype = ctx.getRequestPrototype();
            if (body != null && !"*".equals(body)) {
                FieldDescriptor field = bodyPrototype.getDescriptorForType().findFieldByName(body);
                bodyPrototype = bodyPrototype.newBuilderForType().getFieldBuilder(field)
                        .getDefaultInstanceForType();
            }
            pipeline.addLast(new ProtobufDecoder(bodyPrototype));
            pipeline.addLast(new StreamingClientHandler(nettyRequest));

            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                nettyContext.writeAndFlush(HttpUtils.CONTINUE_RESPONSE.retainedDuplicate());
            }
        } else {
            pipeline.addLast(new HttpContentCompressor());

            // this will cause the channelRead0 to be called as soon as the request is complete
            // it will also reject requests whose body is greater than the MAX_BODY_SIZE)
            pipeline.addLast(new HttpObjectAggregator(ctx.getMaxBodySize()));
            pipeline.addLast(this);
            nettyContext.fireChannelRead(nettyRequest);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext nettyContext, FullHttpRequest req) throws Exception {
        Context ctx = nettyContext.channel().attr(CTX_CONTEXT).get();

        if (ctx.isOffloaded()) {
            ctx.getBody().retain();
            workerPool.execute(() -> {
                dispatch(ctx);
                ctx.getBody().release();
            });
        } else {
            dispatch(ctx);
        }
    }

    private RouteMatch matchRoute(HttpMethod method, String uri) throws MethodNotAllowedException {
        for (Route route : routes) {
            if (route.getHttpMethod().equals(method)) {
                Matcher matcher = route.matchURI(uri);
                if (matcher.matches()) {
                    if (route.isDeprecated()) {
                        log.warn("A client used a deprecated route: {}", uri);
                    }

                    return new RouteMatch(matcher, route);
                }
            }
        }

        // Second pass, in case we did not find an exact match
        Set<HttpMethod> allowedMethods = new HashSet<>(4);
        for (Route route : routes) {
            Matcher matcher = route.matchURI(uri);
            if (matcher.matches()) {
                allowedMethods.add(method);
            }
        }
        if (!allowedMethods.isEmpty()) {
            throw new MethodNotAllowedException(method, uri, allowedMethods);
        }

        return null;
    }

    private void dispatch(Context ctx) {
        ScheduledFuture<?> twoSecWarn = null;
        if (!ctx.isOffloaded()) {
            twoSecWarn = timer.schedule(() -> {
                log.error("{}: Blocking the netty thread for 2 seconds. uri: {}", ctx, ctx.getURI());
            }, 2, TimeUnit.SECONDS);
        }

        // the handlers will send themselves the response unless they throw an exception, case which is handled in the
        // catch below.
        try {
            Message requestMessage;
            try {
                requestMessage = HttpTranscoder.transcode(ctx);
            } catch (HttpTranscodeException e) {
                throw new BadRequestException(e.getMessage());
            }

            MethodDescriptor method = ctx.getMethod();

            Observer<Message> responseObserver;
            if (ctx.isServerStreaming()) {
                responseObserver = new ServerStreamingObserver(ctx);
            } else {
                responseObserver = new CallObserver(ctx);
            }

            if (ctx.isClientStreaming()) {
                Observer<? extends Message> requestObserver = ctx.getApi().callMethod(method, ctx, responseObserver);
            } else {
                ctx.getApi().callMethod(method, ctx, requestMessage, responseObserver);
            }

            ctx.requestFuture.whenComplete((channelFuture, e) -> {
                if (e != null) {
                    log.debug("{}: API request finished with error: {}, transferred bytes: {}",
                            ctx, e.getMessage(), ctx.getTransferredSize());
                } else {
                    log.debug("{}: API request finished successfully, transferred bytes: {}",
                            ctx, ctx.getTransferredSize());
                }
            });
        } catch (Throwable t) {
            handleException(ctx, t);
            ctx.requestFuture.completeExceptionally(t);
        }
        if (twoSecWarn != null) {
            twoSecWarn.cancel(true);
        }

        if (logSlowRequests) {
            int numSec = ctx.isOffloaded() ? 120 : 20;
            timer.schedule(() -> {
                if (!ctx.requestFuture.isDone()) {
                    log.warn("{} executing for more than {} seconds. uri: {}", ctx, numSec, ctx.nettyRequest.uri());
                }
            }, numSec, TimeUnit.SECONDS);
        }
    }

    private void handleException(Context ctx, Throwable t) {
        if (t instanceof HttpException) {
            HttpException e = (HttpException) t;
            if (e.isServerError()) {
                log.error("{}: Responding '{}': {}", ctx, e.getStatus(), e.getMessage(), e);
                CallObserver.sendError(ctx, e.getStatus(), e);
            } else {
                log.warn("{}: Responding '{}': {}", ctx, e.getStatus(), e.getMessage());
                CallObserver.sendError(ctx, e.getStatus(), e);
            }
        } else {
            log.error("{}: Responding '{}'", ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, t);
            CallObserver.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, t);
        }
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public String getContextPath() {
        return contextPath;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Closing channel due to exception", cause);
        ctx.close();
    }

    /**
     * Represents a matched route pattern
     */
    private static final class RouteMatch {
        final Matcher regexMatch;
        final Route route;

        RouteMatch(Matcher regexMatch, Route route) {
            this.regexMatch = regexMatch;
            this.route = route;
        }
    }
}
