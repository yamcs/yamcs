package org.yamcs.timeline;

import java.util.List;

import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;

public class TimelineItemFilter extends Filter<TimelineItem> {

    private static final String FIELD_LABEL = "label";
    private static final String FIELD_TAG = "tag";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "status";

    private String lcName;

    public TimelineItemFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addStringField(FIELD_LABEL, this::getLabel);
        addStringCollectionField(FIELD_TAG, this::getTags);
        addEnumField(FIELD_TYPE, TimelineItemType.class, this::getType);
        addEnumField(FIELD_STATUS, ExecutionStatus.class, this::getStatus);
        parse();
    }

    @Override
    public void beforeItem(TimelineItem item) {
        // Preload lowercase variants to boost non-field text search
        // with multiple terms

        // Reset previous state
        lcName = null;

        if (includesTextSearch()) {
            var itemName = item.getName();
            if (itemName != null) {
                lcName = itemName.toLowerCase();
            }
        }
    }

    private String getLabel(TimelineItem item) {
        return item.getName();
    }

    private List<String> getTags(TimelineItem item) {
        return item.getTags();
    }

    private TimelineItemType getType(TimelineItem item) {
        return item.getType();
    }

    private ExecutionStatus getStatus(TimelineItem item) {
        if (item instanceof TimelineActivity activity) {
            return activity.getStatus();
        } else {
            return null;
        }
    }

    @Override
    protected boolean matchesLiteral(TimelineItem item, String lowercaseLiteral) {
        return (lcName != null && lcName.contains(lowercaseLiteral));
    }
}
