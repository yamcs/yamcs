package org.yamcs.replication;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.replication.protobuf.Request;
import org.yamcs.replication.protobuf.Response;
import org.yamcs.replication.protobuf.Wakeup;
import org.yamcs.utils.DecodingException;

import com.google.protobuf.TextFormat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.ChannelInitializer;

/**
 * TCP replication server - works both on the master and on the slave side depending on the channel handler
 * <p>
 * Has to be defined as a global Yamcs service. The {@link ReplicationMaster} or {@link ReplicationSlave} defined at the
 * instance level will register to this if the tcpRole is set to "Server".
 * 
 */
public class ReplicationServer extends AbstractYamcsService {
    int port;
    static final EventLoopGroup workerGroup = new NioEventLoopGroup();
    ServerBootstrap serverBootstrap;
    private Map<String, ReplicationMaster> masters = new HashMap<>();
    private Map<String, ReplicationSlave> slaves = new HashMap<>();
    Set<Channel> activeChannels = Collections.newSetFromMap(new ConcurrentHashMap<Channel, Boolean>());

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);
        port = config.getInt("port");
    }

    @Override
    protected void doStart() {
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(8192, 1, 3));
                        ch.pipeline().addLast(new MyChannelHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        log.debug("Starting replication server on port {}", port);
        try {
            Channel ch = serverBootstrap.bind(port).sync().channel();
            activeChannels.add(ch);
            notifyStarted();
        } catch (InterruptedException e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        for (Channel ch : activeChannels) {
            ch.close();
        }
        notifyStopped();
    }

    public void registerMaster(ReplicationMaster replicationMaster) {
        masters.put(replicationMaster.getYamcsInstance(), replicationMaster);
    }

    public void registerSlave(ReplicationSlave replicationSlave) {
        slaves.put(replicationSlave.getYamcsInstance(), replicationSlave);
    }

    class MyChannelHandler extends ChannelInboundHandlerAdapter {
        ChannelHandlerContext channelHandlerContext;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object o) throws Exception {
            ByteBuffer buf = ((ByteBuf) o).nioBuffer();
            Message msg;
            try {
                msg = Message.decode(buf);
            } catch (DecodingException e) {
                log.warn("Failed to decode message", e);
                sendErrorReturn(0, "Failed to decode message: " + e.getMessage());
                return;
            }

            if (msg.type == Message.WAKEUP) {// this is sent by a master when we are slave.
                processWakeup((Wakeup) msg.protoMsg);
            } else if (msg.type == Message.REQUEST) {
                processRequest((Request) msg.protoMsg);
            } else {
                log.warn("Unexpected message type {} received, closing the connection", msg.type);
                ctx.close();
            }
        }

        private void processWakeup(Wakeup wp) { // called when we are slave
            log.debug("Received wakeup message: {}", TextFormat.shortDebugString(wp));

            verifyAuth(wp.getAuthToken());
            if (!wp.hasYamcsInstance()) {
                sendErrorReturn(0, "instance not present in the request");
                return;
            }
            ReplicationSlave slave = slaves.get(wp.getYamcsInstance());
            if (slave == null) {
                log.warn("No replication slave registered for instance '{}'", wp.getYamcsInstance());
                sendErrorReturn(0, "No replication slave registered for instance '" + wp.getYamcsInstance() + "''");
                return;
            }
            ChannelHandler sch;
            try {
                sch = slave.newChannelHandler();
            } catch (YamcsException e) { // this happens if the master connects twice to the same slave
                log.warn("Got exception when creating a slave handler: " + e);
                sendErrorReturn(0, e.toString());
                return;
            }

            ChannelPipeline pipeline = channelHandlerContext.channel().pipeline();
            pipeline.remove(this);
            pipeline.addLast(sch);
        }

        private void processRequest(Request req) {// called when we are master to initiate a request
            if (!req.hasYamcsInstance()) {
                sendErrorReturn(0, "instance not present in the request");
                return;
            }
            ReplicationMaster master = masters.get(req.getYamcsInstance());
            if (master == null) {
                log.warn("Received a replication request for non registered master: {}", TextFormat.shortDebugString(req));
                sendErrorReturn(req.getRequestSeq(),
                        "No replication master registered for instance '" + req.getYamcsInstance() + "''");
                return;
            }
            log.debug("Received a replication request: {}, starting a new handler on the master", TextFormat.shortDebugString(req));
            ChannelPipeline pipeline = channelHandlerContext.channel().pipeline();
            pipeline.remove(this);
            pipeline.addLast(master.newChannelHandler(req));
        }

        private void verifyAuth(String authToken) {
            // TODO Auto-generated method stub

        }

        private void sendErrorReturn(int requestSeq, String error) {
            Response resp = Response.newBuilder().setRequestSeq(requestSeq).setResult(-1).setErrorMsg(error).build();
            channelHandlerContext.writeAndFlush(Unpooled.wrappedBuffer(Message.get(resp).encode()));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.debug("New connection from {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.channelHandlerContext = ctx;
            activeChannels.add(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("Connection {} closed", ctx.channel().remoteAddress());
            activeChannels.remove(ctx.channel());
        }
    }

}
