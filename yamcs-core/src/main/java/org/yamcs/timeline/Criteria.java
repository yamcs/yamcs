package org.yamcs.timeline;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.Filter;

public class Criteria {
    protected final TimeInterval interval;

    private List<Filter<TimelineItem>> filters = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public Criteria(TimeInterval interval) {
        this.interval = interval;
    }

    TimeInterval getTimeInterval() {
        return interval;
    }

    public void addFilterQuery(String filterQuery) {
        var filter = createFilter(filterQuery);
        filters.add(filter);
    }

    public Filter<TimelineItem> createFilter(String filterQuery) {
        return TimelineItemFilterFactory.create(filterQuery);
    }

    public List<String> getTags() {
        return tags;
    }

    public void addTags(List<String> tags) {
        this.tags.addAll(tags);
    }

    public boolean matches(TimelineItem item) {
        for (var filter : filters) {
            if (!filter.matches(item)) {
                return false;
            }
        }

        return true;
    }
}
