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
  public static void main(String[] args) {
    System.out.println("hello world");
    EventLoopGroup bossEventLoop = new NioEventLoopGroup();
    EventLoopGroup workerEventLoop = new NioEventLoopGroup();

    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossEventLoop, workerEventLoop)
          .channel(NioServerSocketChannel.class) // Set up a TCP server
          .option(ChannelOption.SO_BACKLOG, 1) // Max incoming connections.
          .handler(new LoggingHandler(LogLevel.INFO)) // Handler for boss channel (incl. port binding, accepting
                                                      // connections, etc.)
          .childHandler(new ServerInitializer()); // Handler for worker channel (incl. receiving new data, etc.)
      ChannelFuture f = unchecked(bootstrap.bind(1337)::sync).get();
      unchecked(f.channel().closeFuture()::sync).get();
    } finally {
      bossEventLoop.shutdownGracefully();
      workerEventLoop.shutdownGracefully();
    }
  }
}