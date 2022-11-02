package org.yamcs.timeline;

import java.util.List;

import org.yamcs.protobuf.ItemFilter;
import org.yamcs.utils.TimeInterval;

public class RetrievalFilter {
    final TimeInterval interval;
    final List<ItemFilter> itemFilters;

    @Deprecated
    List<String> tags;

    public RetrievalFilter(TimeInterval interval, List<ItemFilter> itemFilters) {
        this.interval = interval;
        this.itemFilters = itemFilters;
    }

    TimeInterval getTimeInterval() {
        return interval;
    }

    public List<ItemFilter> getItemFilters() {
        return itemFilters;
    }

    @Deprecated
    public List<String> getTags() {
        return tags;
    }

    @Deprecated
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
