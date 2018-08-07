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
    EventLoopGroup bossEventLoop = new NioEventLoopGroup();
    EventLoopGroup workerEventLoop = new NioEventLoopGroup();

    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossEventLoop, workerEventLoop)
          .channel(NioServerSocketChannel.class) // Set up a TCP server
          .option(ChannelOption.SO_BACKLOG, MAX_INOMING_CONNECTIONS)
          .handler(new LoggingHandler(LogLevel.INFO)) // Handler for boss channel (incl. port binding, accepting
                                                      // connections, etc.)
          .childHandler(new ServerInitializer()); // Handler for worker channel (incl. receiving new data, etc.)
      ChannelFuture f = unchecked(bootstrap.bind(DEFAULT_PORT)::sync).get();
      unchecked(f.channel().closeFuture()::sync).get();
    } finally {
      bossEventLoop.shutdownGracefully();
      workerEventLoop.shutdownGracefully();
    }
  }
}