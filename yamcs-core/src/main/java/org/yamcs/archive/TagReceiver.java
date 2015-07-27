package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ArchiveTag;

/**
 * Used by TagDb to send back updates on fetched tags
 */
public interface TagReceiver {

    /**
     * A new tag matching the request was fetched from the tag db
     */
    void onTag(ArchiveTag tag);
    
    /**
     * Called when there are no more tags
     */
    void finished();
}
