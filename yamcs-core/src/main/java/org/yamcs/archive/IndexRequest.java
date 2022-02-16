package org.yamcs.archive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;

/**
 * Request index (histogram) information about tm packets, pp groups and commands
 */
public class IndexRequest {

    private String instance;
    private long start = TimeEncoding.INVALID_INSTANT;
    private long stop = TimeEncoding.INVALID_INSTANT;

    // namespace to use when sending all tm, pp or cmd (when using a filter, the namespace specified in the filter will
    // be used)
    // if not specified, the fully qualified canonical names will be sent
    private String defaultNamespace;

    // if true, all tm packets are sent, otherwise those in the tmPacket list (which can be empty)
    private boolean sendAllTm;
    private List<NamedObjectId> tmPackets = new ArrayList<>();

    // if true, all PP groups are sent, otherwise those in the ppGroup list (which can be empty)
    private boolean sendAllPp;
    private List<NamedObjectId> ppGroups = new ArrayList<>();

    // if true, all completeness groups are sent, otherwise those in the completenessGroups list (which can be empty)
    private boolean sendCompletenessIndex;
    private List<NamedObjectId> completenessGroups = new ArrayList<>();

    // if true, all command names are sent, otherwise those in the cmdName list (which can be empty)
    private boolean sendAllCmd;
    private List<NamedObjectId> commandNames = new ArrayList<>();

    // if true, all events are sent, otherwise those in the eventSource list (which can be empty)
    private boolean sendAllEvent;
    private List<NamedObjectId> eventSources = new ArrayList<>();

    private int mergeTime = -1;

    public IndexRequest(String instance) {
        this.instance = instance;
    }

    public String getInstance() {
        return instance;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getStop() {
        return stop;
    }

    public void setStop(long stop) {
        this.stop = stop;
    }

    public int getMergeTime() {
        return mergeTime;
    }

    public void setMergeTime(int mergeTime) {
        this.mergeTime = mergeTime;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public boolean isSendAllTm() {
        return sendAllTm;
    }

    public void setSendAllTm(boolean sendAllTm) {
        this.sendAllTm = sendAllTm;
    }

    public List<NamedObjectId> getTmPackets() {
        return tmPackets;
    }

    public boolean isSendAllPp() {
        return sendAllPp;
    }

    public void setSendAllPp(boolean sendAllPp) {
        this.sendAllPp = sendAllPp;
    }

    public List<NamedObjectId> getPpGroups() {
        return ppGroups;
    }

    public boolean isSendCompletenessIndex() {
        return sendCompletenessIndex;
    }

    public void setSendCompletenessIndex(boolean sendCompletenessIndex) {
        this.sendCompletenessIndex = sendCompletenessIndex;
    }

    public List<NamedObjectId> getCompletenessGroups() {
        return completenessGroups;
    }

    public boolean isSendAllCmd() {
        return sendAllCmd;
    }

    public void setSendAllCmd(boolean sendAllCmd) {
        this.sendAllCmd = sendAllCmd;
    }

    public List<NamedObjectId> getCommandNames() {
        return commandNames;
    }

    public boolean isSendAllEvent() {
        return sendAllEvent;
    }

    public void setSendAllEvent(boolean sendAllEvent) {
        this.sendAllEvent = sendAllEvent;
    }

    public List<NamedObjectId> getEventSources() {
        return eventSources;
    }
}
