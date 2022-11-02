package org.yamcs.replication;

import static org.yamcs.replication.ReplicationServer.workerGroup;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.yamcs.logging.Log;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * Replication TCP client - works both on the master and on the slave side depending on the config
 * 
 */
public class ReplicationClient {
    final String host;
    final int port;
    final Supplier<ChannelHandler> channelHandlerSupplier;
    final Log log;
    final long reconnectionInterval;
    final int maxTupleSize;
    Channel channel;
    ScheduledFuture<?> reconnectFuture;
    Bootstrap bootstrap;
    volatile boolean quitting = false;
    SslContext sslCtx = null;

    public ReplicationClient(String yamcsInstance, String host, int port, SslContext sslCtx,
            long reconnectionInterval, int maxTupleSize,
            Supplier<ChannelHandler> channelHandlerSupplier) {
        this.port = port;
        this.host = host;
        this.channelHandlerSupplier = channelHandlerSupplier;
        this.reconnectionInterval = reconnectionInterval;
        log = new Log(getClass(), yamcsInstance);
        this.sslCtx = sslCtx;
        this.maxTupleSize = maxTupleSize;
    }

    public void start() {
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        if (sslCtx != null) {
                            ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                        }
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(maxTupleSize, 1, 3));
                        ch.pipeline().addLast(channelHandlerSupplier.get());
                    }
                });
        doConnect();
    }

    private void doConnect() {
        log.debug("Connecting for replication to {}:{}", host, port);
        bootstrap.connect(host, port).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                log.info("Connected to server at {}:{}", host, port);
                channel.closeFuture().addListener(f1 -> {
                    scheduleReconnect();
                });
            } else {
                log.warn("Failed to connect: {}", f.cause().getMessage(), f.cause());
                scheduleReconnect();
            }
        });
    }

    void scheduleReconnect() {
        if (quitting || reconnectionInterval < 0) {
            return;
        }

        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        reconnectFuture = workerGroup.schedule(() -> doConnect(), reconnectionInterval, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        quitting = true;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        if (channel != null) {
            channel.close();
        }
    }

    public Channel getChannel() {
        return channel;
    }
}
