package org.yamcs.mdb;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;

/**
 * keeps track of the parameters and containers subscribed (because we only want to extract those)
 * 
 */
public class Subscription {
    private final Map<SequenceContainer, SubscribedContainer> containers = new HashMap<>();
    final static Logger log = LoggerFactory.getLogger(Subscription.class);

    Mdb mdb;

    Subscription(Mdb mdb) {
        this.mdb = mdb;
    }

    public SubscribedContainer addSequenceContainer(SequenceContainer containerDef) {
        SubscribedContainer subscribedContainer = containers.get(containerDef);
        if (subscribedContainer != null) {
            return subscribedContainer;
        }

        subscribedContainer = new SubscribedContainer(containerDef);
        containers.put(containerDef, subscribedContainer);

        // if there is a base container, add that one to the subscription and the parameters which have to be
        // extracted from the base in order to know if the inheritance condition applies
        SequenceContainer base = containerDef.getBaseContainer();
        if (base != null) {
            SubscribedContainer bases = addSequenceContainer(base);

            MatchCriteria mc = containerDef.getRestrictionCriteria();
            bases.addIneriting(subscribedContainer);

            if (mc != null) {
                for (Parameter p : mc.getDependentParameters()) {
                    addParameter(p);
                }
            }

        }
        // if this container is part of another containers through aggregation, then add those

        List<ContainerEntry> entries = mdb.getContainerEntries(containerDef);
        if (entries != null) {
            for (ContainerEntry ce : entries) {
                addSequenceEntry(ce);
            }
            if (containerDef.getSizeInBits() < 0) {
                // desperately add all parameters from this container in order for the parent to know the size
                addAll(containerDef);
            }
        }
        return subscribedContainer;
    }

    /**
     * Called in the cases when seq is part of other containers through aggregation. The parent container will need to
     * know the size of this one so we add all entries of seq.
     * 
     * @param seq
     */
    public void addAll(SequenceContainer seq) {
        SubscribedContainer subscr = containers.computeIfAbsent(seq, k -> new SubscribedContainer(k));
        subscr.addAllEntries();

        for (SequenceEntry se : seq.getEntryList()) {
            if (se instanceof ContainerEntry) {
                addAll(((ContainerEntry) se).getRefContainer());
            }
        }
        List<SequenceContainer> inheriting = mdb.getInheritingContainers(seq);
        if (inheriting != null) {
            for (SequenceContainer sc : inheriting) {
                addAll(sc);
                subscr.addIneriting(containers.get(sc));
            }
        }
    }

    public void addSequenceEntry(SequenceEntry se) {

        SubscribedContainer subscr = addSequenceContainer(se.getSequenceContainer());
        subscr.addEntry(se);

        SequenceContainer sctmp = se.getSequenceContainer();

        // if this entry's location is relative to the previous one, then we have to add also that one in the list
        if (se.getReferenceLocation() == SequenceEntry.ReferenceLocationType.PREVIOUS_ENTRY) {
            if (se.getIndex() > 0) {
                addSequenceEntry(sctmp.getEntryList().get(se.getIndex() - 1));
            } else { // continue with the basecontainer if we are at the first entry and go up in hierarchy skipping all
                     // containers with no entry
                do {
                    sctmp = sctmp.getBaseContainer();
                } while (sctmp != null && sctmp.getEntryList().size() == 0);
                if (sctmp != null) {
                    addSequenceEntry(sctmp.getEntryList().get(sctmp.getEntryList().size() - 1));
                }
            }
        }

        if ((se.getRepeatEntry() != null) && (se.getRepeatEntry().getCount() instanceof DynamicIntegerValue div)) {
            addParameter(div.getParameterInstanceRef().getParameter());
        }

        if (se instanceof ArrayParameterEntry ape) {
            for (IntegerValue iv : ape.getSize()) {
                if (iv instanceof DynamicIntegerValue) {
                    addParameter(((DynamicIntegerValue) iv).getParameterInstanceRef().getParameter());
                }
            }
        }
        if (se instanceof ContainerEntry ce) {
            addSequenceContainer(ce.getRefContainer());
        }

    }

    /**
     * Add to the subscription all the entries and containers on which this parameter depends
     * 
     * @param parameter
     */
    public void addParameter(Parameter parameter) {
        List<ParameterEntry> tpips = mdb.getParameterEntries(parameter);
        if (tpips != null) {
            for (ParameterEntry pe : tpips) {
                addSequenceEntry(pe);
            }
        }

        for (String ns : parameter.getAliasSet().getNamespaces()) {
            Collection<IndirectParameterRefEntry> c = mdb.getIndirectParameterRefEntries(ns);
            if (c != null) {
                for (IndirectParameterRefEntry ipre : c) {
                    addParameter(ipre.getParameterRef().getParameter());
                    addSequenceEntry(ipre);
                }
            }
        }
    }

    /**
     * Get the set of all containers subscribed
     * 
     * @return set of containers subscribed
     */
    public Collection<SequenceContainer> getContainers() {
        Set<SequenceContainer> r = new HashSet<SequenceContainer>();
        r.addAll(containers.keySet());
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Current list of parameter subscribed:\n");
        for (SubscribedContainer subscr : containers.values()) {
            sb.append(subscr.toString());
        }
        sb.append("-----------------------------------\n");

        return sb.toString();
    }

    public SubscribedContainer getSubscribedContainer(SequenceContainer containerDef) {
        return containers.get(containerDef);
    }

}
