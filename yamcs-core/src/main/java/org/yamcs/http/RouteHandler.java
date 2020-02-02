package org.yamcs.http;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.logging.Log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

@Sharable
public class RouteHandler extends Handler {

    private static final Log log = new Log(RouteHandler.class);

    private boolean logSlowRequests = true;
    private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    // this is used to execute the routes marked as offloaded
    private final ExecutorService workerPool;

    public RouteHandler() {
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("YamcsHttpExecutor-%d").setDaemon(false).build();
        workerPool = new ThreadPoolExecutor(0, 2 * Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), tf);
    }

    @Override
    public void handle(ChannelHandlerContext nettyContext, FullHttpRequest req) {
        RouteContext ctx = nettyContext.channel().attr(HttpRequestHandler.CTX_CONTEXT).get();
        ctx.setFullNettyRequest(req);

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
        try {
            Message requestMessage;
            try {
                requestMessage = HttpTranscoder.transcode(ctx);
            } catch (HttpTranscodeException e) {
                throw new BadRequestException(e.getMessage());
            }

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

        if (logSlowRequests) {
            int numSec = ctx.isOffloaded() ? 120 : 20;
            timer.schedule(() -> {
                if (!ctx.isDone()) {
                    log.warn("{}: Executing for more than {} seconds. uri: {}", ctx, numSec, ctx.getURI());
                }
            }, numSec, TimeUnit.SECONDS);
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
}
