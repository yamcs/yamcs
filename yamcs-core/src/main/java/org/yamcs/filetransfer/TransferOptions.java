package org.yamcs.filetransfer;

import org.yamcs.protobuf.FileTransferOption;

import java.util.ArrayList;
import java.util.List;

public class TransferOptions {
    private boolean overwrite;
    boolean reliable;
    private boolean reliableSet = false;
    // used in CFDP to indicate that the receiving entity should acknowledge the reception of the file even if the
    // reliable is false
    boolean closureRequested;

    boolean createpath;
    private boolean closureRequestedSet = false;

    private List<FileTransferOption> extraOptions = new ArrayList<>();

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public void setCreatePath(boolean createpath) {
        this.createpath = createpath;
    }

    public boolean isCreatePath() {
        return createpath;
    }

    public void setReliable(boolean reliable) {
        this.reliable = reliable;
        this.reliableSet = true; // Temporary solution
    }

    public boolean isReliable() {
        return reliable;
    }

    public boolean isReliableSet() {
        return reliableSet;
    }

    public void setClosureRequested(boolean closureRequested) {
        this.closureRequested = closureRequested;
        this.closureRequestedSet = true; // Temporary solution
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    public boolean isClosureRequestedSet() {
        return closureRequestedSet;
    }

    public void addExtraOptions(List<FileTransferOption> options) {
        this.extraOptions.addAll(options);
    }

    public List<FileTransferOption> getExtraOptions() {
        return extraOptions;
    }
}
