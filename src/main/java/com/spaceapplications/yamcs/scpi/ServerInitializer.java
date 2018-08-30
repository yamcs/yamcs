package com.spaceapplications.yamcs.scpi;

import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
    public Supplier<ServerHandler> serverHandler;

    public ServerInitializer(Supplier<ServerHandler> serverHandler) {
        this.serverHandler = serverHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ByteBuf[] delimiters = new ByteBuf[] {
                Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }),
                Unpooled.wrappedBuffer(new byte[] { '\n' }),
                Unpooled.wrappedBuffer(new byte[] { '\4' })
        };

        ChannelHandler delimitedDecoder = new DelimiterBasedFrameDecoder(8192, false, delimiters);
        ChannelHandler stringDecoder = new StringDecoder();
        ChannelHandler stringEncoder = new StringEncoder();

        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(delimitedDecoder);
        pipeline.addLast(stringDecoder);
        pipeline.addLast(stringEncoder);
        pipeline.addLast(serverHandler.get());
    }
}
