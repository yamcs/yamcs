package org.yamcs.web;

import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;


public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
    
    private Router apiRouter;
    
    public HttpServerInitializer(Router apiRouter) {
        this.apiRouter = apiRouter;
    }
    
    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        
        pipeline.addLast("streamer", new ChunkedWriteHandler());
        pipeline.addLast("handler", new HttpHandler(apiRouter));
    }
}
