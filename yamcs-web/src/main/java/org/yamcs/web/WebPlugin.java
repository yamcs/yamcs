package org.yamcs.web;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.yamcs.Plugin;
import org.yamcs.PluginException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YamcsServer;
import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class WebPlugin implements Plugin {

    private Log log = new Log(getClass());
    private String version;

    public WebPlugin() {
        Package pkg = getClass().getPackage();
        version = (pkg != null) ? pkg.getImplementationVersion() : null;

        Spec spec = new Spec();
        spec.addOption("tag", OptionType.STRING);
        spec.addOption("staticRoot", OptionType.STRING);
        YamcsServer.getServer().addConfigurationSection("yamcs-web", spec);
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

        // Set-up HTML5 deep-linking:
        // Catch any non-handled URL and make it return the contents of our index.html
        // This will cause initialization of the Angular app on any requested path. The
        // Angular router will interpret this and do client-side routing as needed.
        IndexHandler indexHandler = new IndexHandler(httpServer, webRoot);
        httpServer.addHandler("*", () -> indexHandler);

        // Print these log statements via a ready listener because it is more helpful
        // if they appear at the end of the boot log.
        YamcsServer.getServer().addReadyListener(() -> {
            if (httpServer.isHttpEnabled()) {
                log.info("Website deployed at {}", httpServer.getHttpBaseUri());
            }
            if (httpServer.isHttpsEnabled()) {
                log.info("Website deployed at {}", httpServer.getHttpsBaseUri());
            }
        });
    }
}
