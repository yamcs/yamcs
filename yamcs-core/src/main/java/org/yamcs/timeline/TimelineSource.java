package org.yamcs.timeline;

import java.util.List;
import java.util.UUID;

import org.yamcs.http.BadRequestException;
import org.yamcs.protobuf.timeline.CreateItemRequest;
import org.yamcs.protobuf.timeline.ItemFilter;
import org.yamcs.protobuf.timeline.LogEntry;
import org.yamcs.protobuf.timeline.TimelineItemLog;
import org.yamcs.protobuf.timeline.TimelineSourceCapabilities;
import org.yamcs.protobuf.timeline.UpdateItemRequest;
import org.yamcs.timeline.db.AbstractItem;

public interface TimelineSource {

    /**
     * Returns an item or null if the item does not exist
     * 
     * @param id
     * @return
     */
    public TimelineItem getItem(String id);

    public void getItems(int limit, String next, RetrievalFilter filter, ItemListener consumer);

    /**
     * Create a new item and return it.
     * <p>
     * The returned value should have defaults (if any) filled in, also if the item has a relative time, the start time
     * of the returned value will be computed from the relative time and the start of
     * {@link AbstractItem#relativeItemUuid}
     */
    default TimelineItem createItem(String username, CreateItemRequest request) throws BadRequestException {
        throw new UnsupportedOperationException();
    }

    /**
     * Update an item and return the updated item.
     * <p>
     * The item parameter should have the uuid set and at least the start time or relative time
     * 
     * @return the updated item
     * @throws BadRequestException
     *             if the item does not exist, if the groupUuuid or relatimeTimeUuid properties create a circular
     *             dependency or other source specific error conditions
     */
    default TimelineItem updateItem(String username, UpdateItemRequest request) throws BadRequestException {
        throw new UnsupportedOperationException();
    }

    /**
     * Delete the item with the given uuid and return the deleted item.
     * <p>
     * If the item does not exist, return null
     */
    default AbstractItem deleteItem(UUID id) {
        throw new UnsupportedOperationException();
    }

    default AbstractItem deleteTimelineGroup(UUID id) {
        throw new UnsupportedOperationException();
    }

    public TimelineSourceCapabilities getCapabilities();

    /**
     * Checks that the source can filter based on the criteria specified
     */
    public void validateFilters(List<ItemFilter> filters) throws BadRequestException;

    /**
     * Returns the item log
     * 
     * @throws BadRequestException
     *             - if the item does not exist
     */
    default TimelineItemLog getItemLog(String id) {
        return TimelineItemLog.newBuilder().setId(id).build();
    }

    /**
     * Adds an entry to the log table
     * 
     * @throws BadRequestException
     *             - if the item does not exist
     */
    default LogEntry addItemLog(String id, LogEntry entry) {
        throw new UnsupportedOperationException();
    }

}
