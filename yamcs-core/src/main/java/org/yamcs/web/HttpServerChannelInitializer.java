package org.yamcs.web;

import org.yamcs.web.rest.Router;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
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

        if (corsConfig != null) {
            pipeline.addLast(new CorsHandler(corsConfig));
        }
        pipeline.addLast(new HttpContentCompressor());
        pipeline.addLast(new ChunkedWriteHandler());

        //this has to be the last handler in the pipeline
        pipeline.addLast(new HttpRequestHandler(apiRouter));

        // the following handlers are added dynamically depending on the request
        //pipeline.addLast(new HttpObjectAggregator(65536));
        //pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath));
        //pipeline.addLast(new WebSocketFrameHandler<TextWebSocketFrame>());
        //pipeline.addLast(new WebSocketFrameHandler<BinaryWebSocketFrame>());
    }
}
