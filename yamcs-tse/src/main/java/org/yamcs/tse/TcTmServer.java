package org.yamcs.tse;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Tse.TseCommand;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

/**
 * Listens for TSE commands in the form of Protobuf messages over TCP/IP.
 */
public class TcTmServer extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(TcTmServer.class);

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1 MB
    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("([^`]*)`(.*?)`([^`]*)");

    private InstrumentController instrumentController;
    private int port = 8135;

    private NioEventLoopGroup eventLoopGroup;
    private int seq = 0;

    public TcTmServer(int port, InstrumentController instrumentController) {
        this.port = port;
        this.instrumentController = instrumentController;
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast(new ProtobufDecoder(TseCommand.getDefaultInstance()));

                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new ProtobufEncoder());

                        pipeline.addLast(new TcTmServerHandler(TcTmServer.this));
                    }
                });

        try {
            b.bind(port).sync();
            log.info("TC/TM Server listening for clients on port " + port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    public void processTseCommand(ChannelHandlerContext ctx, TseCommand command) {
        InstrumentDriver device = instrumentController.getInstrument(command.getInstrument());
        boolean expectResponse = command.hasResponse();

        ListenableFuture<String> f = instrumentController.queueCommand(device, command.getCommand(), expectResponse);
        f.addListener(() -> {
            try {
                String response = f.get();
                if (expectResponse) {
                    parseResponse(ctx, command, response);
                }
            } catch (ExecutionException e) {
                log.error("Failed to execute command", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, directExecutor());
    }

    private void parseResponse(ChannelHandlerContext ctx, TseCommand command, String response) {
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
            ctx.writeAndFlush(pdata);
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
