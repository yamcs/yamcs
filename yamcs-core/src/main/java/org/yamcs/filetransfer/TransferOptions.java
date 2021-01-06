package org.yamcs.filetransfer;

public class TransferOptions {
    private boolean overwrite;
    boolean reliable;
    // used in CFDP to indicate that the receiving entity should acknowledge the reception of the file even if the
    // reliable is false
    boolean closureRequested;

    boolean createpath;

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
    }

    public boolean isReliable() {
        return reliable;
    }

    public void setClosureRequested(boolean closureRequested) {
        this.closureRequested = closureRequested;
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }
}
