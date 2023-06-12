package org.yamcs.http;

/**
 * Handles favicon requests. Some of these are automatically issued by browsers, others are referenced in header section
 * of both the auth app and yamcs-web.
 */
public class FaviconHandler extends HttpHandler {

    public static final String[] HANDLED_PATHS = new String[] {
            "apple-touch-icon-precomposed.png", // Not in classpath, but should still respond 404.
            "apple-touch-icon.png",
            "favicon.ico",
            "favicon-16x16.png",
            "favicon-32x32.png",
            "favicon-notification.ico",
            "safari-pinned-tab.svg",
    };

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();
        var filePath = ctx.getPathWithoutContext();
        ctx.sendResource("/favicon" + filePath);
    }
}
