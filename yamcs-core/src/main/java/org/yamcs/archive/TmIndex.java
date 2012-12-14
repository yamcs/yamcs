package org.yamcs.archive;

import java.io.IOException;
import java.util.List;

import org.yamcs.yarch.StreamSubscriber;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Interface for (completeness) TmIndex.
 * All the implementing classes have to provide a constructor(String archiveInstance, boolean readonly)
 * 
 * @author nm
 *
 */
public interface TmIndex extends StreamSubscriber {

    public abstract void close() throws IOException;

    public abstract void deleteRecords(long start, long stop);
    
    /**
     * return an iterator that provides all the index entries between start and stop
     * 
     * @param names can be used to filter which entries are returned. If null, everything is returned. 
     * @param start
     * @param stop
     * @return
     */
    public abstract IndexIterator getIterator(List<NamedObjectId> names, long start, long stop);

}