package com.spaceapplications.yamcs.scpi.telnet;

import java.util.List;

import com.spaceapplications.yamcs.scpi.Config;
import com.spaceapplications.yamcs.scpi.Device;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class TelnetServer {

    private Config config;
    private List<Device> devices;
    private int port = 8023;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public TelnetServer(Config config, List<Device> devices) {
        this.config = config;
        this.devices = devices;
        if (config.telnet != null) {
            port = config.telnet.port;
        }
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new TelnetServerInitializer(config, devices));

        b.bind(port).sync();
        System.out.println("Listening for telnet clients on port " + port);
    }

    public void stop() throws InterruptedException {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
    }
}
