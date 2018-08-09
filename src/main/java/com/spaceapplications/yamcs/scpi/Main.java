package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingSupplier.unchecked;

import java.util.Arrays;
import java.util.List;
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
  public static Integer MAX_INOMING_CONNECTIONS = 5;

  public static void main(String[] args) {
    SafeConfig c = SafeConfig.load("config.yaml");
    System.out.println("base: " + c.get("base"));
    System.out.println("base.port: " + c.get("base.port"));
    
    Optional<List<Integer>> bla = c.get("devices.tenma");
    
    List<Integer> b = bla.orElse(Arrays.asList());
    System.out.println("base.stuff: " + b);
  }

  public Main(Args args) {
    NioEventLoopGroup bossEventLoop = new NioEventLoopGroup();
    NioEventLoopGroup workerEventLoop = new NioEventLoopGroup();

    try {
      ServerBootstrap b = bootstrap(bossEventLoop, workerEventLoop);
      ChannelFuture f = unchecked(b.bind(DEFAULT_PORT)::sync).get();
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
        .option(ChannelOption.SO_BACKLOG, MAX_INOMING_CONNECTIONS)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ServerInitializer());
  }
}