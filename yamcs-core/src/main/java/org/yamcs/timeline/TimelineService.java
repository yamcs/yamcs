package org.yamcs.timeline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.activities.ActivityService;
import org.yamcs.protobuf.TimelineSourceCapabilities;

import com.google.common.util.concurrent.ServiceManager;

/**
 * This service manages the Yamcs timeline.
 * <p>
 * The timeline is a collection of events or activities collectively called timeline items.
 * 
 * @author nm
 */
public class TimelineService extends AbstractYamcsService {

    public static final String RDB_TIMELINE_SOURCE = "rdb";
    public static final String COMMANDS_TIMELINE_SOURCE = "commands";

    private static final String CONFIG_ACTIVITIES = "activities";
    private static final String CONFIG_SCHEDULING = "scheduling";

    private Map<String, ItemProvider> timelineSources = new HashMap<>();

    private TimelineBandDb timelineBandDb;
    private TimelineViewDb timelineViewDb;
    private TimelineItemDb timelineItemDb;

    // Guava manager for sub-services
    private ServiceManager serviceManager;

    private ActivityService activityService = new ActivityService();
    private ActivityScheduler activityScheduler = new ActivityScheduler();

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption(CONFIG_ACTIVITIES, OptionType.MAP)
                .withSpec(activityService.getSpec())
                .withApplySpecDefaults(true);
        spec.addOption(CONFIG_SCHEDULING, OptionType.MAP)
                .withSpec(activityScheduler.getSpec())
                .withApplySpecDefaults(true);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        timelineItemDb = new TimelineItemDb(yamcsInstance);
        timelineSources.put(RDB_TIMELINE_SOURCE, timelineItemDb);
        timelineSources.put(COMMANDS_TIMELINE_SOURCE, new CommandItemProvider(yamcsInstance));
        timelineBandDb = new TimelineBandDb(yamcsInstance);
        timelineViewDb = new TimelineViewDb(yamcsInstance);

        var activityConfig = config.getConfigOrEmpty(CONFIG_ACTIVITIES);
        activityService.init(yamcsInstance, activityConfig);

        var schedulerConfig = config.getConfigOrEmpty(CONFIG_SCHEDULING);
        activityScheduler.init(this, schedulerConfig);
    }

    public Map<String, TimelineSourceCapabilities> getSources() {
        Map<String, TimelineSourceCapabilities> r = new HashMap<>();
        for (var entry : timelineSources.entrySet()) {
            r.put(entry.getKey(), entry.getValue().getCapabilities());
        }
        return r;
    }

    public ItemProvider getSource(String source) {
        return timelineSources.get(source);
    }

    public TimelineBandDb getTimelineBandDb() {
        return timelineBandDb;
    }

    public TimelineViewDb getTimelineViewDb() {
        return timelineViewDb;
    }

    public TimelineItemDb getTimelineItemDb() {
        return timelineItemDb;
    }

    public ActivityService getActivityService() {
        return activityService;
    }

    public ActivityScheduler getActivityScheduler() {
        return activityScheduler;
    }

    @Override
    protected void doStart() {
        serviceManager = new ServiceManager(Arrays.asList(activityService, activityScheduler));
        try {
            serviceManager.startAsync().awaitHealthy(10, TimeUnit.SECONDS);
            notifyStarted();
        } catch (TimeoutException e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        serviceManager.stopAsync();
        try {
            serviceManager.awaitStopped(5, TimeUnit.SECONDS);
            notifyStopped();
        } catch (TimeoutException e) {
            notifyFailed(e);
        }
    }
}
