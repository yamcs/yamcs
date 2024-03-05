package org.yamcs.activities;

import java.util.Collections;
import java.util.Map;

import org.yamcs.Spec;
import org.yamcs.Spec.NamedSpec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.security.User;

public class CommandExecutor implements ActivityExecutor {

    private String yamcsInstance;
    private ActivityService activityService;
    private Spec activitySpec;

    @Override
    public String getActivityType() {
        return "COMMAND";
    }

    @Override
    public String getDisplayName() {
        return "Command";
    }

    @Override
    public String getDescription() {
        return "Run a command.";
    }

    @Override
    public String getIcon() {
        return "rss_feed";
    }

    @Override
    public NamedSpec getSpec() {
        var spec = new NamedSpec("commandExecution");
        return spec;
    }

    @Override
    public void init(ActivityService activityService, YConfiguration options) {
        this.activityService = activityService;
        yamcsInstance = activityService.getYamcsInstance();

        activitySpec = new Spec();
        var processorOption = activitySpec.addOption("processor", OptionType.STRING);

        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        var processor = ysi.getFirstProcessor();
        if (processor != null && processor.hasCommanding()) {
            processorOption.withDefault(processor.getName());
        } else {
            processorOption.withRequired(true);
        }

        activitySpec.addOption("command", OptionType.STRING).withRequired(true);
        activitySpec.addOption("args", OptionType.MAP).withSpec(Spec.ANY);
        activitySpec.addOption("extra", OptionType.MAP).withSpec(Spec.ANY);
    }

    @Override
    public Spec getActivitySpec() {
        return activitySpec;
    }

    @Override
    public String describeActivity(Map<String, Object> args) {
        return YConfiguration.getString(args, "command");
    }

    @Override
    public CommandExecution createExecution(Activity activity, User caller) throws ValidationException {
        var args = getActivitySpec().validate(activity.getArgs());
        var processorName = YConfiguration.getString(args, "processor");
        var commandName = YConfiguration.getString(args, "command");

        Map<String, Object> commandArgs = Collections.emptyMap();
        if (args.containsKey("args")) {
            commandArgs = YConfiguration.getMap(args, "args");
        }

        Map<String, Object> commandExtra = Collections.emptyMap();
        if (args.containsKey("extra")) {
            commandExtra = YConfiguration.getMap(args, "extra");
        }

        var processor = YamcsServer.getServer().getProcessor(yamcsInstance, processorName);
        return new CommandExecution(activityService, this,
                activity, processor, commandName, commandArgs, commandExtra, caller);
    }
}
