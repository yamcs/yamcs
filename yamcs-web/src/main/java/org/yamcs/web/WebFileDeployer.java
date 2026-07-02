package org.yamcs.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
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

    private Thread redeployer;

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
                setupRedeployer();
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
        setupRedeployer();
    }

    public void redeploy() {
        try { // Silent redeploy
            prepareWebApplication();
        } catch (IOException | ParseException e) {
            log.error("Failed to deploy additional static roots", e);
        }
    }

    private synchronized void setupRedeployer() {
        if (redeployer != null) {
            redeployer.interrupt();
            try {
                redeployer.join();
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Watch for changes
        redeployer = new Redeployer(source, extraStaticRoots, target);
        redeployer.start();
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
            collectReferencedAssets(extraStaticRoot, cssFiles, jsFiles);
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

    private static final Pattern STYLESHEET_HREF_PATTERN = Pattern.compile("href=\"([^\"]+\\.css)\"");
    private static final Pattern SCRIPT_SRC_PATTERN = Pattern.compile("src=\"([^\"]+\\.js)\"");

    /**
     * Determines which JS/CSS files an extension's build actually references, by reading the asset
     * filenames straight out of its own generated index.html, rather than listing every *.js/*.css
     * file that happens to be sitting in its directory. Angular's watch mode doesn't always reliably
     * delete a previous build's hashed output once it's superseded (observed: a stale main-*.js
     * lingering for several minutes after a newer one was written, even though Angular's own
     * regenerated index.html only referenced the new one). Blindly listing the directory in that
     * situation would emit a &lt;script&gt; tag for both, and loading a stale bundle alongside the
     * current one throws when it tries to re-register the same custom element name.
     */
    private void collectReferencedAssets(Path extraStaticRoot, List<Path> cssFiles, List<Path> jsFiles)
            throws IOException {
        var indexFile = extraStaticRoot.resolve(PATH_INDEX);
        if (!Files.exists(indexFile)) {
            // Not an Angular build (no index.html to consult) -- fall back to a directory listing
            try (var listing = Files.list(extraStaticRoot)) {
                listing.forEachOrdered(file -> {
                    var lcFilename = file.getFileName().toString().toLowerCase();
                    if (lcFilename.endsWith(".css")) {
                        cssFiles.add(file);
                    } else if (lcFilename.endsWith(".js")) {
                        jsFiles.add(file);
                    }
                });
            }
            return;
        }

        var html = Files.readString(indexFile, UTF_8);
        for (var filename : extractAssetFilenames(html, STYLESHEET_HREF_PATTERN)) {
            cssFiles.add(extraStaticRoot.resolve(filename));
        }
        for (var filename : extractAssetFilenames(html, SCRIPT_SRC_PATTERN)) {
            jsFiles.add(extraStaticRoot.resolve(filename));
        }
    }

    private static List<String> extractAssetFilenames(String html, Pattern pattern) {
        var filenames = new ArrayList<String>();
        var matcher = pattern.matcher(html);
        while (matcher.find()) {
            filenames.add(matcher.group(1));
        }
        return filenames;
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
        private List<Path> extraStaticRoots;
        private Path target;

        private Redeployer(Path source, List<Path> extraStaticRoots, Path target) {
            this.source = source;
            this.extraStaticRoots = extraStaticRoots;
            this.target = target;
        }

        // How long to wait after the first detected change before redeploying, so that a burst of
        // several file writes from a single build (index.html, main-*.js, *.css, ...) is more likely
        // to have fully settled before we scan the directory, rather than catching it mid-write.
        private static final long SETTLE_DELAY_MILLIS = 250;

        // Upper bound on how long a poll cycle waits for an event before looping back around to
        // re-check which paths currently exist. This -- not a WatchKey ever reporting itself invalid
        // -- is what guarantees recovery: a directory that gets deleted and recreated out from under
        // an existing registration (e.g. a clean rebuild racing this watcher) is not reliably
        // reported as such by every WatchService implementation, so this loop can't wait indefinitely
        // for that signal. Re-deriving the watched set on a fixed cadence instead means recovery
        // never depends on it.
        private static final long POLL_TIMEOUT_MILLIS = 2000;

        @Override
        public void run() {
            WatchService watchService = null;
            var registeredPaths = List.<Path>of();
            var forceDeploy = false;
            // Whether we've ever registered a non-empty path set. Deliberately NOT set on the very
            // first registration below: the constructor already called prepareWebApplication()
            // synchronously before this thread was started, so an immediate redeploy here would be
            // redundant -- and worse, it would race setupRedeployer(), which is synchronized on this
            // WebFileDeployer and, immediately after starting this thread, may go on to interrupt()
            // and join() a *later* Redeployer generation while still holding that lock. If this
            // thread's first move were to block trying to acquire that same lock inside
            // prepareWebApplication(), the two threads would deadlock. Only a *later* re-registration
            // (paths disappeared and came back, e.g. a clean rebuild raced this watcher) represents
            // state we might have missed and need to force a catch-up deploy for.
            var everRegistered = false;
            try {
                while (true) {
                    var currentPaths = new ArrayList<Path>();
                    if (source != null && Files.exists(source)) {
                        currentPaths.add(source);
                    }
                    for (var extraStaticRoot : extraStaticRoots) {
                        if (Files.exists(extraStaticRoot)) {
                            currentPaths.add(extraStaticRoot);
                        }
                    }

                    if (!currentPaths.equals(registeredPaths)) {
                        if (watchService != null) {
                            try {
                                watchService.close();
                            } catch (IOException e) {
                                // Ignore; we're replacing it anyway
                            }
                        }
                        watchService = null;
                        registeredPaths = currentPaths;
                        forceDeploy = everRegistered;
                        everRegistered = true;

                        if (!currentPaths.isEmpty()) {
                            try {
                                // Assume all paths are on same file system
                                watchService = currentPaths.get(0).getFileSystem().newWatchService();
                                for (var path : currentPaths) {
                                    path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
                                }
                            } catch (IOException e) {
                                log.warn("Failed to set up a watch service for {}; will retry", currentPaths, e);
                                watchService = null;
                                registeredPaths = List.of();
                            }
                        }
                    }

                    if (watchService == null) {
                        Thread.sleep(500);
                        continue;
                    }

                    if (forceDeploy) {
                        forceDeploy = false;
                        redeployQuietly();
                    }

                    var key = watchService.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    if (key == null) {
                        // Nothing happened within the timeout; loop back around to re-check which
                        // paths currently exist rather than waiting on this WatchService any longer
                        continue;
                    }
                    key.pollEvents();

                    // Let a burst of writes from one build settle, then drain whatever else arrived
                    // meanwhile (on this key or any other watched path) before doing a single
                    // redeploy that reflects the (by then hopefully final) state.
                    Thread.sleep(SETTLE_DELAY_MILLIS);
                    WatchKey pending;
                    while ((pending = watchService.poll()) != null) {
                        pending.pollEvents();
                        if (pending != key) {
                            pending.reset();
                        }
                    }

                    redeployQuietly();
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Redeploys, logging (rather than propagating) failures so that a transient error -- e.g.
         * racing a build tool that is still mid-write -- doesn't permanently kill this thread and
         * leave the server stuck serving a stale deployment until restart.
         */
        private void redeployQuietly() {
            log.debug("Redeploying yamcs-web from {}", source);
            try {
                FileUtils.deleteContents(target);
                FileUtils.copyRecursively(source, target);
                prepareWebApplication();
            } catch (EOFException e) {
                // Ignore, expect another later watch event
            } catch (IOException | ParseException e) {
                log.warn("Failed to redeploy web application; will retry on next change", e);
            }
        }
    }
}
