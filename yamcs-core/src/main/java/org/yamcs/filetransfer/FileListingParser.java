package org.yamcs.filetransfer;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.RemoteFile;

import java.util.Comparator;
import java.util.List;

public abstract class FileListingParser {

    String yamcsInstance;
    YConfiguration config;

    Comparator<RemoteFile> fileDirComparator = (file1, file2) -> { // Sort by filename placing directories first
        int typeCmp = - Boolean.compare(file1.getIsDirectory(), file2.getIsDirectory());
        return typeCmp != 0 ? typeCmp : file1.getName().compareToIgnoreCase(file2.getName());
    };

    public abstract Spec getSpec();

    public void init(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    protected FileListingParser() {}

    public abstract List<RemoteFile> parse(String remotePath, String data);
}

