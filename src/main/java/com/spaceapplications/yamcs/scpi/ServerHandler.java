package com.spaceapplications.yamcs.scpi;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
  private static String PROMPT = "\r\n$ ";

  public interface Command extends Supplier<String> {
  }

  public static Map<String, Command> commands = new HashMap<>();

  {
    commands.put("help", () -> "help here");
    commands.put("list", () -> "list devices here");
  }

  private Commander commander;

  public ServerHandler(Commander commander) {
    this.commander = commander;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    ctx.write("Connected. Run help for more info." + PROMPT);
    ctx.flush();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
    Command c = commands.getOrDefault(msg, () -> msg + ": command not found");
    ctx.write(c.get() + PROMPT);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    cause.printStackTrace();
    ctx.close();
  }
}