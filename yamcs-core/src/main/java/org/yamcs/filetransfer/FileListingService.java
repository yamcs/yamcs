package org.yamcs.filetransfer;

import java.util.Map;
import java.util.Set;

import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.ListFilesResponse;

public interface FileListingService {

    default void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
    }

    void registerRemoteFileListMonitor(RemoteFileListMonitor monitor);

    void unregisterRemoteFileListMonitor(RemoteFileListMonitor monitor);

    void notifyRemoteFileListMonitors(ListFilesResponse listFilesResponse);

    Set<RemoteFileListMonitor> getRemoteFileListMonitors();

    /**
     * Return latest file list of the given destination.
     *
     * @param source
     *            source requesting the file list (e.g. local entity for CFDP)
     * @param destination
     *            destination from which the file list is needed (e.g. remote entity for CFDP)
     * @param remotePath
     *            path on the destination from which to get the file list
     * @param options
     *            reliability of the file listing request (e.g. transmission mode for CFDP, may not be needed)
     * @return file list
     */
    ListFilesResponse getFileList(String source, String destination, String remotePath, Map<String, Object> options);

    /**
     * Start fetching a new file list from remote.
     *
     * @param source
     *            source requesting the file list (e.g. local entity for CFDP)
     * @param destination
     *            destination from which the file list is needed (e.g. remote entity for CFDP)
     * @param remotePath
     *            path on the destination from which to get the file list
     * @param options
     *            reliability of the file listing request (e.g. transmission mode for CFDP, may not be needed)
     */
    void fetchFileList(String source, String destination, String remotePath, Map<String, Object> options);

    void saveFileList(ListFilesResponse listFilesResponse);
}
