package org.yamcs.web;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Runs a simple http server based on Netty
 */
public class HttpSocketServer {
    
    private final int port;
    private static HttpSocketServer instance;

    private Map<String, YamcsWebService> yamcsInstances=new ConcurrentHashMap<String, YamcsWebService>();
    
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
    
    public boolean isInstanceRegistered( String yamcsInstance) {
        return yamcsInstances.containsKey(yamcsInstance);
    }
    
    public void run() {
        // Configure the server.
        //Note that while the thread pools created with this method are unbounded, netty will limit the number
        //of workers to 2*number of CPU
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new WebSocketServerPipelineFactory());

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        System.out.println("Web socket server started at port " + port + '.');
        System.out.println("Open your browser and navigate to http://localhost:" + port + '/');
    }

    public static void main(String[] args) throws ConfigurationException {
        HttpSocketServer.getInstance().registerYamcsInstance("byops", new YamcsWebService("byops"));
    }
}
