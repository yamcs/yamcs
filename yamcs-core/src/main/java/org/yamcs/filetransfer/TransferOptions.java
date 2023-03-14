package org.yamcs.filetransfer;

import java.util.HashMap;
import java.util.Map;

public class TransferOptions {
    private boolean overwrite;
    boolean reliable;
    private boolean reliableSet = false;
    // used in CFDP to indicate that the receiving entity should acknowledge the reception of the file even if the
    // reliable is false
    boolean closureRequested;

    boolean createpath;
    private boolean closureRequestedSet = false;

    private final Map<String, Object> extraOptions = new HashMap<>();

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

    public void putExtraOptions(Map<String, Object> options) {
        extraOptions.putAll(options);
    }

    public Map<String, Object> getExtraOptions() {
        return extraOptions;
    }
}
