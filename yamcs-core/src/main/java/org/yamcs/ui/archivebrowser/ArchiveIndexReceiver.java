package org.yamcs.ui.archivebrowser;



import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeInterval;

public interface ArchiveIndexReceiver {
    public void setIndexListener(ArchiveIndexListener indexListener);
    public void getIndex(String instance, TimeInterval interval);
    public void getTag(String instance, TimeInterval interval);
    public void insertTag(String instance, Yamcs.ArchiveTag tag);
    public void updateTag(String instance, ArchiveTag oldTag, Yamcs.ArchiveTag tag);
    public void deleteTag(String instance, Yamcs.ArchiveTag tag);
    public boolean supportsTags();
}