package org.yamcs.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    public synchronized static HttpServer getInstance(){
        if(instance==null) {
            try {
                setup();
            } catch (InterruptedException e) {
                throw new RuntimeException(e); //shouldn't happen since the webserver is setup from the main java class
            }
        } 
        return instance;
    }

    public static void setup() throws InterruptedException {
        int port = WebConfig.getInstance().getPort();
        instance = new HttpServer(port);
        instance.run();
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

    public void run() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        
        //Note that while the thread pools created with this method are unbounded, netty will limit the number
        //of workers to 2*number of CPU cores
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childHandler(new HttpServerChannelInitializer(apiRouter));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port)).sync();

        try {
            log.info("Web address: http://{}:{}/", InetAddress.getLocalHost().getHostName(), port);
        } catch (UnknownHostException e) {
            log.info("Web address: http://localhost:{}/", port);
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer.getInstance().registerYamcsInstance("byops", new YamcsWebService("byops"));
    }
}
