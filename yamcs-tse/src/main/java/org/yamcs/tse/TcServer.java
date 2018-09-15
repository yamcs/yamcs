package org.yamcs.tse;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Tse.TseCommand;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufDecoder;

/**
 * Listens for TSE commands in the form of Protobuf messages over TCP/IP.
 */
public class TcServer extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(TcServer.class);

    private static final int MAX_FRAME_LENGTH = 512 * 1024; // 512 KB

    private InstrumentController deviceManager;
    private int port = 8135;

    private NioEventLoopGroup eventLoopGroup;

    private TmSender tmSender;

    public TcServer(Map<String, Object> args, InstrumentController deviceManager, TmSender tmSender) {
        port = YConfiguration.getInt(args, "port");
        this.deviceManager = deviceManager;
        this.tmSender = tmSender;
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
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast(new ProtobufDecoder(TseCommand.getDefaultInstance()));
                        pipeline.addLast(new TcServerHandler(TcServer.this));
                    }
                });

        try {
            b.bind(port).sync();
            log.info("TC Server listing for clients on port " + port);
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    public void processTseCommand(TseCommand command) {
        InstrumentDriver device = deviceManager.getInstrument(command.getDevice());
        ListenableFuture<String> f = deviceManager.queueCommand(device, command.getCommand());
        f.addListener(() -> {
            try {
                String response = f.get();
                if (command.hasResponse()) {
                    tmSender.parseResponse(command, response);
                }
            } catch (ExecutionException e) {
                log.error("Failed to execute command", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, directExecutor());
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
