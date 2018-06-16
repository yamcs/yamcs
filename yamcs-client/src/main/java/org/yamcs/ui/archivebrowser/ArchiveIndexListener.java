package org.yamcs.ui.archivebrowser;

import java.util.List;

import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;

public interface ArchiveIndexListener extends ConnectionListener {
    public void receiveArchiveRecords(IndexResult ir);
    public void receiveArchiveRecordsError(String errorMessage);
	public void receiveArchiveRecordsFinished();
	public void receiveTags(List<ArchiveTag> tagList);
    public void tagAdded(ArchiveTag ntag);
    public void tagRemoved(ArchiveTag rtag);
    public void tagChanged(ArchiveTag oldTag, ArchiveTag newTag);
    public void receiveTagsFinished();
}