package org.yamcs.web;

import org.yamcs.protobuf.Web.WebsiteConfig;
import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;

public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private Router apiRouter;
    private CorsConfig corsConfig;
    private WebSocketConfig wsConfig;
    private final WebsiteConfig websiteConfig;
    private final SslContext sslCtx;
    
    public HttpServerChannelInitializer(SslContext sslCtx, Router apiRouter, CorsConfig corsConfig, WebSocketConfig wsConfig,
            WebsiteConfig websiteConfig) {
        this.apiRouter = apiRouter;
        this.corsConfig = corsConfig;
        this.wsConfig = wsConfig;
        this.websiteConfig = websiteConfig;
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if(sslCtx!=null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        pipeline.addLast(new HttpServerCodec());
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }

        // this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(apiRouter, wsConfig, websiteConfig));
    }
}
