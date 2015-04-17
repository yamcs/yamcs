package org.yamcs.web;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Runs a simple http server based on Netty
 */
public class HttpSocketServer {
    
    private final int port;
    private static HttpSocketServer instance;

    private Map<String, YamcsWebService> yamcsInstances=new ConcurrentHashMap<String, YamcsWebService>();
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
    public void registerYamcsInstance(String yinstance, YamcsWebService rps) {
        yamcsInstances.put(yinstance, rps);
    }
    
    public void unRegisterYamcsInstance(String yinstance) {
        yamcsInstances.remove(yinstance);
        if(yamcsInstances.isEmpty()) {
            instance.shutdown();
        }
    }
    
    private void shutdown() {
	bossGroup.shutdownGracefully();
	
    }

    public boolean isInstanceRegistered( String yamcsInstance) {
        return yamcsInstances.containsKey(yamcsInstance);
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
        	   .childHandler(new HttpServerInitializer());
        
        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        System.out.println("Web socket server started at port " + port + '.');
        System.out.println("Open your browser and navigate to http://localhost:" + port + '/');
    }

    public static void main(String[] args) throws ConfigurationException {
        HttpSocketServer.getInstance().registerYamcsInstance("byops", new YamcsWebService("byops"));
    }
}
