package org.yamcs.tse;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Tse.TseCommand;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * Listens for TSE commands and sends them to Yamcs in the form of Protobuf messages over TCP/IP.
 */
public class TmSender extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(TmSender.class);

    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("([^`]*)`(.*?)`([^`]*)");

    private String host;
    private int port;

    private NioEventLoopGroup eventLoopGroup;
    private Channel channel;

    private int seq = 0;

    public TmSender(Map<String, Object> args) {
        host = YConfiguration.getString(args, "host");
        port = YConfiguration.getInt(args, "port");
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        // Ignore inbound
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        log.error("Closing channel due to exception", cause);
                        ctx.close();
                    }
                });

        try {
            channel = b.connect(host, port).sync().channel();
            log.info("TM Sender will send to {}:{}", host, port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    public void parseResponse(TseCommand command, String response) {
        long now = TimeEncoding.getWallclockTime();
        ParameterData.Builder pdata = ParameterData.newBuilder();
        pdata.setGenerationTime(now)
                .setGroup("TSE")
                .setSeqNum(seq++);

        StringBuilder regex = new StringBuilder();
        Matcher m = PARAMETER_REFERENCE.matcher(command.getResponse());
        while (m.find()) {
            String l = m.group(1);
            String name = m.group(2);
            String r = m.group(3);
            regex.append(Pattern.quote(l));
            regex.append("(?<").append(name).append(">.+)");
            regex.append(Pattern.quote(r));
        }

        Pattern p = Pattern.compile(regex.toString());
        m = p.matcher(response);
        if (m.matches()) {
            for (Entry<String, String> entry : command.getParameterMappingMap().entrySet()) {
                String value = m.group(entry.getKey());
                String qname = entry.getValue();
                pdata.addParameter(ParameterValue.newBuilder()
                        .setGenerationTime(now)
                        .setId(NamedObjectId.newBuilder().setName(qname))
                        .setRawValue(Value.newBuilder().setType(Type.STRING).setStringValue(value)));
            }

            ByteBuf buf = Unpooled.copiedBuffer(pdata.build().toByteArray());
            channel.writeAndFlush(new DatagramPacket(buf, new InetSocketAddress(host, port)));
        } else {
            log.warn("Instrument response '{}' could not be matched to pattern '{}'.", response, command.getResponse());
        }
    }

    @Override
    public void doStop() {
        eventLoopGroup.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(future.cause());
            }
        });
    }
}
