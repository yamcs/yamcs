package org.yamcs.yfe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.time.TimeService;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class YfeLink extends AbstractLink implements AggregatedDataLink {
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

    // TODO: this should come from the proto definitions
    final static byte TM = 1;

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
        TmLink tmLink = new TmLink(this);
        tmLink.init(instance, name + ".tm", config);
        tmLinks.put(100, tmLink);

        subLinks.add(tmLink);
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

    class YfeChannelHandler extends ChannelInboundHandlerAdapter {
        ChannelHandlerContext ctx;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.ctx = ctx;
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
            if (type == TM) {
                processTm(buf);
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
}
