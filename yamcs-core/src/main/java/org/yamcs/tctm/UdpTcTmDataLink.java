package org.yamcs.tctm;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.actions.ActionResult;
import org.yamcs.commanding.PreparedCommand;

import com.google.gson.JsonObject;

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
public class UdpTcTmDataLink extends AbstractTcTmParamLink {

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
        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
        initialDelay = config.getLong("initialDelay", -1);
    }

    @Override
    public boolean sendCommand(PreparedCommand preparedCommand) {
        var binary = postprocess(preparedCommand);
        if (binary != null) {
            var address = (InetSocketAddress) channel.remoteAddress();
            var dgram = new DatagramPacket(Unpooled.wrappedBuffer(binary), address);
            channel.writeAndFlush(dgram);
            dataOut(1, binary.length);
            ackCommand(preparedCommand.getCommandId());
        }

        return true;
    }

    @Override
    protected Status connectionStatus() {
        if (channel == null || !channel.isActive()) {
            return Status.UNAVAIL;
        }
        return Status.OK;
    }

    @Override
    protected void doStart() {
        var eventLoopGroup = getEventLoop();
        eventLoopGroup.schedule(() -> createBootstrap(), initialDelay, TimeUnit.MILLISECONDS);
        addAction(new ChangeDestinationAction());
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
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (client to %s:%d)", host, port);
        } else {
            return String.format("OK, client to %s:%d", host, port);
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
            dataIn(1, packet.length);
        }
    }

    private class ChangeDestinationAction extends LinkAction {

        ChangeDestinationAction() {
            super("change-destination", "Change destination");
        }

        @Override
        public Spec getSpec() {
            var spec = new Spec();
            spec.addOption("host", OptionType.STRING)
                    .withRequired(true)
                    .withDefault(host);
            spec.addOption("port", OptionType.INTEGER)
                    .withRequired(true)
                    .withDefault(port);
            return spec;
        }

        @Override
        public void execute(Link link, JsonObject request, ActionResult result) {
            host = request.get("host").getAsString();
            port = request.get("port").getAsInt();
            log.info("Changing destination to {}:{}", host, port);

            if (isRunningAndEnabled()) {
                var ch = UdpTcTmDataLink.this.channel;
                disable();
                ch.close().addListener(f -> {
                    if (f.isSuccess()) {
                        enable();
                        result.complete();
                    } else {
                        result.completeExceptionally(f.cause());
                    }
                });
            } else {
                result.complete();
            }
        }
    }
}
