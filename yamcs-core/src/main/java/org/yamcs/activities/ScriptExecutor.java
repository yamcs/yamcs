package org.yamcs.activities;

import static org.yamcs.activities.LocalScriptRunner.LOCAL_SCRIPT_RUNNER_NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.yamcs.Spec;
import org.yamcs.Spec.NamedSpec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.security.User;

public class ScriptExecutor implements ActivityExecutor {

    private ActivityService activityService;
    private boolean impersonateCaller;

    // Preserve definition order
    private Map<String, ScriptRunner> scriptRunners = new LinkedHashMap<>();

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

        var runnerFactoriesByType = new HashMap<String, ScriptRunnerFactory>();
        for (var factory : ServiceLoader.load(ScriptRunnerFactory.class)) {
            runnerFactoriesByType.put(factory.getType(), factory);
        }

        var runnersSpec = new Spec();
        runnersSpec.addOption("name", OptionType.STRING).withRequired(true);
        runnersSpec.addOption("type", OptionType.STRING)
                .withRequired(true)
                .withChoices(runnerFactoriesByType.keySet());

        for (var factory : runnerFactoriesByType.values()) {
            var argsSpec = factory.getSpec() != null ? factory.getSpec() : Spec.ANY;
            runnersSpec.when("type", factory.getType())
                    .addOption("args", OptionType.MAP)
                    .withSpec(argsSpec)
                    .withApplySpecDefaults(true);
        }

        spec.addOption("runners", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(runnersSpec)
                .withDefault(Collections.emptyList());

        spec.addOption("enableLocalRunner", OptionType.BOOLEAN).withDefault(true);
        return spec;
    }

    @Override
    public void init(ActivityService activityService, YConfiguration options) {
        this.activityService = activityService;

        impersonateCaller = options.getBoolean("impersonateCaller");

        if (options.getBoolean("enableLocalRunner")) {
            var localScriptRunner = new LocalScriptRunner(options);
            scriptRunners.put(localScriptRunner.getName(), localScriptRunner);
        }

        var runnerFactoriesByType = new HashMap<String, ScriptRunnerFactory>();
        for (var factory : ServiceLoader.load(ScriptRunnerFactory.class)) {
            runnerFactoriesByType.put(factory.getType(), factory);
        }

        for (var runnerConfig : options.getConfigList("runners")) {
            var name = runnerConfig.getString("name");
            var factory = runnerFactoriesByType.get(runnerConfig.getString("type"));
            var args = runnerConfig.getConfig("args");

            var scriptRunner = factory.createScriptRunner(name, args);
            if (scriptRunners.containsKey(name)) {
                throw new IllegalArgumentException("Runner names must be unique");
            }
            scriptRunners.put(name, scriptRunner);
        }
    }

    @Override
    public Spec getActivitySpec() {
        var spec = new Spec();
        spec.addOption("runner", OptionType.STRING);
        spec.addOption("processor", OptionType.STRING);
        spec.addOption("script", OptionType.STRING).withRequired(true);
        spec.addOption("args", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING);
        return spec;
    }

    @Override
    public String describeActivity(Map<String, Object> args) {
        return YConfiguration.getString(args, "script");
    }

    /**
     * Returns the available script runners in definition order
     */
    public List<ScriptRunner> getRunners() {
        return new ArrayList<>(scriptRunners.values());
    }

    /**
     * Returns the runner for the provided name.
     * <p>
     * If a null name is provided, this returns the "default" runner;
     * <ul>
     * <li>If there is only one runner: return that runner
     * <li>If there are multiple runners: return local runner (if it exists)
     * </ul>
     */
    public ScriptRunner getRunner(String name) {
        if (name == null) {
            if (scriptRunners.size() == 1) {
                return scriptRunners.values().iterator().next();
            } else {
                return scriptRunners.get(LOCAL_SCRIPT_RUNNER_NAME);
            }
        } else {
            return scriptRunners.get(name);
        }
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

        var becomeUser = caller;
        if (!impersonateCaller) {
            becomeUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
        }

        var runnerName = YConfiguration.getString(args, "runner", LOCAL_SCRIPT_RUNNER_NAME);
        var runner = scriptRunners.get(runnerName);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown runner '" + runnerName + "'");
        }

        return new ScriptExecution(activityService, this, activity, runner, processor, script, scriptArgs, becomeUser);
    }
}
