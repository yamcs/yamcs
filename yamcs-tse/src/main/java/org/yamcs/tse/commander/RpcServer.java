package org.yamcs.tse.commander;

import org.yamcs.protobuf.Tse.CommandDeviceRequest;

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
public class RpcServer {

    private DevicePool devicePool;
    private int port = 8135;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public RpcServer(DevicePool devicePool) {
        this.devicePool = devicePool;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                        pipeline.addLast("protobufDecoder",
                                new ProtobufDecoder(CommandDeviceRequest.getDefaultInstance()));

                        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                        pipeline.addLast("protobufEncoder", new ProtobufEncoder());

                        pipeline.addLast(new RpcServerHandler(devicePool));
                    }
                });

        b.bind(port).sync();
        System.out.println("Listening for RPC clients on port " + port);
    }

    public void stop() throws InterruptedException {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
    }
}
