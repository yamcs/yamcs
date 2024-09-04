package org.yamcs.client;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.YamcsInstance.InstanceState;

public class InstanceFilter {

    private List<String> filterExpressions = new ArrayList<>();

    public void addLabel(String label, String value) {
        filterExpressions.add("label." + label + "=" + value);
    }

    public void setState(InstanceState state) {
        filterExpressions.add("state=" + state);
    }

    public void excludeState(InstanceState state) {
        filterExpressions.add("state!=" + state);
    }

    List<String> getFilterExpressions() {
        return filterExpressions;
    }
}
