package com.spaceapplications.yamcs.scpi;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class TcpIpDevice extends Device {

    private String host;
    private int port;

    private EventLoopGroup group;
    private Channel channel;

    private BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    public TcpIpDevice(String id, String host, int port) {
        super(id);
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(new InetSocketAddress(host, port))
                    .handler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast("frameDecoder", new LineBasedFrameDecoder(Integer.MAX_VALUE));
                            ch.pipeline().addLast("stringDecoder", new StringDecoder(encoding));
                            ch.pipeline().addLast("stringEncoder", new StringEncoder(encoding));
                            ch.pipeline().addLast("responseHandler",
                                    new TcpIpDeviceResponseHandler(TcpIpDevice.this, responseQueue));
                        }
                    });
            ChannelFuture f = b.connect().sync();
            channel = f.channel();
            // f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void disconnect() {
        if (group != null) {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        group = null;
        channel = null;
        responseQueue.clear();
    }

    @Override
    public void write(String cmd) {
        if (channel != null) {
            channel.write(cmd);
            channel.writeAndFlush("\n");
        }
    }

    @Override
    public String read() throws InterruptedException {
        return responseQueue.poll(responseTimeout, TimeUnit.MILLISECONDS);
    }
}
