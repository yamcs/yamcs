package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import org.yamcs.protobuf.Web.WebsiteConfig;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

@Sharable
public class WebsiteConfigHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private WebsiteConfig websiteConfig;

    public WebsiteConfigHandler(WebsiteConfig websiteConfig) {
        this.websiteConfig = websiteConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
        String path = qsDecoder.path();
        if (path.equals("/websiteConfig")) {
            handleWebsiteConfigRequest(ctx, req);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, NOT_FOUND);
        }
    }

    private void handleWebsiteConfigRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (req.method() == HttpMethod.GET) {

            WebsiteConfig.Builder configb = WebsiteConfig.newBuilder(websiteConfig);
            configb.setAuth(AuthHandler.createAuthInfo());
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, configb.build(), true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }
}
