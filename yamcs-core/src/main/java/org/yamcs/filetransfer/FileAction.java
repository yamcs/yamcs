package org.yamcs.filetransfer;

import org.yamcs.actions.Action;

public abstract class FileAction extends Action<String> {

    public FileAction(String id, String label) {
        super(id, label);
    }
}
