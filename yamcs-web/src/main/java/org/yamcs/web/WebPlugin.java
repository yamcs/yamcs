package org.yamcs.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.yamcs.Plugin;
import org.yamcs.PluginException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.io.CharStreams;

public class WebPlugin implements Plugin {

    private Log log = new Log(getClass());

    public WebPlugin() {
        Spec spec = new Spec();
        spec.addOption("tag", OptionType.STRING);
        spec.addOption("displayPath", OptionType.STRING);
        spec.addOption("stackPath", OptionType.STRING);
        spec.addOption("staticRoot", OptionType.STRING);
        spec.addOption("twoStageCommanding", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("commandClearances", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("completeness", OptionType.BOOLEAN).withDefault(false);

        Spec featuresSpec = new Spec();
        featuresSpec.addOption("cfdp", OptionType.BOOLEAN).withDefault(false);
        featuresSpec.addOption("dass", OptionType.BOOLEAN).withDefault(false);
        featuresSpec.addOption("layouts", OptionType.BOOLEAN).withDefault(false)
                .withDeprecationMessage("The layout functionality was removed");
        featuresSpec.addOption("tc", OptionType.BOOLEAN).withDefault(true);
        featuresSpec.addOption("tmArchive", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("features", OptionType.MAP)
                .withSpec(featuresSpec)
                .withApplySpecDefaults(true);

        Spec extraColumnSpec = new Spec();
        extraColumnSpec.addOption("id", OptionType.STRING).withRequired(true);
        extraColumnSpec.addOption("label", OptionType.STRING).withRequired(true);
        extraColumnSpec.addOption("after", OptionType.STRING).withRequired(true);
        extraColumnSpec.addOption("width", OptionType.STRING);

        Spec eventsSpec = new Spec();
        eventsSpec.addOption("extraColumns", OptionType.LIST).withElementType(OptionType.MAP).withSpec(extraColumnSpec);
        spec.addOption("events", OptionType.MAP).withSpec(eventsSpec);

        YamcsServer.getServer().addConfigurationSection("yamcs-web", spec);
    }

    @Override
    public void onLoad() throws PluginException {
        YConfiguration yamcsConfig = YamcsServer.getServer().getConfig();
        YConfiguration config = YConfiguration.emptyConfig();
        if (yamcsConfig.containsKey("yamcs-web")) {
            config = yamcsConfig.getConfig("yamcs-web");
        }

        YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        try {
            if (config.containsKey("displayPath")) {
                Path displayPath = Paths.get(config.getString("displayPath")).toAbsolutePath().normalize();
                yarch.addFileSystemBucket("displays", displayPath);
            } else {
                Bucket bucket = yarch.getBucket("displays");
                if (bucket == null) {
                    yarch.createBucket("displays");
                }
            }
        } catch (IOException e) {
            throw new PluginException("Could not create displays bucket", e);
        }

        try {
            if (config.containsKey("stackPath")) {
                Path stackPath = Paths.get(config.getString("stackPath")).toAbsolutePath().normalize();
                yarch.addFileSystemBucket("stacks", stackPath);
            } else {
                Bucket bucket = yarch.getBucket("stacks");
                if (bucket == null) {
                    yarch.createBucket("stacks");
                }
            }
        } catch (IOException e) {
            throw new PluginException("Could not create stacks bucket", e);
        }

        HttpServer httpServer = YamcsServer.getServer().getGlobalServices(HttpServer.class).get(0);

        // Deploy the website, either from classpath or from a manually
        // configured directory. The latter is primarily intended for
        // development work on the web interface.
        Path staticRoot;
        if (config.containsKey("staticRoot")) {
            staticRoot = Paths.get(config.getString("staticRoot"));
            staticRoot = staticRoot.toAbsolutePath().normalize();
        } else {
            try {
                staticRoot = deployWebsiteFromClasspath();
            } catch (IOException e) {
                throw new PluginException("Could not deploy website", e);
            }
        }
        if (Files.exists(staticRoot)) {
            log.debug("Serving yamcs-web from {}", staticRoot);
        } else {
            log.warn("Static root for yamcs-web not found at '{}'", staticRoot);
        }
        httpServer.addStaticRoot(staticRoot);

        // Set-up HTML5 deep-linking:
        // Catch any non-handled URL and make it return the contents of our index.html
        // This will cause initialization of the Angular app on any requested path. The
        // Angular router will interpret this and do client-side routing as needed.
        IndexHandler indexHandler = new IndexHandler(config, httpServer, staticRoot);
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

    /**
     * Deploys all web files located in the classpath, as listed in a manifest.txt file. This file is generated during
     * the Maven build and enables us to skip having to do classpath listings.
     */
    private Path deployWebsiteFromClasspath() throws IOException {
        Path cacheDir = YamcsServer.getServer().getCacheDirectory().resolve("yamcs-web");
        FileUtils.deleteRecursivelyIfExists(cacheDir);
        Files.createDirectory(cacheDir);
        try (InputStream in = getClass().getResourceAsStream("/static/manifest.txt");
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            String manifest = CharStreams.toString(reader);
            String[] staticFiles = manifest.split(";");

            log.debug("Unpacking {} webapp files", staticFiles.length);
            for (String staticFile : staticFiles) {
                try (InputStream resource = getClass().getResourceAsStream("/static/" + staticFile)) {
                    Files.copy(resource, cacheDir.resolve(staticFile));
                }
            }

            return cacheDir;
        }
    }
}
