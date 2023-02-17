package org.yamcs.filetransfer;

import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.ListFilesResponse;

import java.util.Map;
import java.util.Set;

public interface FileListingService {

    void registerRemoteFileListMonitor(RemoteFileListMonitor monitor);

    void unregisterRemoteFileListMonitor(RemoteFileListMonitor monitor);

    void notifyRemoteFileListMonitors(ListFilesResponse listFilesResponse);

    Set<RemoteFileListMonitor> getRemoteFileListMonitors();

    default void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
    }

    /**
     * Return latest file list of the given destination.
     *
     * @param source
     *         source requesting the file list (e.g. local entity for CFDP)
     * @param destination
     *         destination from which the file list is needed (e.g. remote entity for CFDP)
     * @param remotePath
     *         path on the destination from which to get the file list
     * @param options
     *         reliability of the file listing request (e.g. transmission mode for CFDP, may not be needed)
     * @return file list
     */
    // TODO: maybe move "reliable" into a map
    ListFilesResponse getFileList(String source, String destination, String remotePath, Map<String, Object> options);

    /**
     * Start fetching a new file list from remote.
     *
     * @param source
     *         source requesting the file list (e.g. local entity for CFDP)
     * @param destination
     *         destination from which the file list is needed (e.g. remote entity for CFDP)
     * @param remotePath
     *         path on the destination from which to get the file list
     * @param options
     *         reliability of the file listing request (e.g. transmission mode for CFDP, may not be needed)
     */
    void fetchFileList(String source, String destination, String remotePath, Map<String, Object> options);

    void saveFileList(ListFilesResponse listFilesResponse);
}
