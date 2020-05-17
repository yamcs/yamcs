package org.yamcs.replication;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import static org.yamcs.replication.MessageType.*;

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
        for(Channel ch: activeChannels) {
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
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            int sizetype = buf.readInt();
            byte msgType = (byte) (sizetype >> 24);
            if (msgType == MessageType.WAKEUP) {// this is sent by a master when we are slave.
                try {
                    Wakeup wp = MessageType.nettyToProto(buf, Wakeup.newBuilder()).build();
                    log.debug("Received wakeup message: {}", TextFormat.shortDebugString(wp));
                    processWakeup(wp);
                } catch (InvalidProtocolBufferException e) {
                    log.warn("Failed to decode WAKEUP message", e);
                    sendErrorReturn(0, "failed: "+e.getMessage());
                }
            } else if (msgType == MessageType.REQUEST) {
                try {
                    Request req = MessageType.nettyToProto(buf, Request.newBuilder()).build();
                    processRequest(req);
                } catch (InvalidProtocolBufferException e) {
                    log.warn("Failed to decode REQUEST message", e);
                    sendErrorReturn(0, "failed: "+e.getMessage());
                }
            } else {
                log.warn("Unexpected message type {} received, closing the connection", msgType);
                ctx.close();
            }
        }

        private void processWakeup(Wakeup wp) { //called when we are slave
            verifyAuth(wp.getAuthToken());
            if(!wp.hasYamcsInstance()) {
                sendErrorReturn(0, "instance not present in the request");
                return;
            }
            ReplicationSlave slave = slaves.get(wp.getYamcsInstance());
            if(slave == null) {
                log.warn( "No replication slave registered for instance '{}'", wp.getYamcsInstance());
                sendErrorReturn(0, "No replication slave registered for instance '"+wp.getYamcsInstance()+"''");
                return;
            }
            ChannelHandler sch;
            try {
                sch = slave.newChannelHandler();
            } catch (YamcsException e) { //this happens if the master connects twice to the same slave
                log.warn("Got exception when creating a slave handler: "+e);
                sendErrorReturn(0, e.toString());
                return;
            }
            
            ChannelPipeline pipeline = channelHandlerContext.channel().pipeline();
            pipeline.remove(this);
            pipeline.addLast(sch);
        }
        
        
        private void processRequest(Request req) {//called when we are master to initiate a request
            if(!req.hasYamcsInstance()) {
                sendErrorReturn(0, "instance not present in the request");
                return;
            }
            ReplicationMaster master = masters.get(req.getYamcsInstance());
            if(master == null) {
                log.warn("Received a replication request for non registered master: {}", req);
                sendErrorReturn(req.getRequestSeq(), "No replication master registered for instance '"+req.getYamcsInstance()+"''");
                return;
            }
            log.debug("Received a replication request: {}, starting a new handler on the master", req);
            ChannelPipeline pipeline = channelHandlerContext.channel().pipeline();
            pipeline.remove(this);
            pipeline.addLast(master.newChannelHandler(req));
        }
        
        
        
        private void verifyAuth(String authToken) {
            // TODO Auto-generated method stub
            
        }

        private void sendErrorReturn(int requestSeq, String error) {
            Response resp = Response.newBuilder().setRequestSeq(requestSeq).setResult(-1).setErrorMsg(error).build();
            channelHandlerContext.writeAndFlush(protoToNetty(MessageType.RESPONSE, resp));
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
