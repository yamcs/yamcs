package org.yamcs.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;

public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private HttpServer httpServer;
    private final SslContext sslCtx;

    public HttpServerChannelInitializer(HttpServer httpServer, SslContext sslCtx) {
        this.httpServer = httpServer;
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        pipeline.addLast(new HttpServerCodec());

        CorsConfig corsConfig = httpServer.getCorsConfig();
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }

        // this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(httpServer));
    }
}
