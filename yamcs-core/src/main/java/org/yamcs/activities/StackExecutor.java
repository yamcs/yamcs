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
import org.yamcs.buckets.Bucket;
import org.yamcs.security.User;

public class StackExecutor implements ActivityExecutor {

    private String yamcsInstance;
    private ActivityService activityService;
    private Spec activitySpec;

    @Override
    public String getActivityType() {
        return "STACK";
    }

    @Override
    public String getDisplayName() {
        return "Stack";
    }

    @Override
    public String getDescription() {
        return "Run a stack.";
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
        activitySpec.addOption("processor", OptionType.STRING);
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
    public StackExecution createExecution(Activity activity, User caller) throws ValidationException {
        var args = getActivitySpec().validate(activity.getArgs());
        var processorName = YConfiguration.getString(args, "processor", null);
        var bucketName = YConfiguration.getString(args, "bucket");
        var stackName = YConfiguration.getString(args, "stack");

        // Default to the first processor with commanding enabled
        if (processorName == null) {
            var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
            for (var candidate : ysi.getProcessors()) {
                if (candidate.hasCommanding()) {
                    processorName = candidate.getName();
                    break;
                }
            }
        }

        var processor = YamcsServer.getServer().getProcessor(yamcsInstance, processorName);

        var bucketManager = YamcsServer.getServer().getBucketManager();
        Bucket bucket;
        try {
            bucket = bucketManager.getBucket(bucketName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new StackExecution(activityService, this, activity, processor, bucket, stackName, caller);
    }
}
