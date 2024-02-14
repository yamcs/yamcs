package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.YObjectLoader;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * A UDP-based link that acts as a client: sending TC and receiving TM on the same socket pair.
 */
public class UdpTcTmDataLink extends AbstractTmDataLink implements TcDataLink {

    protected CommandHistoryPublisher commandHistoryPublisher;
    protected AtomicLong dataOutCount = new AtomicLong();
    protected CommandPostprocessor cmdPostProcessor;

    protected String host;
    protected int port;
    protected long initialDelay;

    private Channel channel;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER).withRequired(true);
        spec.addOption("initialDelay", OptionType.INTEGER);
        spec.addOption("commandPostprocessorClassName", OptionType.STRING);
        spec.addOption("commandPostprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
        initialDelay = config.getLong("initialDelay", -1);
        initPostprocessor(yamcsInstance, config);
    }

    @Override
    public boolean sendCommand(PreparedCommand preparedCommand) {
        var binary = postprocess(preparedCommand);
        if (binary != null) {
            var address = (InetSocketAddress) channel.remoteAddress();
            var dgram = new DatagramPacket(Unpooled.wrappedBuffer(binary), address);
            channel.writeAndFlush(dgram);
            dataOutCount.getAndIncrement();
            ackCommand(preparedCommand.getCommandId());
        }

        return true;
    }

    protected void initPostprocessor(String instance, YConfiguration config) {
        String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
        YConfiguration commandPostprocessorArgs = null;

        // The GenericCommandPostprocessor class does nothing if there are no arguments,
        // which is what we want.
        if (config != null) {
            commandPostprocessorClassName = config.getString("commandPostprocessorClassName",
                    GenericCommandPostprocessor.class.getName());
            if (config.containsKey("commandPostprocessorArgs")) {
                commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
            }
        }

        try {
            if (commandPostprocessorArgs != null) {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance,
                        commandPostprocessorArgs);
            } else {
                cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the command postprocessor", e);
            throw e;
        }
    }

    /**
     * Postprocesses the command, unless postprocessing is disabled.
     * 
     * @return potentially modified binary, or {@code null} to indicate that the command should not be handled further.
     */
    protected byte[] postprocess(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (!pc.disablePostprocessing()) {
            binary = cmdPostProcessor.process(pc);
            if (binary == null) {
                log.warn("command postprocessor did not process the command");
            }
        }
        return binary;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher) {
        this.commandHistoryPublisher = commandHistoryPublisher;
        cmdPostProcessor.setCommandHistoryPublisher(commandHistoryPublisher);
    }

    @Override
    protected Status connectionStatus() {
        if (channel == null || !channel.isActive()) {
            return Status.UNAVAIL;
        }
        return Status.OK;
    }

    /**
     * Save an ack in the command history that the command has been sent out of the link
     */
    protected void ackCommand(CommandId commandId) {
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY, getCurrentTime(),
                AckStatus.OK);
    }

    @Override
    protected void doStart() {
        var eventLoopGroup = getEventLoop();
        eventLoopGroup.schedule(() -> createBootstrap(), initialDelay, TimeUnit.MILLISECONDS);
        notifyStarted();
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

    private void createBootstrap() {
        if (disabled.get()) {
            return;
        }
        if (channel != null && channel.isActive()) {
            return;
        }
        var eventLoopGroup = getEventLoop();
        var b = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {

                    @Override
                    protected void initChannel(NioDatagramChannel ch) throws Exception {
                        var pipeline = ch.pipeline();
                        pipeline.addLast(new UdpTcTmDataLinkHandler(UdpTcTmDataLink.this));
                    }
                });
        var future = b.connect(host, port);
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
    public long getDataOutCount() {
        return dataOutCount.get();
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        dataOutCount.set(0);
    }

    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (should exchange with %s:%d)", host, port);
        } else {
            return String.format("OK, exchanging with %s:%d", host, port);
        }
    }

    public void handleIncomingPacket(byte[] packet) {
        if (isRunningAndEnabled()) {
            var tmPacket = new TmPacket(timeService.getMissionTime(), packet);
            tmPacket.setEarthReceptionTime(timeService.getHresMissionTime());
            tmPacket = packetPreprocessor.process(tmPacket);
            if (tmPacket != null) {
                processPacket(tmPacket);
            }
            packetCount.incrementAndGet();
        }
    }
}
