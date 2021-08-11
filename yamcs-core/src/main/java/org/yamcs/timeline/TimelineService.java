package org.yamcs.timeline;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.TimelineSourceCapabilities;

/**
 * This service manages the Yamcs timeline.
 * <p>
 * The timeline is a collection of events or activities collectively called timeline items.
 * 
 * 
 * @author nm
 *
 */
public class TimelineService extends AbstractYamcsService {
    public static final String RDB_TIMELINE_SOURCE = "rdb";
    public static final String COMMANDS_TIMELINE_SOURCE = "commands";

    Map<String, ItemProvider> timelineSources = new HashMap<>();
    TimelineBandDb timelineBandDb;
    TimelineViewDb timelineViewDb;

    public TimelineBandDb getTimelineBandDb() {
        return timelineBandDb;
    }

    public void setTimelineBandDb(TimelineBandDb timelineBandDb) {
        this.timelineBandDb = timelineBandDb;
    }

    public TimelineViewDb getTimelineViewDb() {
        return timelineViewDb;
    }

    public void setTimelineViewDb(TimelineViewDb timelineViewDb) {
        this.timelineViewDb = timelineViewDb;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        timelineSources.put(RDB_TIMELINE_SOURCE, new TimelineItemDb(yamcsInstance));
        timelineSources.put(COMMANDS_TIMELINE_SOURCE, new CommandItemProvider(yamcsInstance));
        timelineBandDb = new TimelineBandDb(yamcsInstance);
        timelineViewDb = new TimelineViewDb(yamcsInstance);
    }

    public Map<String, TimelineSourceCapabilities> getSources() {
        Map<String, TimelineSourceCapabilities> r = new HashMap<>();
        for (Map.Entry<String, ItemProvider> me : timelineSources.entrySet()) {
            r.put(me.getKey(), me.getValue().getCapabilities());
        }
        return r;
    }

    public ItemProvider getSource(String source) {
        return timelineSources.get(source);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
