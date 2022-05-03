package org.yamcs.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;

public class SubscribedContainer {
    final SequenceContainer conainerDef;
    List<SequenceEntry> entries = new ArrayList<>();

    List<InheritingContainer> inheritingContainers = new ArrayList<>();
    boolean allEntriesAdded = false;

    public SubscribedContainer(SequenceContainer sc) {
        this.conainerDef = sc;
    }

    public void addEntry(SequenceEntry se) {
        if (allEntriesAdded) {
            return;
        }

        int idx = Collections.binarySearch(entries, se);
        if (idx < 0) {
            entries.add(-idx - 1, se);
        }
    }

    public void addAllEntries() {
        if (allEntriesAdded) {
            return;
        }
        entries = conainerDef.getEntryList();
    }

    public void addIneriting(SubscribedContainer child) {
        if (!inheritingContainers.stream().anyMatch(ic -> ic.container == child)) {
            inheritingContainers.add(new InheritingContainer(child, child.conainerDef.getRestrictionCriteria()));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(conainerDef);
        sb.append(" with entries:\n");
        for (SequenceEntry se : entries) {
            sb.append("\t").append(se).append("\n");
        }
        return sb.toString();
    }

    static class InheritingContainer {
        final SubscribedContainer container;
        final MatchCriteriaEvaluator criteriaEvaluator;

        public InheritingContainer(SubscribedContainer container, MatchCriteria matchCriteria) {
            this.container = container;
            criteriaEvaluator = matchCriteria == null ? MatchCriteriaEvaluatorFactory.ALWAYS_MATCH
                    : MatchCriteriaEvaluatorFactory.getEvaluator(matchCriteria);
        }

        public MatchResult matches(ProcessingData data) {
            return criteriaEvaluator.evaluate(data);
        }
    }
}
