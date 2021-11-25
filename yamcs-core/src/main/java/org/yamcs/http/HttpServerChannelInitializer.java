package org.yamcs.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;

public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private HttpServer httpServer;
    private final SslContext sslCtx;
    private final GlobalTrafficShapingHandler globalTrafficHandler;

    public HttpServerChannelInitializer(HttpServer httpServer, SslContext sslCtx,
            GlobalTrafficShapingHandler globalTrafficHandler) {
        this.httpServer = httpServer;
        this.sslCtx = sslCtx;
        this.globalTrafficHandler = globalTrafficHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(globalTrafficHandler);
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        pipeline.addLast(new ChannelTrafficShapingHandler(5000));
        pipeline.addLast(new HttpServerCodec());

        CorsConfig corsConfig = httpServer.getCorsConfig();
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }

        // this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(httpServer));
    }
}
