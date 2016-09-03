package org.yamcs.web;

import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;


public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private Router apiRouter;
    private CorsConfig corsConfig;

    public HttpServerChannelInitializer(Router apiRouter) {
        this.apiRouter = apiRouter;
        
        corsConfig = WebConfig.getInstance().getCorsConfig();
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(65536));
        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }
        pipeline.addLast(new SmartHttpContentCompressor());
        pipeline.addLast(new HttpRequestHandler(apiRouter));

        // Currently added dynamically due to websocketPath not fixed
        //pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath));
        //pipeline.addLast(new WebSocketFrameHandler<TextWebSocketFrame>());
        //pipeline.addLast(new WebSocketFrameHandler<BinaryWebSocketFrame>());
    }
}
