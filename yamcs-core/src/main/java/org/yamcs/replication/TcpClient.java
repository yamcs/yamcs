package org.yamcs.replication;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.yamcs.logging.Log;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.ChannelInitializer;

import static org.yamcs.replication.ReplicationServer.workerGroup;

/**
 * TCP client - works both on the master and on the slave side depending on the config
 * 
 */
public class TcpClient {
    final String host;
    final int port;
    final Supplier<ChannelHandler> channelHandlerSupplier;
    final Log log;
    final long reconnectionInterval;
    
    Channel channel;
    ScheduledFuture<?> reconnectFuture;
    Bootstrap bootstrap;
    volatile boolean quitting = false;

    public TcpClient(String yamcsInstance, String host, int port, long reconnectionInterval,
            Supplier<ChannelHandler> channelHandlerSupplier) {
        this.port = port;
        this.host = host;
        this.channelHandlerSupplier = channelHandlerSupplier;
        this.reconnectionInterval = reconnectionInterval;

        log = new Log(getClass(), yamcsInstance);
    }

    public void start() {
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(8192, 1, 3));
                        ch.pipeline().addLast(channelHandlerSupplier.get());
                    }
                });
        doConnect();
    }

    private void doConnect() {
        log.debug("Connecting for replication to {}:{}", host, port);
        bootstrap.connect(host, port).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (f.isSuccess()) {
                    channel = f.channel();
                    log.info("Connected to server at {}:{}", host, port);
                    channel.closeFuture().addListener(f1 -> {
                        scheduleReconnect();
                    });
                } else {
                    log.warn("Failed to connect: {}", f.cause().getMessage());
                    scheduleReconnect();
                }
            }
        });
    }
    
    void scheduleReconnect() {
        if(quitting || reconnectionInterval < 0) {
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
}
