package org.yamcs.tse;

import static io.netty.handler.codec.Delimiters.lineDelimiter;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class TelnetServer extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(TelnetServer.class);

    // These are marked as '@Sharable'
    private static final StringDecoder STRING_DECODER = new StringDecoder(CharsetUtil.US_ASCII);
    private static final StringEncoder STRING_ENCODER = new StringEncoder(CharsetUtil.US_ASCII);

    private InstrumentController instrumentController;
    private int port;

    private NioEventLoopGroup eventLoopGroup;

    public TelnetServer(int port, InstrumentController instrumentController) {
        this.port = port;
        this.instrumentController = instrumentController;
    }

    @Override
    protected void doStart() {
        eventLoopGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new DelimiterBasedFrameDecoder(8192, lineDelimiter()));
                        pipeline.addLast(STRING_DECODER);
                        pipeline.addLast(STRING_ENCODER);
                        pipeline.addLast(new TelnetServerHandler(instrumentController));
                    }
                });

        try {
            b.bind(port).sync();
            log.debug("Listening for Telnet clients on port " + port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).addListener(future -> {
            if (future.isSuccess()) {
                notifyStopped();
            } else {
                notifyFailed(future.cause());
            }
        });
    }
}
