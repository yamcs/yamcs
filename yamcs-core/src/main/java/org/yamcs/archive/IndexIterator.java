package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ArchiveRecord;

public interface IndexIterator {

    public abstract ArchiveRecord getNextRecord();

    public abstract void close();
}