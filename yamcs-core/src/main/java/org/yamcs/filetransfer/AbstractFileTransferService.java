package org.yamcs.filetransfer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.actions.ActionHelper;
import org.yamcs.protobuf.FileTransferCapabilities;

public abstract class AbstractFileTransferService extends AbstractYamcsService
        implements FileTransferService, FileActionProvider {

    private Map<String, FileAction> fileActions = new LinkedHashMap<>(); // Keep them in order of registration

    protected void addFileAction(FileAction action) {
        if (fileActions.containsKey(action.getId())) {
            throw new IllegalArgumentException("Action '" + action.getId() + "' already registered");
        }
        fileActions.put(action.getId(), action);
    }

    @Override
    public List<FileAction> getFileActions() {
        return new ArrayList<>(fileActions.values());
    }

    @Override
    public FileAction getFileAction(String actionId) {
        return fileActions.get(actionId);
    }

    @Override
    public final FileTransferCapabilities getCapabilities() {
        var b = FileTransferCapabilities.newBuilder();
        for (var action : fileActions.values()) {
            var actionInfo = ActionHelper.toActionInfo(action);
            b.addFileActions(actionInfo);
        }

        addCapabilities(b);
        return b.build();
    }

    protected abstract void addCapabilities(FileTransferCapabilities.Builder builder);
}
