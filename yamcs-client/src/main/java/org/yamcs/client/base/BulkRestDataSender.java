package org.yamcs.client.base;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.client.ClientException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Used to post large quantities of data to yamcs. The data is sent using HTTP chuncked encoding
 */
public class BulkRestDataSender extends SimpleChannelInboundHandler<FullHttpResponse> {
    ChannelHandlerContext ctx;
    CompletableFuture<byte[]> completeRequestCf = new CompletableFuture<>();
    volatile ClientException clientException = null;
    int count = 0;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * send the next chunk of data. The caller is blocked if it sends data faster that can be transfered to the server.
     * 
     * @param data
     * @throws ClientException
     *             when there is an exception sending the data. The exception is also thrown if no data can be sent for
     *             10 seconds
     */
    public void sendData(byte[] data) throws ClientException {
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        sendData(buf);
    }

    public void sendData(ByteBuf buf) throws ClientException {
        if (clientException != null) {
            throw clientException;
        }

        count++;
        try {
            Channel ch = ctx.channel();
            if (!ch.isOpen()) {
                throw new ClosedChannelException();
            }

            ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultHttpContent(buf));
            if (!ch.isWritable()) {
                boolean writeCompleted = writeFuture.await(600, TimeUnit.SECONDS);
                if (!writeCompleted) {
                    throw new ClientException("Channel did not become writable in 60 seconds");
                }
            }
        } catch (Exception e) {
            completeRequestCf.completeExceptionally(e);
            if (e instanceof ClientException) {
                throw (ClientException) e;
            } else {
                throw new ClientException(e.toString(), e);
            }
        }
    }

    /**
     * Complete the request by a final empty chunck and return the response from the server.
     * 
     * @return a CompletableFuture that completes once the response from the server has been received.
     */
    public CompletableFuture<byte[]> completeRequest() {
        if (completeRequestCf.isDone()) {
            return completeRequestCf;
        }

        Channel ch = ctx.channel();
        if (!ch.isOpen()) {
            completeRequestCf.completeExceptionally(new ClosedChannelException());
        }
        ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultLastHttpContent());
        writeFuture.addListener(f -> {
            if (!f.isSuccess()) {
                completeRequestCf.completeExceptionally(f.cause());
            }
        });
        return completeRequestCf;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        HttpResponseStatus status = msg.status();
        if (status.equals(HttpResponseStatus.OK)) {
            byte[] b = HttpClient.getByteArray(msg.content());
            completeRequestCf.complete(b);
        } else {
            clientException = HttpClient.decodeException(msg);
            completeRequestCf.completeExceptionally(clientException);
        }
    }

    /**
     *
     * This handler expects to receive a 100 Continue message which means that the request is ok and the sender can
     * start streaming data if this is received, a new bulk sender will be created and added to the pipeline and the
     * CompletableFuture will be completed with the new object if any other response is received, the CompletableFuture
     * will be completed exceptionally.
     */
    static class ContinuationHandler extends SimpleChannelInboundHandler<HttpResponse> {
        CompletableFuture<BulkRestDataSender> cf;

        public ContinuationHandler(CompletableFuture<BulkRestDataSender> cf) {
            this.cf = cf;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpResponse msg) {
            if (msg.status().equals(HttpResponseStatus.CONTINUE)) {
                ChannelPipeline pipeline = ctx.pipeline();
                pipeline.remove(this);
                pipeline.addLast(new HttpObjectAggregator(512 * 1024));
                BulkRestDataSender brds = new BulkRestDataSender();
                pipeline.addLast(brds);
                cf.complete(brds);
            } else {
                cf.completeExceptionally(new ClientException("Cannot continue the bulk load: " + msg.status()));
            }
        }
    }
}
