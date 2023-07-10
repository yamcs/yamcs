package org.yamcs.ygw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;

import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.utils.ProtoBufUtils;
import org.yamcs.ygw.protobuf.Ygw.Event;
import org.yamcs.ygw.protobuf.Ygw.MessageType;
import org.yamcs.ygw.protobuf.Ygw.ParameterData;

import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class YgwLink extends AbstractLink implements AggregatedDataLink {
    final static int MAX_PACKET_LENGTH = 0xFFFF;

    String instance;
    YConfiguration config;

    String host;
    int port;
    long reconnectionDelay;

    String linkName;
    TcLink tcLink;

    List<Link> subLinks = new ArrayList<>();
    Map<Integer, TmLink> tmLinks = new HashMap<>();

    YfeChannelHandler handler;
    public static final byte VERSION = 0;

    @Override
    public void init(String instance, String name, YConfiguration config) {
        this.instance = instance;
        this.config = config;
        this.host = config.getString("host");
        this.port = config.getInt("port");
        this.linkName = name;
        this.reconnectionDelay = config.getLong("reconnectionDelay");

        log = new Log(getClass(), instance);
        log.setContext(name);
        eventProducer = EventProducerFactory.getEventProducer(instance, name, 10000);
        timeService = YamcsServer.getTimeService(instance);

        // TODO configure properly
        TmLink tmLink1 = new TmLink(this);
        tmLink1.init(instance, name + ".mil1553-tm", config);
        tmLinks.put(1, tmLink1);

        TmLink tmLink2 = new TmLink(this);
        tmLink2.init(instance, name + ".lan-tm", config);
        tmLinks.put(2, tmLink2);

        TcLink tcLink = new TcLink(this, 100);
        tcLink.init(instance, name + ".tc", config);

        subLinks.add(tcLink);
        subLinks.add(tmLink1);
        subLinks.add(tmLink2);
    }

    @Override
    public Spec getSpec() {
        Spec spec = getDefaultSpec();
        spec.addOption("host", OptionType.STRING).withRequired(true)
                .withDescription("The host to connect to the Yamcs frontend");

        spec.addOption("port", OptionType.INTEGER)
                .withDescription("Port to connect to the Yamcs frontend");

        spec.addOption("reconnectionDelay", OptionType.INTEGER).withDefault(5000)
                .withDescription("If the connection to the Yamcs frontend fails or breaks, "
                        + "the time (in milliseconds) to wait before reconnection.");

        // FIXME - should be possible to add them from the AbstractTmDataLink
        spec.addOption("packetPreprocessorClassName", OptionType.STRING);
        spec.addOption("packetPreprocessorArgs", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("updateSimulationTime", OptionType.BOOLEAN).withDefault(false);
        
        return spec;

    }

    void connect() {
        handler = new YfeChannelHandler();
        NioEventLoopGroup workerGroup = getEventLoop();
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(MAX_PACKET_LENGTH, 0, 4));
                ch.pipeline().addLast(handler);
            }
        });
        b.connect(host, port).addListener(f -> {
            if (!f.isSuccess()) {
                eventProducer.sendWarning("Failed to connect to the Yamcs frontend: " + f.cause().getMessage());
                if (reconnectionDelay > 0) {
                    workerGroup.schedule(() -> connect(), reconnectionDelay,
                            TimeUnit.MILLISECONDS);
                }
            } else {
                log.info("Connected to the Yamcs frontend at {}:{}", host, port);
            }
        });

    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

    @Override
    public long getDataInCount() {
        long count = 0;
        for (Link l : subLinks) {
            count += l.getDataInCount();
        }
        return count;
    }

    @Override
    public long getDataOutCount() {
        return tcLink == null ? 0 : tcLink.getDataOutCount();
    }

    @Override
    public void resetCounters() {
        for (Link l : subLinks) {
            l.resetCounters();
        }
    }

    @Override
    public void doDisable() {
        doStop();
    }

    @Override
    public void doEnable() {
        doStart();
    }

    @Override
    public String getName() {
        return linkName;
    }

    @Override
    protected Status connectionStatus() {
        var _handler = handler;
        if (_handler == null) {
            return Status.UNAVAIL;
        }
        return _handler.isConnected() ? Status.OK : Status.UNAVAIL;
    }

    @Override
    protected void doStart() {
        getEventLoop().execute(() -> connect());
        notifyStarted();
    }

    @Override
    protected void doStop() {
        getEventLoop().execute(() -> {
            if (handler != null) {
                handler.stop();
            }
        });
        notifyStopped();
    }

    boolean isConnected() {
        var _handler = handler;
        if (_handler == null) {
            return false;
        }
        return _handler.isConnected();
    }

    public CompletableFuture<Void> sendMessage(byte msgType, int targetId, byte[] data) {
        CompletableFuture<Void> cf = new CompletableFuture<Void>();
        var _handler = handler;
        if (_handler == null || _handler.ctx == null) {
            cf.completeExceptionally(new IOException("Connection to the frontend not open"));
        } else {
            _handler.sendMessage(msgType, targetId, data).addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    cf.complete(null);
                } else {
                    cf.completeExceptionally(future.cause());
                }
            });
        }
        return cf;
    }

    class YfeChannelHandler extends ChannelInboundHandlerAdapter {
        ChannelHandlerContext ctx;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.ctx = ctx;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.warn("Connection to the Yamcs frontend closed");
            ctx.executor().schedule(() -> connect(), reconnectionDelay, TimeUnit.MILLISECONDS);
        }

        public ChannelFuture sendMessage(byte msgType, int targetId, byte[] data) {
            ByteBuf buf = Unpooled.buffer(10 + data.length);
            buf.writeInt(6 + data.length);
            buf.writeByte(VERSION);
            buf.writeByte(msgType);

            buf.writeInt(targetId);
            buf.writeBytes(data);

            return ctx.writeAndFlush(buf);

        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            buf.readInt();// length
            byte version = buf.readByte();
            if (version != VERSION) {
                log.warn("Got mesage with version {}, expected {}; closing connection", version, VERSION);
                ctx.close();
                return;
            }
            byte type = buf.readByte();
            if (type == MessageType.TM_VALUE) {
                processTm(buf);
            } else if (type == MessageType.PARAMETER_DATA_VALUE) {
                processParameters(buf);
            } else if (type == MessageType.EVENT_VALUE) {
                processEvent(buf);
            } else {
                // TODO
                log.warn("message of type {} not implemented", type);
            }

        }

        private void processTm(ByteBuf buf) {
            int targetId = buf.readInt();
            TmLink tmLink = tmLinks.get(targetId);
            if (tmLink == null) {
                log.warn("Got message for unknown target {}", targetId);
                return;
            }
            tmLink.processMessage(buf);
        }

        private void processEvent(ByteBuf buf) {
            int targetId = buf.readInt();

            try {
                Event ev = ProtoBufUtils.fromByteBuf(buf, Event.newBuilder());
                eventProducer.sendEvent(toYamcsEvent(ev));
            } catch (InvalidProtocolBufferException e) {
                log.warn("Failed to decode event", e);
            }
        }

        private void processParameters(ByteBuf buf) {
            int targetId = buf.readInt();

            try {
                ParameterData para = ProtoBufUtils.fromByteBuf(buf, ParameterData.newBuilder());
                System.out.println("got parameters " + para);
            } catch (InvalidProtocolBufferException e) {
                log.warn("Failed to decode parameters", e);
            }
        }

        public boolean isConnected() {
            return ctx != null && ctx.channel().isOpen();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Caught exception {}", cause.getMessage());
        }

        public void stop() {
            ctx.close();
        }
    }

    private org.yamcs.yarch.protobuf.Db.Event toYamcsEvent(Event ev) {
        org.yamcs.yarch.protobuf.Db.Event.Builder yevb = org.yamcs.yarch.protobuf.Db.Event.newBuilder()
                .setMessage(ev.getMessage())
                .setGenerationTime(ev.getGenerationTime())
                .setSeqNumber(ev.getSeqNumber());

        if (ev.hasSource()) {
            yevb.setSource(ev.getSource());

        }
        yevb.setGenerationTime(timeService.getMissionTime());

        return yevb.build();
    }

}
