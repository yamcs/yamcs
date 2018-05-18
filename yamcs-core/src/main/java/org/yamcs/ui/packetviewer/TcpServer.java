package org.yamcs.ui.packetviewer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;

import org.yamcs.protobuf.Yamcs.TmPacketData;

public class TcpServer {
    private List<Channel> connectedChannels = new ArrayList<>();
    final ArrayBlockingQueue<TmPacketData> queue ;
    Semaphore semaphore = new Semaphore(0);
    ServerBootstrap bootstrap; 
    int port;
    private final PacketViewer packetViewer;
    
    public TcpServer(PacketViewer packetViewer, int port, int queueSize) {
        this.port = port;
        this.packetViewer = packetViewer;
        queue = new ArrayBlockingQueue<>(queueSize);
        
        // Configure the server.       
        //Note2: the DefaultNioSocketChannelConfig is used which uses a 64KB buffer 
        //  before returning false to channel.isWritable
        EventLoopGroup  bossGroup = new NioEventLoopGroup(1);
        //Note that while the thread pools created with this method are unbounded, netty will limit the number
        //of workers to 2*number of CPU
        EventLoopGroup workerGroup = new NioEventLoopGroup();


        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new MySocketHandler());
            }
        });
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
    }
    void start() throws InterruptedException {
        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port)).sync();
    }


    private void reallySendPacket(TmPacketData p) {
        
        for(Channel c:connectedChannels) {
            if(c.isWritable()) {
                byte[] b = p.getPacket().toByteArray();
                if(b.length>4) {
                    c.writeAndFlush(Unpooled.wrappedBuffer(b, 4, b.length-4));
                }
            } else {
                packetViewer.log("Dropping packet because buffer to "+c.remoteAddress()+" is full");
            }
        }
    }


    /**
     * Send a packet to all connected clients or put it in the queue if there is no client connected
     */
    public synchronized void send(TmPacketData p) {
        if(connectedChannels.size()>0) {
            reallySendPacket(p);
        } else {
            if(!queue.offer(p)) {
                queue.remove();
                queue.offer(p);
            }
        }
    }
    /**
     * Send all packets to all connected clients or put them in the queue if there is no client connected
     */
    public synchronized void sendAll(List<TmPacketData> plist) {
        for(TmPacketData p:plist) {
            send(p);
        }
    }    

    /**
     * Add a new client and send all the packets accumulated in the queue if this is the first client
     * @param channel
     */
    public synchronized void addClient(Channel channel) {
        connectedChannels.add(channel);
        if(connectedChannels.size()==1) {
            //first client, flush the queue
            TmPacketData p;
            while((p = queue.poll())!=null) {
                reallySendPacket(p);
            }
        }
    }

    public synchronized void removeClient(Channel channel) {
        connectedChannels.remove(channel);
    }



    class MySocketHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            packetViewer.log( "New TCP connection from "+channel.remoteAddress());
            addClient(channel);

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            packetViewer.log( "TCP client from "+channel.remoteAddress()+" disconnected");
            removeClient(channel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            Channel channel = ctx.channel();
            packetViewer.log( "Exception caught when communication to TCP client from "+channel.remoteAddress());
        }
    }
}