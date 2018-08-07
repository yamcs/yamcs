package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingSupplier.unchecked;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Main {
  public static Integer DEFAULT_PORT = 1337;
  public static Integer MAX_INOMING_CONNECTIONS = 5;

  public static void main(String[] args) {
    new Main(args);
  }

  public Main(String[] args) {
    EventLoopGroup bossEventLoop = new NioEventLoopGroup();
    EventLoopGroup workerEventLoop = new NioEventLoopGroup();
    
    try {
      ServerBootstrap b = bootstrap(bossEventLoop, workerEventLoop);
      ChannelFuture f = unchecked(b.bind(DEFAULT_PORT)::sync).get();
      unchecked(f.channel().closeFuture()::sync).get();
    } finally {
      bossEventLoop.shutdownGracefully();
      workerEventLoop.shutdownGracefully();
    }
  }

  private ServerBootstrap bootstrap(EventLoopGroup bossEventLoop, EventLoopGroup workerEventLoop) {
    return new ServerBootstrap()
      .group(bossEventLoop, workerEventLoop)
      .channel(NioServerSocketChannel.class)
      .option(ChannelOption.SO_BACKLOG, MAX_INOMING_CONNECTIONS)
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler(new ServerInitializer());
  }
}