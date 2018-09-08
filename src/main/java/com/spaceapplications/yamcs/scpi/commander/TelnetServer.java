package com.spaceapplications.yamcs.scpi.commander;

import java.util.List;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class TelnetServer {

    private List<Device> devices;
    private int port = 8023;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public TelnetServer(List<Device> devices) {
        this.devices = devices;
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
                .childHandler(new TelnetServerInitializer(devices));

        b.bind(port).sync();
        System.out.println("Listening for telnet clients on port " + port);
    }

    public void stop() throws InterruptedException {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
    }
}
