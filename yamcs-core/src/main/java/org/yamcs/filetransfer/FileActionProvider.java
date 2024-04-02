package org.yamcs.filetransfer;

import java.util.List;

public interface FileActionProvider {

    List<FileAction> getFileActions();

    FileAction getFileAction(String actionId);
}
