package org.yamcs.archive;

import java.io.IOException;

import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.yarch.YarchException;

public interface TagDb {

    /**
     * Synchonously gets tags, passing every separate one to the provided
     * {@link TagReceiver}.
     */
    public void getTags(TimeInterval intv, TagReceiver callback) throws IOException;

    /**
     * Inserts a new Tag. No id should be specified. If it is, it will
     * silently be overwritten, and the new tag will be returned.
     * @throws YarchException 
     */
    public ArchiveTag insertTag(ArchiveTag tag) throws IOException ;


    /**
     * Updates an existing tag. The tag is fetched by the specified id
     * throws YamcsException if the tag could not be found.
     * <p>
     * Note that both tagId and oldTagStart need to be specified so that
     * a direct lookup in the internal data structure can be made. 
     */
    public ArchiveTag updateTag(long tagTime, int tagId, ArchiveTag tag) throws IOException, YamcsException ;

    /**
     * Deletes the specified tag
     * @throws YamcsException if the id was invalid, or if the tag could not be found
     */
    public ArchiveTag deleteTag(long tagTime, int tagId) throws IOException, YamcsException ;
}
