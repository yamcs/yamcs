package com.spaceapplications.yamcs.scpi;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
  private Commander commander;

  public ServerHandler(Commander commander) {
    this.commander = commander;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    ctx.writeAndFlush(commander.confirm());
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, String cmd) throws Exception {
    ctx.writeAndFlush(commander.execute(cmd));
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    System.out.println(cause);
    ctx.close();
  }
}