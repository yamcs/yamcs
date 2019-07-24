package org.yamcs.web;

import org.yamcs.YConfiguration;
import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;

public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private String contextPath;
    private Router apiRouter;
    private CorsConfig corsConfig;
    private YConfiguration wsConfig;
    private final YConfiguration websiteConfig;
    private final SslContext sslCtx;

    public HttpServerChannelInitializer(SslContext sslCtx, String contextPath, Router apiRouter,
            CorsConfig corsConfig, YConfiguration wsConfig, YConfiguration websiteConfig) {
        this.contextPath = contextPath;
        this.apiRouter = apiRouter;
        this.corsConfig = corsConfig;
        this.wsConfig = wsConfig;
        this.websiteConfig = websiteConfig;
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        pipeline.addLast(new HttpServerCodec());
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }

        // this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(contextPath, apiRouter, wsConfig, websiteConfig));
    }
}
