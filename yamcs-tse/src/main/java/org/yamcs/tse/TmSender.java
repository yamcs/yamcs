package org.yamcs.tse;

import java.net.InetSocketAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * Listens for TSE commands in the form of Protobuf messages over TCP/IP.
 */
public class TmSender extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(TmSender.class);

    private DeviceManager deviceManager;
    private String host;
    private int port;

    private NioEventLoopGroup eventLoopGroup;

    public TmSender(Map<String, Object> args, DeviceManager deviceManager) {
        host = YConfiguration.getString(args, "host");
        port = YConfiguration.getInt(args, "port");
        this.deviceManager = deviceManager;
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new TmSenderHandler(new InetSocketAddress(host, port), deviceManager));

        try {
            b.connect(host, port).sync();
            log.info("TM Sender will send to {}:{}", host, port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    @Override
    public void doStop() {
        eventLoopGroup.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(future.cause());
            }
        });
    }
}
