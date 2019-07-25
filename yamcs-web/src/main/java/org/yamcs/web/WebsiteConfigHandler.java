package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;

import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Web.WebsiteConfig;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

@Sharable
public class WebsiteConfigHandler extends Handler {

    private YConfiguration websiteConfig;

    public WebsiteConfigHandler(YConfiguration websiteConfig) {
        this.websiteConfig = websiteConfig;
    }

    @Override
    void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.GET) {
            WebsiteConfig.Builder configb = WebsiteConfig.newBuilder()
                    .setAuth(AuthHandler.createAuthInfo());

            if (websiteConfig.containsKey("tag")) {
                configb.setTag(websiteConfig.getString("tag"));
            }

            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, configb.build(), true);
        } else {
            HttpRequestHandler.sendPlainTextError(ctx, req, METHOD_NOT_ALLOWED);
        }
    }
}
