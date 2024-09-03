package org.yamcs.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.yamcs.Experimental;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.api.ServerApi;
import org.yamcs.http.auth.AuthHandler;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementService;
import org.yamcs.templating.ParseException;
import org.yamcs.templating.TemplateProcessor;
import org.yamcs.utils.FileUtils;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.util.JsonFormat;

/**
 * Deploys web files from a source to a target directory, while tweaking some files.
 * <p>
 * The source is determined in order to be either:
 * 
 * <ul>
 * <li>(1) Check system property <code>yamcs.web.staticRoot</code>
 * <li>(2) Check a property in <code>etc/yamcs.yaml</code>
 * <li>(3) Load from classpath (packaged inside yamcs-web jar)
 * </ul>
 * 
 * A production deployment will use a precompiled web application, and use (3).
 * <p>
 * (1) and (2) are intended for enabling local development on the web sources. (1) allows doing so without needing to
 * modify the <code>etc/yamcs.yaml</code>.
 */
public class WebFileDeployer {

    private static final Log log = new Log(WebFileDeployer.class);
    public static final String PATH_INDEX_TEMPLATE = "index.template.html";
    public static final String PATH_INDEX = "index.html";
    public static final String PATH_NGSW = "ngsw.json";
    public static final String PATH_WEBMANIFEST = "manifest.webmanifest";

    // Optional, but immutable
    // (if null, webfiles are deployed from the classpath)
    private Path source;

    // Required, but with modified files
    private Path target;

    private List<Path> extraStaticRoots;
    private Map<String, Map<String, Object>> extraConfigs;

    private YConfiguration config;
    private String contextPath;

    public WebFileDeployer(String cacheKey, YConfiguration config, String contextPath, List<Path> extraStaticRoots,
            Map<String, Map<String, Object>> extraConfigs) throws IOException, ParseException {
        this.config = config;
        this.contextPath = contextPath;
        this.extraStaticRoots = extraStaticRoots;
        this.extraConfigs = extraConfigs;

        target = YamcsServer.getServer().getCacheDirectory().resolve(cacheKey);
        FileUtils.deleteRecursivelyIfExists(target);
        Files.createDirectory(target);

        var sourceOverride = System.getProperty("yamcs.web.staticRoot");
        if (sourceOverride != null) {
            source = Path.of(sourceOverride);
            source = source.toAbsolutePath().normalize();
        } else if (config.containsKey("staticRoot")) {
            source = Path.of(config.getString("staticRoot"));
            source = source.toAbsolutePath().normalize();
        }

        var deployed = false;
        if (source != null) {
            if (Files.exists(source)) {
                log.debug("Deploying yamcs-web from {}", source);
                FileUtils.copyRecursively(source, target);
                deployed = true;

                // Watch for changes
                new Redeployer(source, target).start();
            } else {
                log.warn("Static root for yamcs-web not found at '{}'", source);
            }
        }
        if (!deployed) {
            deployWebsiteFromClasspath(target);
        }

        prepareWebApplication();
    }

    /**
     * Returns the directory where files are deployed
     */
    public Path getDirectory() {
        return target;
    }

    @Experimental
    public List<Path> getExtraStaticRoots() {
        return extraStaticRoots;
    }

    @Experimental
    public void setExtraSources(List<Path> extraStaticRoots, Map<String, Map<String, Object>> extraConfigs) {
        this.extraStaticRoots = extraStaticRoots;
        this.extraConfigs = extraConfigs;
        redeploy();
    }

    public void redeploy() {
        try { // Silent redeploy
            prepareWebApplication();
        } catch (IOException | ParseException e) {
            log.error("Failed to deploy additional static roots", e);
        }
    }

    /**
     * Deploys all web files located in the classpath, as listed in a manifest.txt file. This file is generated during
     * the Maven build and enables us to skip having to do classpath listings.
     */
    private void deployWebsiteFromClasspath(Path target) throws IOException {
        try (var in = getClass().getResourceAsStream("/static/manifest.txt")) {
            if (in != null) {
                try (var reader = new InputStreamReader(in, UTF_8)) {

                    var manifest = CharStreams.toString(reader);
                    var staticFiles = manifest.split(";");

                    log.debug("Unpacking {} webapp files", staticFiles.length);
                    for (var staticFile : staticFiles) {
                        try (var resource = getClass().getResourceAsStream("/static/" + staticFile)) {
                            Files.createDirectories(target.resolve(staticFile).getParent());
                            Files.copy(resource, target.resolve(staticFile));
                        }
                    }
                }
            }
        }
    }

    private synchronized void prepareWebApplication() throws IOException, ParseException {
        // Keep track of SHA1 of modified files (for injection in ngsw.json)
        var hashTableOverrides = new HashMap<String, String>();

        var indexTemplateFile = target.resolve(PATH_INDEX_TEMPLATE);
        var indexFile = target.resolve(PATH_INDEX);
        if (Files.exists(indexTemplateFile)) {
            var content = renderIndex(indexTemplateFile);
            Files.writeString(indexFile, content, UTF_8);
            hashTableOverrides.put("/" + PATH_INDEX, calculateSha1(content));
        }
        var webManifestFile = target.resolve(PATH_WEBMANIFEST);
        if (Files.exists(webManifestFile)) {
            var content = renderWebManifest(webManifestFile);
            Files.writeString(webManifestFile, content, UTF_8);
            hashTableOverrides.put("/" + PATH_WEBMANIFEST, calculateSha1(content));
        }
        var ngswFile = target.resolve(PATH_NGSW);
        if (Files.exists(ngswFile)) {
            var ngswContent = renderNgsw(ngswFile, hashTableOverrides);
            Files.writeString(ngswFile, ngswContent, UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private String renderIndex(Path file) throws IOException, ParseException {
        var template = new String(Files.readAllBytes(file), UTF_8);

        var cssFiles = new ArrayList<Path>();
        var jsFiles = new ArrayList<Path>();
        for (var extraStaticRoot : extraStaticRoots) {
            try (var listing = Files.list(extraStaticRoot)) {
                listing.forEachOrdered(extensionFile -> {
                    var lcFilename = extensionFile.getFileName().toString().toLowerCase();
                    if (lcFilename.endsWith(".css")) {
                        cssFiles.add(extensionFile);
                    } else if (lcFilename.endsWith(".js")) {
                        jsFiles.add(extensionFile);
                    }
                });
            }
        }

        var extraHeaderHtml = new StringBuilder();
        for (var cssFile : cssFiles) {
            extraHeaderHtml.append("<link rel=\"stylesheet\" href=\"")
                    .append(contextPath)
                    .append("/")
                    .append(cssFile.getFileName())
                    .append("\">\n");
        }
        for (var jsFile : jsFiles) {
            extraHeaderHtml.append("<script src=\"")
                    .append(contextPath)
                    .append("/")
                    .append(jsFile.getFileName())
                    .append("\" type=\"module\"></script>\n");
        }

        extraHeaderHtml.append(config.getString("extraHeaderHTML", ""));

        // Don't use template for this, because Angular compiler messes up the HTML
        template = template.replace("<!--[[ EXTRA_HEADER_HTML ]]-->", extraHeaderHtml.toString());

        var webConfig = new HashMap<>(config.toMap());

        // Remove filesystem path from custom logo
        if (config.containsKey("logo")) {
            var logo = Path.of(config.getString("logo"));
            var filename = logo.getFileName().toString();
            webConfig.put("logo", contextPath + "/" + filename);
        }

        var authInfo = AuthHandler.createAuthInfo();
        var authJson = JsonFormat.printer().print(authInfo);
        var authMap = new Gson().fromJson(authJson, Map.class);
        webConfig.put("auth", authMap);

        var yamcs = YamcsServer.getServer();

        var pluginManager = yamcs.getPluginManager();
        var plugins = new ArrayList<String>();
        for (var plugin : pluginManager.getPlugins()) {
            var pluginName = pluginManager.getMetadata(plugin.getClass()).getName();
            plugins.add(pluginName);
        }
        webConfig.put("plugins", plugins);

        var commandOptions = new ArrayList<Map<String, Object>>();
        for (var option : yamcs.getCommandOptions()) {
            var json = JsonFormat.printer().print(ServerApi.toCommandOptionInfo(option));
            commandOptions.add(new Gson().fromJson(json, Map.class));
        }
        webConfig.put("commandOptions", commandOptions);
        webConfig.put("serverId", yamcs.getServerId());
        webConfig.put("hasTemplates", !yamcs.getInstanceTemplates().isEmpty());

        // Enable clearance-related UI only if there's potential for a processor
        // that has it enabled (we expect most people to not use this feature).
        var commandClearanceEnabled = ProcessorFactory.getProcessorTypes().entrySet().stream()
                .anyMatch(entry -> entry.getValue().checkCommandClearance());
        webConfig.put("commandClearanceEnabled", commandClearanceEnabled);

        // Make queue names directly available without API request. It is used
        // for populating a command history combo box.
        var queueNames = new TreeSet<String>(); // Sorted
        for (var qmanager : ManagementService.getInstance().getCommandQueueManagers()) {
            for (var queue : qmanager.getQueues()) {
                queueNames.add(queue.getName());
            }
        }
        webConfig.put("queueNames", queueNames);

        // May be used by web extensions to pass arbitrary information
        webConfig.put("extra", extraConfigs);

        var args = new HashMap<String, Object>();
        args.put("contextPath", contextPath);
        args.put("config", webConfig);
        args.put("configJson", new Gson().toJson(webConfig));
        return TemplateProcessor.process(template, args);
    }

    private String renderWebManifest(Path file) throws IOException, ParseException {
        var template = new String(Files.readAllBytes(file), UTF_8);
        var args = new HashMap<String, Object>();
        args.put("contextPath", contextPath);
        return TemplateProcessor.process(template, args);
    }

    private String renderNgsw(Path file, Map<String, String> hashTableOverrides) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try (var reader = Files.newBufferedReader(file, UTF_8)) {
            var jsonObject = gson.fromJson(reader, JsonObject.class);
            if (jsonObject == null) { // EOF
                throw new EOFException();
            }
            if (jsonObject.get("configVersion").getAsInt() != 1) {
                log.warn("Unexpected ngsw.json config version");
            }
            jsonObject.addProperty("index", contextPath + jsonObject.get("index").getAsString());

            for (var assetGroupEl : jsonObject.get("assetGroups").getAsJsonArray()) {
                var assetGroup = assetGroupEl.getAsJsonObject();

                var modifiedUrls = new JsonArray();
                for (var urlEl : assetGroup.get("urls").getAsJsonArray()) {
                    modifiedUrls.add(contextPath + urlEl.getAsString());
                }
                assetGroup.add("urls", modifiedUrls);
            }

            for (var dataGroupEl : jsonObject.get("dataGroups").getAsJsonArray()) {
                var dataGroup = dataGroupEl.getAsJsonObject();

                var modifiedPatterns = new JsonArray();
                for (var patternEl : dataGroup.get("patterns").getAsJsonArray()) {
                    modifiedPatterns.add(Pattern.quote(contextPath) + patternEl.getAsString());
                }
                dataGroup.add("patterns", modifiedPatterns);
            }

            var modifiedHashTable = new JsonObject();
            for (var hashEntry : jsonObject.get("hashTable").getAsJsonObject().entrySet()) {
                var sha1 = hashTableOverrides.getOrDefault(hashEntry.getKey(), hashEntry.getValue().getAsString());
                modifiedHashTable.addProperty(contextPath + hashEntry.getKey(), sha1);
            }
            jsonObject.add("hashTable", modifiedHashTable);
            return gson.toJson(jsonObject);
        }
    }

    private String calculateSha1(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            digest.update(content.getBytes(UTF_8));
            return String.format("%040x", new BigInteger(1, digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private class Redeployer extends Thread {

        private Path source;
        private Path target;

        private Redeployer(Path source, Path target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    if (Files.exists(source)) {
                        var watchService = source.getFileSystem().newWatchService();
                        source.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);

                        var forceDeploy = false;
                        var loop = true;
                        while (loop) {
                            var key = watchService.take();
                            if (forceDeploy || !key.pollEvents().isEmpty()) {
                                forceDeploy = false;

                                log.debug("Redeploying yamcs-web from {}", source);
                                FileUtils.deleteContents(target);
                                FileUtils.copyRecursively(source, target);
                                try {
                                    prepareWebApplication();
                                } catch (EOFException e) {
                                    // Ignore, expect another later watch event
                                }
                            }
                            loop = key.reset();
                        }

                        // If the source directory goes away (webapp rebuild),
                        // force a redeploy whenever the directory comes back.
                        forceDeploy = true;
                    } else {
                        Thread.sleep(500);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
