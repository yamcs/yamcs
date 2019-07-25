package org.yamcs.web;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.Plugin;
import org.yamcs.api.PluginException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class WebPlugin implements Plugin {

    private String version;

    public WebPlugin() {
        Package pkg = getClass().getPackage();
        version = pkg != null ? pkg.getImplementationVersion() : null;
    }

    @Override
    public String getName() {
        return "yamcs-web";
    }

    @Override
    public String getDescription() {
        return "Web interface for managing and monitoring Yamcs";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getVendor() {
        return "Space Applications Services";
    }

    @Override
    public void onLoad() throws PluginException {
        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        try {
            Bucket bucket = yarch.getBucket("displays");
            if (bucket == null) {
                yarch.createBucket("displays");
            }
        } catch (IOException e) {
            throw new PluginException("Could not create displays bucket", e);
        }

        HttpServer httpServer = YamcsServer.getServer().getGlobalServices(HttpServer.class).get(0);

        Path webRoot = Paths.get("lib/yamcs-web");
        httpServer.addStaticRoot(webRoot);

        YConfiguration httpConfig = httpServer.getConfig();
        YConfiguration websiteConfig = (httpConfig.containsKey("website")) ? httpConfig.getConfig("website")
                : YConfiguration.emptyConfig();

        // Register a custom root handler. We don't add an api handler because
        // those are always subject to authentication and for this path we
        // don't want that.
        WebsiteConfigHandler configHandler = new WebsiteConfigHandler(websiteConfig);
        httpServer.addHandler("websiteConfig", () -> configHandler);

        // Set-up HTML5 deep-linking:
        // Catch any non-handled URL and make it return the contents of our index.html
        // This will cause initialization of the Angular app on any requested path. The
        // Angular router will interpret this and do client-side routing as needed.
        IndexHandler indexHandler = new IndexHandler(httpServer, webRoot);
        httpServer.addHandler("*", () -> indexHandler);
    }
}
