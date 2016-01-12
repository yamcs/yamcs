package org.yamcs.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.web.rest.Router;

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
public class HttpServer {
    
    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    
    private final int port;
    private static HttpServer instance;

    private Map<String, YamcsWebService> yamcsInstances=new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    
    private Router apiRouter = new Router();
    
    
    public synchronized static HttpServer getInstance() throws ConfigurationException {
        if(instance==null) {
            int port = YConfiguration.getConfiguration("yamcs").getInt("webPort");
            instance=new HttpServer(port);
            instance.run();
        } 
        return instance;
    }
    
    public HttpServer(int port) {
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
    
    public void registerRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        apiRouter.registerRouteHandler(yamcsInstance, routeHandler);
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
            .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childHandler(new HttpServerInitializer(apiRouter));
        
        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));
        
        try {
            log.info("Web address: http://{}:{}/", InetAddress.getLocalHost().getHostName(), port);
        } catch (UnknownHostException e) {
            log.info("Web address: http://localhost:{}/", port);
        }
    }

    public static void main(String[] args) throws ConfigurationException {
        HttpServer.getInstance().registerYamcsInstance("byops", new YamcsWebService("byops"));
    }
}
