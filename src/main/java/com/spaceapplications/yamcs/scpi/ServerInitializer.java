package com.spaceapplications.yamcs.scpi;

import java.util.function.Supplier;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
  public Supplier<ServerHandler> serverHandler;

  public ServerInitializer(Supplier<ServerHandler> serverHandler) {
    this.serverHandler = serverHandler;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    ChannelHandler delimitedDecoder = new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter());
    ChannelHandler stringDecoder = new StringDecoder();
    ChannelHandler stringEncoder = new StringEncoder();

    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(delimitedDecoder);
    pipeline.addLast(stringDecoder);
    pipeline.addLast(stringEncoder);
    pipeline.addLast(serverHandler.get());
  }
}