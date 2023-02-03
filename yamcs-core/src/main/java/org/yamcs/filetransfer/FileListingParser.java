package org.yamcs.filetransfer;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.RemoteFile;

import java.util.Comparator;
import java.util.List;

/**
 * Interface for retrieving and saving the list of files of a certain remote directory.
 */
public abstract class FileListingParser {

    String yamcsInstance;
    YConfiguration config;

    /**
     * Comparator to compare 2 RemoteFile lexicographically by file name and by placing directories first
     */
    Comparator<RemoteFile> fileDirComparator = (file1, file2) -> { // Sort by filename placing directories first
        int typeCmp = - Boolean.compare(file1.getIsDirectory(), file2.getIsDirectory());
        return typeCmp != 0 ? typeCmp : file1.getName().compareToIgnoreCase(file2.getName());
    };

    public abstract Spec getSpec();

    public void init(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    /**
     * Parse the provided text data into a list of RemoteFiles
     *
     * @param remotePath remote path where the file listing is located
     * @param data text data (e.g. coming from a file)
     * @return parsed remote files and directories
     */
    public abstract List<RemoteFile> parse(String remotePath, byte[] data);
}

