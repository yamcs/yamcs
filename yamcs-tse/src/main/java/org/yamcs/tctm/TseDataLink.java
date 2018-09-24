package org.yamcs.tctm;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Tse.TseCommand;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.TseLoader;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

public class TseDataLink extends AbstractService implements Link {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1 MB

    // Parameter references are surrounded by backticks (to distinguish from
    // the angle brackets which are used for argument substitution)
    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("`(.*?)`");

    private final Logger log;

    private volatile boolean disabled = false;
    private volatile int outCount = 0;

    private XtceDb xtcedb;
    private String host;
    private int port;

    private Stream ppStream;

    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    private String yamcsInstance;

    public TseDataLink(String yamcsInstance, String name) {
        this(yamcsInstance, name, Collections.emptyMap());
    }

    public TseDataLink(String yamcsInstance, String name, Map<String, Object> args) {
        this.yamcsInstance = yamcsInstance;
        log = LoggingUtils.getLogger(getClass(), yamcsInstance);

        xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        host = YConfiguration.getString(args, "host");
        port = YConfiguration.getInt(args, "port");

        String tcStreamName = YConfiguration.getString(args, "tcStream", "tc_tse");
        Stream tcStream = ydb.getStream(tcStreamName);
        if (tcStream == null) {
            throw new ConfigurationException("Cannot find stream '" + tcStreamName + "'");
        }
        tcStream.addSubscriber(new StreamSubscriber() {
            @Override
            public void onTuple(Stream s, Tuple tuple) {
                sendTc(PreparedCommand.fromTuple(tuple, xtcedb));
            }

            @Override
            public void streamClosed(Stream s) {
                stopAsync();
            }
        });

        String ppStreamName = YConfiguration.getString(args, "ppStream", "pp_tse");
        ppStream = ydb.getStream(ppStreamName);
        if (ppStream == null) {
            throw new ConfigurationException("Cannot find stream '" + ppStreamName + "'");
        }
    }

    public void sendTc(PreparedCommand pc) {
        if (getLinkStatus() != Status.OK) {
            log.warn("Dropping command (link is not OK)");
            return;
        }

        MetaCommand mc = pc.getMetaCommand();
        String subsystemName = mc.getSubsystemName();
        SpaceSystem subsystem = xtcedb.getSpaceSystem(subsystemName);

        TseCommand.Builder msgb = TseCommand.newBuilder()
                .setInstrument(subsystem.getName());

        for (Entry<Argument, Value> entry : pc.getArgAssignment().entrySet()) {
            String name = entry.getKey().getName();
            Value v = entry.getValue();
            switch (name) {
            case TseLoader.ARG_COMMAND:
                msgb.setCommand(v.getStringValue());
                break;
            case TseLoader.ARG_RESPONSE:
                msgb.setResponse(v.getStringValue());
                break;
            default:
                msgb.putArgumentMapping(name, ValueUtility.toGbp(v));
            }
        }

        if (msgb.hasResponse()) {
            Matcher m = PARAMETER_REFERENCE.matcher(msgb.getResponse());
            while (m.find()) {
                String name = m.group(1);
                String qname = subsystem.getQualifiedName() + "/" + name;
                msgb.putParameterMapping(name, qname);
            }
        }

        TseCommand command = msgb.build();
        channel.writeAndFlush(command);
        outCount++;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (channel != null && !channel.isActive()) {
            return Status.UNAVAIL;
        }
        return Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        return getLinkStatus().toString();
    }

    @Override
    public void enable() {
        disabled = false;
        createBootstrap();
    }

    @Override
    public void disable() {
        disabled = true;
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return ppStream.getDataCount();
    }

    @Override
    public long getDataOutCount() {
        return outCount;
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        createBootstrap();
        notifyStarted();
    }

    private void createBootstrap() {
        if (disabled) {
            return;
        }
        if (channel != null && channel.isActive()) {
            return;
        }
        TimeService timeService = YamcsServer.getTimeService(yamcsInstance);
        Bootstrap b = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast(new ProtobufDecoder(ParameterData.getDefaultInstance()));

                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new ProtobufEncoder());

                        pipeline.addLast(new TseDataLinkInboundHandler(timeService, ppStream));
                    }
                });

        ChannelFuture future = b.connect(host, port);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                log.info("Link established to {}:{}", host, port);
                channel = f.channel();
                channel.closeFuture().addListener(closeFuture -> {
                    if (!disabled && !eventLoopGroup.isShuttingDown()) {
                        log.warn("Link to {}:{} closed. Retrying in 10s", host, port);
                        eventLoopGroup.schedule(() -> createBootstrap(), 10, TimeUnit.SECONDS);
                    }
                });
            } else {
                log.info("Cannot establish link to {}:{}: {}. Retrying in 10s",
                        host, port, f.cause().getMessage());
                eventLoopGroup.schedule(() -> createBootstrap(), 10, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    protected void doStop() {
        eventLoopGroup.shutdownGracefully().addListener(f -> {
            if (f.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(f.cause());
            }
        });
    }
}
