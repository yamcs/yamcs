package org.yamcs.timeline;

import java.util.List;
import java.util.UUID;

import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.http.BadRequestException;
import org.yamcs.protobuf.timeline.ItemFilter;
import org.yamcs.protobuf.timeline.LogEntry;
import org.yamcs.protobuf.timeline.TimelineItemLog;
import org.yamcs.protobuf.timeline.TimelineSourceCapabilities;

public interface TimelineSource {

    public TimelineItem getItem(String id);

    public void getItems(int limit, String next, RetrievalFilter filter, ItemListener consumer);

    /**
     * Add an item and return the added item.
     * <p>
     * The returned value should have defaults (if any) filled in, also if the item has a relative time, the start time
     * of the returned value will be computed from the relative time and the start of
     * {@link TimelineItem#relativeItemUuid}
     */
    default TimelineItem addItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }
    /**
     * Update an item and return the updated item.
     * <p>
     * The item parameter should have the uuid set and at least the start time or relative time
     * 
     * @return the updated item
     * @throws InvalidRequestException
     *             if the item does not exist, if the groupUuuid or relatimeTimeUuid properties create a circular
     *             dependency or other source specific error conditions
     */
    default TimelineItem updateItem(TimelineItem item) {
        throw new UnsupportedOperationException();
    }
    /**
     * Delete the item with the given uuid and return the deleted item.
     * <p>
     * If the item does not exist, return null
     */
    default TimelineItem deleteItem(UUID id) {
        throw new UnsupportedOperationException();
    }

    default TimelineItem deleteTimelineGroup(UUID id) {
        throw new UnsupportedOperationException();
    }

    public TimelineSourceCapabilities getCapabilities();

    /**
     * Checks that the source can filter based on the criteria specified
     */
    public void validateFilters(List<ItemFilter> filters) throws BadRequestException;

    /**
     * Returns the item log or null if the item does not exist
     */
    default TimelineItemLog getItemLog(String id) {
        return TimelineItemLog.newBuilder().setId(id).build();
    }

    /**
     * Adds an entry to the log table
     */
    default LogEntry addItemLog(String id, LogEntry entry) {
        throw new UnsupportedOperationException();
    }
}
