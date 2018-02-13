package org.yamcs.web;

import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;


public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private Router apiRouter;
    private WebConfig webConfig;
    public HttpServerChannelInitializer(Router apiRouter) {
        this.apiRouter = apiRouter;
        webConfig = WebConfig.getInstance();
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        CorsConfig corsConfig = webConfig.getCorsConfig();
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }
        
        //this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(apiRouter, WebConfig.getInstance()));
    }
}
