package org.yamcs.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.yamcs.logging.Log;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.epoll.EpollDomainDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainDatagramPacket;
import io.netty.channel.unix.DomainSocketAddress;

/**
 * Helper utility for sending systemd notification events about state changes.
 * <p>
 * The primary use case is to send a notification to the systemd notification when Yamcs has finished starting. This can
 * be used when Yamcs runs as a service unit with {@code Type=notify} in its definition file.
 * <p>
 * See https://www.freedesktop.org/software/systemd/man/sd_notify.html
 */
public class SDNotify {

    private static final Log log = new Log(SDNotify.class);

    // If started through systemd with Type=notify, Yamcs will have NOTIFY_SOCKET
    // env set to the path of an AF_UNIX socket.
    private static final String NOTIFY_SOCKET = System.getenv("NOTIFY_SOCKET");

    /**
     * Tell the service manager that Yamcs has finished starting.
     * <p>
     * <strong>This method requires that {@link #isSupported()} returns {@code true}.</strong>
     */
    public static void sendStartupNotification() throws IOException {
        notify("READY=1", "MAINPID=" + ProcessHandle.current().pid());
    }

    /**
     * Tell the service manager that Yamcs is beginning its shutdown.
     * <p>
     * <strong>This method requires that {@link #isSupported()} returns {@code true}.</strong>
     */
    public static void sendStoppingNotification() {
        try {
            notify("STOPPING=1");
        } catch (IOException e) {
            // Ignore
        }
    }

    private static void notify(String... notifications) throws IOException {
        if (!isSupported()) {
            throw new IllegalStateException("NOTIFY_SOCKET environment variable is not set");
        }

        var recipient = new DomainSocketAddress(NOTIFY_SOCKET);
        var group = new EpollEventLoopGroup(1);
        try {
            try {
                var bootstrap = new Bootstrap();
                bootstrap.group(group).channel(EpollDomainDatagramChannel.class)
                        .handler(new ChannelOutboundHandlerAdapter());

                var file = File.createTempFile("netty", "dsocket");
                file.delete();
                var domainSocketAddress = new DomainSocketAddress(file);
                var channel = bootstrap.bind(domainSocketAddress).sync().channel();
                if (log.isDebugEnabled()) {
                    for (var notification : notifications) {
                        log.debug("Writing: " + notification);
                    }
                }
                var message = String.join("\n", notifications);
                var buf = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
                var packet = new DomainDatagramPacket(buf, recipient);
                channel.writeAndFlush(packet).sync();
                file.delete();
            } finally {
                group.shutdownGracefully().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isSupported() {
        return NOTIFY_SOCKET != null;
    }
}
