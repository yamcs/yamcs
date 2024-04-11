package org.yamcs.tse;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.time.TimeService;
import org.yamcs.tse.api.TseCommand;
import org.yamcs.tse.api.TseCommanderMessage;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

public class TseDataLink extends AbstractLink {
    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1 MB

    // Parameter references are surrounded by backticks (to distinguish from
    // the angle brackets which are used for argument substitution)
    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("`(.*?)`");

    private volatile long inStartCount = 0; // Where to start counting from. Used after counter reset.
    private AtomicLong outCount = new AtomicLong();

    private Mdb mdb;
    private String host;
    private int port;
    private long initialDelay;

    private Stream ppStream;

    private Channel channel;

    private CommandHistoryPublisher cmdhistPublisher;
    private TsePostprocessor postprocessor;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialDelay", OptionType.INTEGER);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
        super.init(yamcsInstance, name, config);

        cmdhistPublisher = new StreamCommandHistoryPublisher(yamcsInstance);

        mdb = MdbFactory.getInstance(yamcsInstance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        host = config.getString("host");
        port = config.getInt("port");
        initialDelay = config.getLong("initialDelay", 0);

        String tcStreamName = config.getString("tcStream", "tc_tse");
        Stream tcStream = ydb.getStream(tcStreamName);
        if (tcStream == null) {
            throw new ConfigurationException("Cannot find stream '" + tcStreamName + "'");
        }
        tcStream.addSubscriber(new StreamSubscriber() {
            @Override
            public void onTuple(Stream s, Tuple tuple) {
                sendTc(PreparedCommand.fromTuple(tuple, mdb));
            }

            @Override
            public void streamClosed(Stream s) {
                stopAsync();
            }
        });

        String ppStreamName = config.getString("ppStream", "pp_tse");
        ppStream = ydb.getStream(ppStreamName);
        if (ppStream == null) {
            throw new ConfigurationException("Cannot find stream '" + ppStreamName + "'");
        }

        initPostprocessor(yamcsInstance, config);
    }

    private void initPostprocessor(String instance, YConfiguration config) {
        String commandPostprocessorClassName = null;
        YConfiguration commandPostprocessorArgs = null;
        if (config != null && config.containsKey("commandPostprocessorClassName")) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName");
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

        if (commandPostprocessorClassName != null) {
            try {
                if (commandPostprocessorArgs != null) {
                    postprocessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance,
                            commandPostprocessorArgs);
                } else {
                    postprocessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance);
                }
                postprocessor.setCommandHistoryPublisher(cmdhistPublisher);
            } catch (ConfigurationException e) {
                log.error("Cannot instantiate the command postprocessor", e);
                throw e;
            }
        }
    }

    private void sendTc(PreparedCommand pc) {
        if (getLinkStatus() != Status.OK) {
            log.warn("Dropping command (link is not OK)");
            long missionTime = timeService.getMissionTime();
            cmdhistPublisher.commandFailed(pc.getCommandId(), missionTime, "Link is not OK");
            return;
        }

        MetaCommand mc = pc.getMetaCommand();
        String subsystemName = mc.getSubsystemName();
        SpaceSystem subsystem = mdb.getSpaceSystem(subsystemName);

        TseCommand.Builder msgb = TseCommand.newBuilder()
                .setId(pc.getCommandId())
                .setInstrument(subsystem.getName());

        for (Entry<Argument, ArgumentValue> entry : pc.getArgAssignment().entrySet()) {
            String name = entry.getKey().getName();
            Value v = entry.getValue().getEngValue();
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

        TseCommand command;
        if (postprocessor == null) {
            command = msgb.build();
        } else {
            command = postprocessor.process(msgb);
        }
        channel.writeAndFlush(command).addListener(f -> {
            long missionTime = timeService.getMissionTime();
            if (f.isSuccess()) {
                cmdhistPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.AcknowledgeSent_KEY,
                        missionTime, AckStatus.OK);
            } else {
                cmdhistPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.AcknowledgeSent_KEY,
                        missionTime, AckStatus.NOK);
            }
        });
        outCount.incrementAndGet();
    }

    @Override
    public String getDetailedStatus() {
        return getLinkStatus().toString();
    }

    @Override
    public long getDataInCount() {
        return ppStream.getDataCount() - inStartCount;
    }

    @Override
    public long getDataOutCount() {
        return outCount.get();
    }

    @Override
    public void resetCounters() {
        inStartCount = ppStream.getDataCount();
        outCount.set(0);
    }

    @Override
    protected void doStart() {
        EventLoopGroup eventLoopGroup = getEventLoop();
        eventLoopGroup.schedule(() -> createBootstrap(), initialDelay, TimeUnit.MILLISECONDS);
        notifyStarted();
    }

    private void createBootstrap() {
        if (disabled.get()) {
            return;
        }
        if (channel != null && channel.isActive()) {
            return;
        }
        TimeService timeService = YamcsServer.getTimeService(yamcsInstance);
        EventLoopGroup eventLoopGroup = getEventLoop();
        Bootstrap b = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast(new ProtobufDecoder(TseCommanderMessage.getDefaultInstance()));

                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new ProtobufEncoder());

                        pipeline.addLast(new TseDataLinkInboundHandler(
                                cmdhistPublisher, mdb, timeService, ppStream));
                    }
                });

        ChannelFuture future = b.connect(host, port);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                log.info("Link established to {}:{}", host, port);
                channel = f.channel();
                channel.closeFuture().addListener(closeFuture -> {
                    if (isRunningAndEnabled()) {
                        log.warn("Link to {}:{} closed. Retrying in 10s", host, port);
                        eventLoopGroup.schedule(() -> createBootstrap(), 10, TimeUnit.SECONDS);
                    }
                });
            } else if (isRunningAndEnabled()) {
                log.info("Cannot establish link to {}:{}: {}. Retrying in 10s",
                        host, port, f.cause().getMessage());
                eventLoopGroup.schedule(() -> createBootstrap(), 10, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    protected void doStop() {
        if (channel == null) {
            notifyStopped();
            return;
        }

        channel.close().addListener(f -> {
            if (f.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(f.cause());
            }
        });
    }

    @Override
    protected void doEnable() throws Exception {
        createBootstrap();
    }

    @Override
    protected void doDisable() throws Exception {
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    protected Status connectionStatus() {
        if (channel == null || !channel.isActive()) {
            return Status.UNAVAIL;
        }
        return Status.OK;
    }

}
