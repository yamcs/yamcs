package org.yamcs.timeline;

public interface ItemListener {

    /**
     * An item was created
     */
    void onItemCreated(TimelineItem item);

    /**
     * An item was updated
     */
    void onItemUpdated(TimelineItem item);

    /**
     * An item was deleted
     */
    void onItemDeleted(TimelineItem item);
}
