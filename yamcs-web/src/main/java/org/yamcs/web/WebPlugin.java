package org.yamcs.web;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.yamcs.Plugin;
import org.yamcs.PluginException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.io.CharStreams;

public class WebPlugin implements Plugin {

    static final String CONFIG_SECTION = "yamcs-web";
    static final String CONFIG_DISPLAY_BUCKET = "displayBucket";
    static final String CONFIG_STACK_BUCKET = "stackBucket";

    /**
     * Allows access to the Admin Area.
     * <p>
     * Remark that certain pages in the Admin Area may require further privileges.
     */
    public static final SystemPrivilege PRIV_ADMIN = new SystemPrivilege("web.AccessAdminArea");

    private Log log = new Log(getClass());

    @Override
    public void onLoad(YConfiguration config) throws PluginException {
        var yamcs = YamcsServer.getServer();
        yamcs.getSecurityStore().addSystemPrivilege(PRIV_ADMIN);

        createBuckets(config);
        var staticRoot = setupWebFiles(config);
        setupRoutes(config, staticRoot);

        // Print these log statements via a ready listener because it is more helpful
        // if they appear at the end of the boot log.
        yamcs.addReadyListener(() -> {
            var httpServer = yamcs.getGlobalService(HttpServer.class);
            for (var binding : httpServer.getBindings()) {
                log.info("Website deployed at {}{}", binding, httpServer.getContextPath());
            }
        });
    }

    private void createBuckets(YConfiguration config) throws PluginException {
        var displayBucketName = config.getString(CONFIG_DISPLAY_BUCKET);
        createBucketIfNotExists(displayBucketName);

        var stackBucketName = config.getString(CONFIG_STACK_BUCKET);
        createBucketIfNotExists(stackBucketName);

        // Buckets can be overriden at instance level. If so, create those
        // buckets here. It is this WebPlugin that owns them.
        ManagementService.getInstance().addManagementListener(new ManagementListener() {
            @Override
            public void instanceStateChanged(YamcsServerInstance ysi) {
                if (ysi.state() == InstanceState.STARTING) {
                    var instanceConfig = ysi.getConfig().getConfigOrEmpty(CONFIG_SECTION);
                    if (instanceConfig.containsKey(CONFIG_DISPLAY_BUCKET)) {
                        var bucketName = instanceConfig.getString(CONFIG_DISPLAY_BUCKET);
                        try {
                            createBucketIfNotExists(bucketName);
                        } catch (PluginException e) {
                            log.error("Could not create display bucket for instance '" + ysi.getName() + "'", e);
                        }
                    }
                    if (instanceConfig.containsKey(CONFIG_STACK_BUCKET)) {
                        var bucketName = instanceConfig.getString(CONFIG_STACK_BUCKET);
                        try {
                            createBucketIfNotExists(bucketName);
                        } catch (PluginException e) {
                            log.error("Could not create stack bucket for instance '" + ysi.getName() + "'", e);
                        }
                    }
                }
            }
        });
    }

    private Bucket createBucketIfNotExists(String bucketName) throws PluginException {
        var yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        try {
            var bucket = yarch.getBucket(bucketName);
            if (bucket == null) {
                bucket = yarch.createBucket(bucketName);
            }
            return bucket;
        } catch (IOException e) {
            throw new PluginException("Could not create '" + bucketName + "' bucket", e);
        }
    }

    /**
     * Deploy the website, for practical reasons we have three different mechanisms. In order:
     * 
     * <ul>
     * <li>(1) Check a system property yamcs.web.staticRoot
     * <li>(2) Check a property in yamcs.yaml
     * <li>(3) Load from classpath (packaged inside yamcs-web)
     * </ul>
     * 
     * End-users should use (3). (1) and (2) make it possible to develop on the web sources. (1) Allows doing so without
     * needing to modify the yamcs.yaml
     */
    private Path setupWebFiles(YConfiguration config) throws PluginException {
        Path staticRoot;
        var staticRootOverride = System.getProperty("yamcs.web.staticRoot");
        if (staticRootOverride != null) {
            staticRoot = Path.of(staticRootOverride);
            staticRoot = staticRoot.toAbsolutePath().normalize();
        } else if (config.containsKey("staticRoot")) {
            staticRoot = Path.of(config.getString("staticRoot"));
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

        return staticRoot;
    }

    /**
     * Deploys all web files located in the classpath, as listed in a manifest.txt file. This file is generated during
     * the Maven build and enables us to skip having to do classpath listings.
     */
    private Path deployWebsiteFromClasspath() throws IOException {
        var cacheDir = YamcsServer.getServer().getCacheDirectory().resolve(CONFIG_SECTION);
        FileUtils.deleteRecursivelyIfExists(cacheDir);
        Files.createDirectory(cacheDir);
        try (var in = getClass().getResourceAsStream("/static/manifest.txt");
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            var manifest = CharStreams.toString(reader);
            var staticFiles = manifest.split(";");

            log.debug("Unpacking {} webapp files", staticFiles.length);
            for (var staticFile : staticFiles) {
                try (var resource = getClass().getResourceAsStream("/static/" + staticFile)) {
                    Files.copy(resource, cacheDir.resolve(staticFile));
                }
            }

            return cacheDir;
        }
    }

    /**
     * Add routes used by Web UI.
     */
    private void setupRoutes(YConfiguration config, Path staticRoot) throws PluginException {
        var httpServer = YamcsServer.getServer().getGlobalService(HttpServer.class);
        httpServer.addStaticRoot(staticRoot);

        // Set-up HTML5 deep-linking:
        // Catch any non-handled URL and make it return the contents of our index.html
        // This will cause initialization of the Angular app on any requested path. The
        // Angular router will interpret this and do client-side routing as needed.
        var indexHandler = new IndexHandler(config, httpServer, staticRoot);
        httpServer.addHandler("*", () -> indexHandler);

        // Serve a logo image, if so configured
        if (config.containsKey("logo")) {
            var file = Path.of(config.getString("logo"));
            var filename = file.getFileName().toString();
            httpServer.addHandler(filename, () -> new LogoHandler(file));
        }

        // Additional API Routes
        try (var in = getClass().getResourceAsStream("/yamcs-web.protobin")) {
            httpServer.getProtobufRegistry().importDefinitions(in);
        } catch (IOException e) {
            throw new PluginException(e);
        }
        httpServer.addApi(new WebApi());
    }
}
