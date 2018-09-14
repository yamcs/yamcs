package org.yamcs.tse;

import java.net.InetSocketAddress;

import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

public class TmSenderHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private int seq = 0;

    private InetSocketAddress recipient;
    private DeviceManager deviceManager;

    public TmSenderHandler(InetSocketAddress recipient, DeviceManager deviceManager) {
        this.recipient = recipient;
        this.deviceManager = deviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        deviceManager.addResponseListener((command, response) -> {
            long now = TimeEncoding.getWallclockTime();
            ParameterData.Builder pdata = ParameterData.newBuilder();
            pdata.setGenerationTime(now)
                    .setGroup("TSE")
                    .setSeqNum(seq++)
                    .addParameter(ParameterValue.newBuilder()
                            .setGenerationTime(now)
                            .setId(NamedObjectId.newBuilder().setName("/TSE/simulator/identifier"))
                            .setRawValue(Value.newBuilder().setType(Type.STRING).setStringValue(response)));

            ByteBuf buf = Unpooled.copiedBuffer(pdata.build().toByteArray());
            ctx.writeAndFlush(new DatagramPacket(buf, recipient));
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        // Ignore. No inbound packets are expected.
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
