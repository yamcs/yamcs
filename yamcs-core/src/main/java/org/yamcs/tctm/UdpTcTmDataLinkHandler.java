package org.yamcs.tctm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

public class UdpTcTmDataLinkHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private UdpTcTmDataLink link;

    public UdpTcTmDataLinkHandler(UdpTcTmDataLink link) {
        this.link = link;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        var buf = msg.content();
        var packet = new byte[buf.readableBytes()];
        buf.readBytes(packet);
        link.handleIncomingPacket(packet);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }
}
