package com.spaceapplications.yamcs.scpi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.spaceapplications.yamcs.scpi.commander.Command;
import com.spaceapplications.yamcs.scpi.commander.Commander;
import com.spaceapplications.yamcs.scpi.commander.DeviceConnect;
import com.spaceapplications.yamcs.scpi.commander.DeviceInspect;
import com.spaceapplications.yamcs.scpi.commander.DeviceList;
import com.spaceapplications.yamcs.scpi.commander.HelpCommand;
import com.spaceapplications.yamcs.scpi.device.ConfigDeviceParser;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Main {

    public static Integer DEFAULT_PORT = 8181;

    public static void main(String[] args) throws InterruptedException {
        args = new String[] { "--config", "/Users/fdi/workspace/yamcs-scpi/config.yaml" };
        new Main(Args.parse(args));
    }

    public Main(Args args) throws InterruptedException {
        Config config = Config.load(args.config);
        int port = Optional.ofNullable(config.daemon).map(d -> d.port).orElse(DEFAULT_PORT);

        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();

        ServerInitializer initializer = new ServerInitializer(() -> {
            Commander commander = new Commander();
            List<Command> commands = new ArrayList<>();
            commands.add(new DeviceInspect("device inspect", "Print device configuration details.", commander, config));
            commands.add(new DeviceList("device list", "List available devices to manage.", commander, config));
            commands.add(new DeviceConnect("device connect", "Connect and interact with a given device.", commander,
                    ConfigDeviceParser.get(config)));
            commands.add(new HelpCommand("help", "Prints this description.", commander, commands));
            commander.addAll(commands);
            return new ServerHandler(commander);
        });

        try {
            ServerBootstrap b = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(initializer);

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Listening on port " + port);
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
