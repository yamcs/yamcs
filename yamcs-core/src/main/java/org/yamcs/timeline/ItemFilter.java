package org.yamcs.timeline;

import java.util.List;

import org.yamcs.utils.TimeInterval;

public class ItemFilter {
    final TimeInterval interval;
    List<String> tags;

    public ItemFilter(TimeInterval interval) {
        this.interval = interval;
    }

    TimeInterval getTimeInterval() {
        return interval;
    }


    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
