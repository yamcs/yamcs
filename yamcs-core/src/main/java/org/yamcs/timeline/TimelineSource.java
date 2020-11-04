package org.yamcs.timeline;

import java.util.Collection;
import java.util.UUID;

import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.protobuf.TimelineSourceCapabilities;

public interface TimelineSource {
    /**
     * Add an item and return the added item.
     * <p>
     * The returned value should have defaults (if any) filled in, also if the item has a relative time, the start time
     * of the returned value will be computed from the relative time and the start of
     * {@link TimelineItem#relativeItemUuid}
     * 
     * @param item
     * @return
     */
    public TimelineItem addItem(TimelineItem item);

    /**
     * Update an item and return the updated item.
     * <p>
     * The item parameter should have the uuid set and at least the start time or relative time
     * 
     * @param item
     * @return the updated item
     * @throws InvalidRequestException
     *             if the item does not exist, if the groupUuuid or relatimeTimeUuid properties create a circular
     *             dependency or other source specific error conditions
     */
    public TimelineItem updateItem(TimelineItem item);

    /**
     * Delete the item with the given uuid and return the deleted item.
     * <p>
     * If the item does not exist, return null
     * 
     * @param uuid
     * @return
     */
    public TimelineItem deleteItem(UUID uuid);

    public TimelineItem deleteTimelineGroup(UUID uuid);

    public TimelineItem getItem(UUID uuid);

    public void getItems(int limit, String next, ItemFilter filter, ItemListener consumer);

    public Collection<String> getTags();

    public TimelineSourceCapabilities getCapabilities();
}
