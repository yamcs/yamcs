package org.yamcs.filetransfer;

import org.yamcs.protobuf.ListFilesResponse;

public interface RemoteFileListMonitor {
    void receivedFileList(ListFilesResponse fileList);
}
