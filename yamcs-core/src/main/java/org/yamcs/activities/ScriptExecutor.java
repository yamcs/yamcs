package org.yamcs.activities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.Spec;
import org.yamcs.Spec.NamedSpec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.security.User;
import org.yamcs.utils.FileUtils;

public class ScriptExecutor implements ActivityExecutor {

    private ActivityService activityService;
    private List<Path> searchPath;
    private boolean impersonateCaller;
    private Map<String, String> fileAssociations = new HashMap<>();

    @Override
    public String getActivityType() {
        return "SCRIPT";
    }

    @Override
    public String getDisplayName() {
        return "Script";
    }

    @Override
    public String getDescription() {
        return "Run a script.";
    }

    @Override
    public String getIcon() {
        return "terminal";
    }

    @Override
    public NamedSpec getSpec() {
        var spec = new NamedSpec("scriptExecution");

        var yamcs = YamcsServer.getServer();
        spec.addOption("searchPath", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault(yamcs.getConfigDirectory().resolve("scripts").toString());

        spec.addOption("impersonateCaller", OptionType.BOOLEAN).withDefault(true);

        spec.addOption("fileAssociations", OptionType.MAP)
                .withSpec(Spec.ANY)
                .withApplySpecDefaults(true);
        return spec;
    }

    @Override
    public void init(ActivityService activityService, YConfiguration options) {
        this.activityService = activityService;
        searchPath = options.<String> getList("searchPath").stream()
                .map(Path::of)
                .collect(Collectors.toList());
        impersonateCaller = options.getBoolean("impersonateCaller");

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
    public Spec getActivitySpec() {
        var spec = new Spec();
        spec.addOption("processor", OptionType.STRING);
        spec.addOption("script", OptionType.STRING).withRequired(true);
        spec.addOption("args", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING);
        return spec;
    }

    @Override
    public String describeActivity(Map<String, Object> args) {
        return YConfiguration.getString(args, "script");
    }

    public List<String> getScripts() throws IOException {
        var scripts = new ArrayList<String>();
        for (var scriptsDir : searchPath) {
            if (Files.exists(scriptsDir)) {
                try (var stream = Files.walk(scriptsDir)) {
                    stream.filter(path -> canExecute(path))
                            .map(path -> scriptsDir.relativize(path).toString())
                            .forEach(scripts::add);
                }
            }
        }
        Collections.sort(scripts);
        return scripts;
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

    @Override
    public ScriptExecution createExecution(Activity activity, User caller) throws ValidationException {
        var args = getActivitySpec().validate(activity.getArgs());
        var processor = YConfiguration.getString(args, "processor", null);
        var script = YConfiguration.getString(args, "script");

        List<String> scriptArgs = new ArrayList<>();
        if (args.containsKey("args")) {
            scriptArgs = YConfiguration.<String> getList(args, "args");
        }

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

        var becomeUser = caller;
        if (!impersonateCaller) {
            becomeUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
        }

        return new ScriptExecution(activityService, this, activity, processor, program, scriptArgs, becomeUser);
    }
}
