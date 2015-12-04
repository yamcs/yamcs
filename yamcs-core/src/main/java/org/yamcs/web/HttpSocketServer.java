package org.yamcs.web;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Runs a simple http server based on Netty
 */
public class HttpSocketServer {
    
    private static final Logger log = LoggerFactory.getLogger(HttpSocketServer.class);
    
    private final int port;
    private static HttpSocketServer instance;

    private Map<String, YamcsWebService> yamcsInstances=new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    
    
    public synchronized static HttpSocketServer getInstance() throws ConfigurationException {
        if(instance==null) {
            int port = YConfiguration.getConfiguration("yamcs").getInt("webPort");
            instance=new HttpSocketServer(port);
            instance.run();
        } 
        return instance;
    }
    
    public HttpSocketServer(int port) {
        this.port = port;
    }
    
    public void registerYamcsInstance(String yamcsInstance, YamcsWebService service) {
        yamcsInstances.put(yamcsInstance, service);
    }
    
    public void unregisterYamcsInstance(String yamcsInstance) {
        yamcsInstances.remove(yamcsInstance);
        if(yamcsInstances.isEmpty()) {
            instance.shutdown();
        }
    }
    
    private void shutdown() {
        bossGroup.shutdownGracefully();
    }

    public boolean isInstanceRegistered(String yamcsInstance) {
        return yamcsInstances.containsKey(yamcsInstance);
    }
    
    public YamcsWebService getYamcsWebService(String yamcsInstance) {
        return yamcsInstances.get(yamcsInstance);
    }
    
    public void run() {
        // Configure the server.

        bossGroup = new NioEventLoopGroup(1);
        //Note that while the thread pools created with this method are unbounded, netty will limit the number
        //of workers to 2*number of CPU
        EventLoopGroup workerGroup = new NioEventLoopGroup();
       
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.INFO))
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childHandler(new HttpServerInitializer());
        
        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        log.info("Web server started at port " + port);
    }

    public static void main(String[] args) throws ConfigurationException {
        HttpSocketServer.getInstance().registerYamcsInstance("byops", new YamcsWebService("byops"));
    }
}
