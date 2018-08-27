package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingSupplier.unchecked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.spaceapplications.yamcs.scpi.commander.Commander;
import com.spaceapplications.yamcs.scpi.commander.DeviceConnect;
import com.spaceapplications.yamcs.scpi.commander.DeviceInspect;
import com.spaceapplications.yamcs.scpi.commander.DeviceList;
import com.spaceapplications.yamcs.scpi.commander.Command;
import com.spaceapplications.yamcs.scpi.commander.HelpCommand;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Main {
  public static Integer DEFAULT_PORT = 1337;
  public static Integer DEFAULT_MAX_CONNECTIONS = 5;

  public static void main(String[] args) {
    new Main(Args.parse(args));
  }

  public Main(Args args) {
    // No null checking for args.config as it is an obligated arg.
    Config config = Config.load(args.config);
    int port = Optional.ofNullable(config.daemon).map(d -> d.port).orElse(DEFAULT_PORT);

    NioEventLoopGroup boss = new NioEventLoopGroup();
    NioEventLoopGroup worker = new NioEventLoopGroup();

    ServerInitializer init = new ServerInitializer(() -> {
      Commander commander = new Commander();
      List<Command> commands = new ArrayList<>();
      commands.add(new DeviceInspect("device inspect", "Print device configuration details.", commander, config));
      commands.add(new DeviceList("device list", "List available devices to manage.", commander, config));
      commands.add(new DeviceConnect("device connect", "Connect and interact with a given device.", commander, ConfigDeviceParser.get(config)));
      commands.add(new HelpCommand("help", "Prints this description.", commander, commands));
      commander.addAll(commands);
      return new ServerHandler(commander);
    });

    try {
      bootstrap(boss, worker, init, port);
    } finally {
      boss.shutdownGracefully();
      worker.shutdownGracefully();
    }
  }

  private void bootstrap(NioEventLoopGroup boss, NioEventLoopGroup worker, ServerInitializer initializer, int port) {
    ChannelFuture f = new ServerBootstrap().group(boss, worker).channel(NioServerSocketChannel.class)
        .option(ChannelOption.SO_BACKLOG, DEFAULT_MAX_CONNECTIONS).handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(initializer).bind(port);
    blockUntilInterrupted(f);
  }

  private void blockUntilInterrupted(ChannelFuture channelFuture) {
    channelFuture = unchecked(channelFuture::sync).get();
    unchecked(channelFuture.channel().closeFuture()::sync).get();
  }
}