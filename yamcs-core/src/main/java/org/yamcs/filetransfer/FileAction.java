package org.yamcs.filetransfer;

import org.yamcs.actions.Action;

public abstract class FileAction extends Action<FileActionIdentifier> {

    protected FileAction(String id, String label) {
        super(id, label);
    }
}
