package org.yamcs.tse.commander;

import org.yamcs.protobuf.Tse.CommandDeviceRequest;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

/**
 * Responds to RPC calls in the form of Protobuf messages over TCP/IP.
 */
public class RpcServer extends AbstractService {

    private static final int MAX_FRAME_LENGTH = 512 * 1024; // 512 KB

    private DeviceManager deviceManager;
    private int port = 8135;

    private NioEventLoopGroup eventLoopGroup;

    public RpcServer(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast("frameDecoder",
                                new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast("protobufDecoder",
                                new ProtobufDecoder(CommandDeviceRequest.getDefaultInstance()));

                        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                        pipeline.addLast("protobufEncoder", new ProtobufEncoder());

                        pipeline.addLast(new RpcServerHandler(deviceManager));
                    }
                });

        try {
            b.bind(port).sync();
            System.out.println("Listening for RPC clients on port " + port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    @Override
    public void doStop() {
        eventLoopGroup.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(future.cause());
            }
        });
    }
}
