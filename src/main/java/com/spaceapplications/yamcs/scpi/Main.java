package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingSupplier.unchecked;

import java.util.Optional;

import com.spaceapplications.yamcs.scpi.config.SafeConfig;

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
    Optional.ofNullable(args.config).map(SafeConfig::load);
    SafeConfig c = SafeConfig.load("config.yaml");
    Optional<Integer> port =  c.get("daemon.port");

    NioEventLoopGroup bossEventLoop = new NioEventLoopGroup();
    NioEventLoopGroup workerEventLoop = new NioEventLoopGroup();

    try {
      ServerBootstrap b = bootstrap(bossEventLoop, workerEventLoop);
      ChannelFuture f = unchecked(b.bind(port.orElse(DEFAULT_PORT))::sync).get();
      unchecked(f.channel().closeFuture()::sync).get();
    } finally {
      bossEventLoop.shutdownGracefully();
      workerEventLoop.shutdownGracefully();
    }
  }

  private ServerBootstrap bootstrap(NioEventLoopGroup bossEventLoop, NioEventLoopGroup workerEventLoop) {
    return new ServerBootstrap()
        .group(bossEventLoop, workerEventLoop)
        .channel(NioServerSocketChannel.class)
        .option(ChannelOption.SO_BACKLOG, DEFAULT_MAX_CONNECTIONS)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ServerInitializer());
  }
}