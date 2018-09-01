package com.spaceapplications.yamcs.scpi.device;

import java.net.InetSocketAddress;

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
import io.netty.util.CharsetUtil;

public class TcpIpDevice implements Device {

    // These are marked as '@Sharable'
    private static final StringDecoder STRING_DECODER = new StringDecoder(CharsetUtil.US_ASCII);
    private static final StringEncoder STRING_ENCODER = new StringEncoder(CharsetUtil.US_ASCII);

    private String id;
    private String host;
    private int port;

    private EventLoopGroup group;
    private Channel channel;

    public TcpIpDevice(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    @Override
    public String id() {
        return id;
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
                            ch.pipeline().addLast("frameDecoder", new LineBasedFrameDecoder(80));
                            ch.pipeline().addLast("stringDecoder", STRING_DECODER);
                            ch.pipeline().addLast("stringEncoder", STRING_ENCODER);
                            ch.pipeline().addLast("lxiHandler", new TcpIpClientHandler());
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
    }

    @Override
    public void write(String cmd) {
        if (channel != null) {
            channel.write(cmd);
            channel.writeAndFlush("\n");
        }
    }

    @Override
    public String read() {
        return "todo";
    }
}
