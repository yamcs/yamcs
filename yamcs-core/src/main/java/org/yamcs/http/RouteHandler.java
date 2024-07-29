package org.yamcs.http;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.YamcsServer;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.logging.Log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

@Sharable
public class RouteHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Log log = new Log(RouteHandler.class);
    private static final Pattern LOG_PARAM_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    private int maxPageSize;
    private boolean logSlowRequests = true;
    private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    // Execute routes marked as offloaded
    private final ExecutorService workerPool;

    public RouteHandler(int maxPageSize) {
        this.maxPageSize = maxPageSize;

        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("YamcsHttpExecutor-%d").setDaemon(false).build();
        workerPool = new ThreadPoolExecutor(0, 2 * Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), tf);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        try {
            String contextPath = ctx.channel().attr(HttpRequestHandler.CTX_CONTEXT_PATH).get();
            handle(new HandlerContext(contextPath, ctx, msg, null));
        } catch (Throwable t) {
            if (!(t instanceof HttpException)) {
                t = new InternalServerErrorException(t);
            }

            HttpException e = (HttpException) t;
            if (e.isServerError()) {
                log.error("Responding '{}': {}", e.getStatus(), e.getMessage(), e);
            } else {
                log.warn("Responding '{}': {}", e.getStatus(), e.getMessage());
            }
            HttpRequestHandler.sendPlainTextError(ctx, msg, e.getStatus());
        }
    }

    private void handle(HandlerContext handlerContext) {
        ChannelHandlerContext nettyContext = handlerContext.getNettyChannelHandlerContext();
        RouteContext ctx = nettyContext.channel().attr(HttpRequestHandler.CTX_CONTEXT).get();
        ctx.setFullNettyRequest(handlerContext.getNettyFullHttpRequest());

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

    @Override
    public void exceptionCaught(ChannelHandlerContext nettyContext, Throwable cause) throws Exception {
        log.error("Closing channel due to exception", cause);
        nettyContext.close();
    }

    private void dispatch(RouteContext ctx) {
        ScheduledFuture<?> blockWarning = null;
        if (!ctx.isOffloaded()) {
            blockWarning = timer.schedule(() -> {
                log.error("{}: Blocking the netty thread for 2 seconds. uri: {}", ctx, ctx.getURI());
            }, 2, TimeUnit.SECONDS);
        }

        // the handlers will send themselves the response unless they throw an exception, case which is handled in the
        // catch below.
        Message requestMessage = null;
        try {
            try {
                requestMessage = HttpTranscoder.transcode(ctx);
            } catch (HttpTranscodeException e) {
                throw new BadRequestException(e.getMessage());
            }
            assertSafe(requestMessage);

            MethodDescriptor method = ctx.getMethod();
            if (ctx.isServerStreaming()) {
                ctx.getApi().callMethod(method, ctx, requestMessage, new ServerStreamingObserver(ctx));
            } else {
                ctx.getApi().callMethod(method, ctx, requestMessage, new CallObserver(ctx));
            }
        } catch (Throwable t) {
            handleException(ctx, t);
            ctx.requestFuture.completeExceptionally(t);
        } finally {
            if (blockWarning != null) {
                blockWarning.cancel(true);
            }
        }

        // Log an audit record, if this call is auditable
        if (!ctx.isServerStreaming() && ctx.getLogFormat() != null) {
            Message finalRequestMessage = requestMessage;
            ctx.requestFuture.whenComplete((channelFuture, e) -> {
                if (e == null) {
                    createAuditRecord(ctx, finalRequestMessage);
                }
            });
        }

        if (logSlowRequests) {
            int numSec = ctx.isOffloaded() ? 120 : 20;
            timer.schedule(() -> {
                if (!ctx.isDone()) {
                    log.warn("{}: Executing for more than {} seconds. uri: {}", ctx, numSec, ctx.getURI());
                }
            }, numSec, TimeUnit.SECONDS);
        }
    }

    // Protect paged calls against excessive memory allocation
    private void assertSafe(Message message) {
        FieldDescriptor limitField = message.getDescriptorForType().findFieldByName("limit");
        if (limitField != null && message.hasField(limitField)) {
            Number limit = (Number) message.getField(limitField);
            if (limit.intValue() > maxPageSize) {
                throw new BadRequestException("Limit parameter is too large");
            }
        }
    }

    private void handleException(RouteContext ctx, Throwable t) {
        if (!(t instanceof HttpException)) {
            t = new InternalServerErrorException(t);
        }

        HttpException e = (HttpException) t;
        if (e.isServerError()) {
            log.error("{}: Responding '{}': {}", ctx, e.getStatus(), e.getMessage(), e);
        } else {
            log.warn("{}: Responding '{}': {}", ctx, e.getStatus(), e.getMessage());
        }
        CallObserver.sendError(ctx, e);
    }

    private void createAuditRecord(RouteContext ctx, Message message) {
        HttpServer httpServer = YamcsServer.getServer().getGlobalService(HttpServer.class);

        String format = ctx.getLogFormat();
        Matcher matcher = LOG_PARAM_PATTERN.matcher(format);
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            String param = matcher.group(1);
            FieldDescriptor field = message.getDescriptorForType().findFieldByName(param);
            if (field != null && message.hasField(field)) {
                String replacement = message.getField(field).toString();
                matcher.appendReplacement(buf, replacement);
            } else {
                log.warn("Cannot resolve parameter {} in audit message format '{}'", param, format);
            }
        }
        matcher.appendTail(buf);

        AuditLog auditLog = httpServer.getAuditLog();
        auditLog.addRecord(ctx, message, buf.toString());
    }
}
