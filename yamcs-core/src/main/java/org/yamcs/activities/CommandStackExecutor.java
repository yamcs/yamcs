package org.yamcs.activities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.yamcs.Spec;
import org.yamcs.Spec.NamedSpec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.security.User;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchDatabase;

public class CommandStackExecutor implements ActivityExecutor {

    private String yamcsInstance;
    private ActivityService activityService;
    private Spec activitySpec;

    @Override
    public String getActivityType() {
        return "COMMAND_STACK";
    }

    @Override
    public String getDisplayName() {
        return "Command Stack";
    }

    @Override
    public String getDescription() {
        return "Run a command stack.";
    }

    @Override
    public String getIcon() {
        return "rss_feed";
    }

    @Override
    public NamedSpec getSpec() {
        var spec = new NamedSpec("stackExecution");
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

        activitySpec.addOption("bucket", OptionType.STRING).withRequired(true);
        activitySpec.addOption("stack", OptionType.STRING).withRequired(true);
    }

    @Override
    public Spec getActivitySpec() {
        return activitySpec;
    }

    @Override
    public String describeActivity(Map<String, Object> args) {
        return "ys://" + YConfiguration.getString(args, "bucket") + "/" + YConfiguration.getString(args, "stack");
    }

    @Override
    public CommandStackExecution createExecution(Activity activity, User caller) throws ValidationException {
        var args = getActivitySpec().validate(activity.getArgs());
        var processorName = YConfiguration.getString(args, "processor");
        var bucketName = YConfiguration.getString(args, "bucket");
        var stackName = YConfiguration.getString(args, "stack");
        var processor = YamcsServer.getServer().getProcessor(yamcsInstance, processorName);

        var yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        Bucket bucket;
        try {
            bucket = yarch.getBucket(bucketName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new CommandStackExecution(activityService, this, activity, processor, bucket, stackName, caller);
    }
}
