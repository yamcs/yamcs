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
    static public final String RDB_TIMELINE_SOURCE = "rdb";

    Map<String, TimelineSource> timelineSources = new HashMap<>();
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

    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        timelineSources.put(RDB_TIMELINE_SOURCE, new TimelineItemDb(yamcsInstance));
        timelineBandDb = new TimelineBandDb(yamcsInstance);
        timelineViewDb = new TimelineViewDb(yamcsInstance);
    }

    public Map<String, TimelineSourceCapabilities> getSources() {
        Map<String, TimelineSourceCapabilities> r = new HashMap<>();
        for (Map.Entry<String, TimelineSource> me : timelineSources.entrySet()) {
            r.put(me.getKey(), me.getValue().getCapabilities());
        }
        return r;
    }

    public TimelineSource getSource(String source) {
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
