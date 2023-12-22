package org.yamcs.client.mdb;

public class StreamMissionDatabaseOptions {

    private boolean includeSpaceSystems;
    private boolean includeContainers;
    private boolean includeParameters;
    private boolean includeParameterTypes;
    private boolean includeCommands;
    private boolean includeAlgorithms;

    public boolean isIncludeSpaceSystems() {
        return includeSpaceSystems;
    }

    public void setIncludeSpaceSystems(boolean includeSpaceSystems) {
        this.includeSpaceSystems = includeSpaceSystems;
    }

    public boolean isIncludeContainers() {
        return includeContainers;
    }

    public void setIncludeContainers(boolean includeContainers) {
        this.includeContainers = includeContainers;
    }

    public boolean isIncludeParameters() {
        return includeParameters;
    }

    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    public boolean isIncludeParameterTypes() {
        return includeParameterTypes;
    }

    public void setIncludeParameterTypes(boolean includeParameterTypes) {
        this.includeParameterTypes = includeParameterTypes;
    }

    public boolean isIncludeCommands() {
        return includeCommands;
    }

    public void setIncludeCommands(boolean includeCommands) {
        this.includeCommands = includeCommands;
    }

    public boolean isIncludeAlgorithms() {
        return includeAlgorithms;
    }

    public void setIncludeAlgorithms(boolean includeAlgorithms) {
        this.includeAlgorithms = includeAlgorithms;
    }
}
