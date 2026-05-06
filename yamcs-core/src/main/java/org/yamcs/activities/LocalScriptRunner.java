package org.yamcs.activities;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.utils.FileUtils;

/**
 * Built-in script runner, runs a local program.
 */
public class LocalScriptRunner implements ScriptRunner {

    public static final String LOCAL_SCRIPT_RUNNER_NAME = "Local";

    private List<Path> searchPath;
    private Map<String, String> fileAssociations = new HashMap<>();

    public LocalScriptRunner(YConfiguration options) {
        searchPath = options.<String> getList("searchPath").stream()
                .map(Path::of)
                .toList();

        fileAssociations.put("java", "java");
        fileAssociations.put("js", "node");
        fileAssociations.put("mjs", "node");
        fileAssociations.put("pl", "perl");
        fileAssociations.put("py", "python -u");
        fileAssociations.put("rb", "ruby");
        for (var assoc : options.<String, String> getMap("fileAssociations").entrySet()) {
            fileAssociations.put(assoc.getKey().toLowerCase(), assoc.getValue());
        }
    }

    @Override
    public String getName() {
        return LOCAL_SCRIPT_RUNNER_NAME;
    }

    @Override
    public CompletableFuture<List<String>> getScripts() {
        var exec = YamcsServer.getServer().getThreadPoolExecutor();
        return CompletableFuture.supplyAsync(() -> {
            var scripts = new ArrayList<String>();
            try {
                for (var scriptsDir : searchPath) {
                    if (Files.exists(scriptsDir)) {
                        try (var stream = Files.walk(scriptsDir, FOLLOW_LINKS)) {
                            stream.filter(path -> canExecute(path))
                                    .map(path -> scriptsDir.relativize(path).toString())
                                    .forEach(scripts::add);
                        }
                    }
                }
                Collections.sort(scripts);
                return scripts;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, exec);
    }

    private boolean canExecute(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (Files.isExecutable(file)) {
            return true;
        }

        // If there's a file association, the script may be non-executable
        var fileExtension = FileUtils.getFileExtension(file);
        if (fileExtension != null && fileAssociations.containsKey(fileExtension)) {
            return true;
        }

        return false;
    }

    @Override
    public LocalScriptRun createRun(String script, List<String> scriptArgs) {
        Path scriptFile;
        try {
            scriptFile = locateScript(script);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (scriptFile == null) {
            throw new IllegalArgumentException("Unexpected script '" + script + "'");
        }

        var program = scriptFile.toString();
        var fileExtension = FileUtils.getFileExtension(scriptFile);
        if (fileExtension != null) {
            var assoc = fileAssociations.get(fileExtension);
            if (assoc != null) {
                program = assoc + " " + scriptFile.toString();
            }
        }

        return new LocalScriptRun(program, scriptArgs);
    }

    private Path locateScript(String script) throws IOException {
        for (var scriptsDir : searchPath) {
            var path = scriptsDir.resolve(script);

            if (!path.normalize().toAbsolutePath().startsWith(scriptsDir.normalize().toAbsolutePath())) {
                throw new IOException("Directory traversal attempted: " + path);
            }

            if (canExecute(path)) {
                return path;
            }
        }
        return null;
    }
}
