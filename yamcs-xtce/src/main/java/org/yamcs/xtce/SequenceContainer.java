package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.yamcs.xtce.AncillaryData.*;

/**
 * A list of raw parameters, parameter segments, stream segments, containers, or container segments.
 * Sequence containers may inherit from other sequence containers; when they do, the sequence in the parent
 * SequenceContainer is 'inherited' and if the location of entries in the child sequence is not specified,
 * it is assumed to start where the parent sequence ended. Parent sequence containers may be marked as "abstract".
 * The idle pattern is part of any unallocated space in the Container.
 */
public class SequenceContainer extends Container {
    private static final long serialVersionUID = 5L;

    List<SequenceEntry> entryList = new ArrayList<>();

    public SequenceContainer(String name) {
        super(name);
    }

    /**
     * Use the qualified name of this container as packet name (stored in the pname column of the tm table).
     * <p>
     * If this property is set, this container name will be used as packet name if the packet doesn't match any
     * inherited container with the property set
     * 
     */
    private boolean useAsPacketName = false;

    /**
     * If this is true, the {@link #useAsPacketName} flag will be set automatically after all the MDB has been loaded.
     */
    private boolean autoPacketName = false;

    @Override
    public void addAncillaryData(AncillaryData data) {
        super.addAncillaryData(data);
        if (isPacketNameProperty(data)) {
            this.useAsPacketName = true;
        }

    }

    @Override
    public void setAncillaryData(List<AncillaryData> ancillaryData) {
        super.setAncillaryData(ancillaryData);

        Optional<?> o = ancillaryData.stream().filter(ad -> isPacketNameProperty(ad)).findAny();
        if (o.isPresent()) {
            this.useAsPacketName = true;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isPacketNameProperty(AncillaryData ad) {
        return KEY_YAMCS.equalsIgnoreCase(ad.getName())
                && (PROP_USE_AS_PACKET_NAME.equalsIgnoreCase(ad.getValue())
                        || PROP_USE_AS_ARCHIVING_PARTITION.equalsIgnoreCase(ad.getValue()));
    }

    public SequenceContainer getBaseContainer() {
        return (SequenceContainer) baseContainer;
    }

    public void setBaseContainer(Container baseContainer) {
        if (baseContainer instanceof SequenceContainer) {
            this.baseContainer = (SequenceContainer) baseContainer;
        } else {
            throw new IllegalArgumentException("The SequenceContainer expects a SequenceContainer as base container");
        }
    }

    public MatchCriteria getRestrictionCriteria() {
        return restrictionCriteria;
    }

    /**
     * Add single entry to list of entries
     * 
     * @param entry
     *            Entry to be added
     */
    public void addEntry(SequenceEntry entry) {
        entryList.add(entry);
        entry.setIndex(entryList.size() - 1);
        entry.setContainer(this);
    }

    /**
     * Insert the given entry in position idx. Shift all the subsequent entries to the right.
     * 
     * @param idx
     * @param entry
     */
    public void insertEntry(int idx, SequenceEntry entry) {
        entryList.add(idx, entry);
        for (int i = idx; i < entryList.size(); i++) {
            SequenceEntry se = entryList.get(i);
            se.setIndex(i);
            se.setContainer(this);
        }
    }

    public void setEntryList(List<SequenceEntry> entryList) {
        this.entryList = entryList;
        for (int i = 0; i < entryList.size(); i++) {
            SequenceEntry entry = entryList.get(i);
            entry.setIndex(i);
            entry.setContainer(this);
        }
    }

    /**
     * Returns the list of the entries in the sequence container. The list is unmodifiable.
     */
    public List<SequenceEntry> getEntryList() {
        return Collections.unmodifiableList(entryList);
    }

    public boolean useAsPacketName() {
        return useAsPacketName;
    }

    /**
     * If set to true, the qualified name of this container is used as packet name. The XtceTmRecorder stores it in the
     * "pname" column of the tm table, it appears in the Archive overview and in the packet statistics and it is used
     * to check the ReadPacket privileges.
     * <p>
     * If the container has a child that matches the packet, and the child also has this flag set, then the name of the
     * child will be used instead (e.g. the name of the most specific packet will be used).
     * <p>
     * If no container having this flag matches a packet, the name of the root container will be used (even if it does
     * not have the flag set).
     * 
     * <p>
     * The flag is stored in the ancillary data using the old {@link AncillaryData#PROP_USE_AS_ARCHIVING_PARTITION}
     * value such that the exported XTCE files can be read by older Yamcs versions.
     * 
     * @param useAsPacketName
     */
    @SuppressWarnings("deprecation")
    public void useAsPacketName(boolean useAsPacketName) {
        this.useAsPacketName = useAsPacketName;
        if (ancillaryData != null) {
            ancillaryData.removeIf(ad -> isPacketNameProperty(ad));
        }

        if (useAsPacketName) {
            // TODO switch to PROP_USE_AS_PACKET_NAME once older versions not recognizing it are no longer in use
            addAncillaryData(new AncillaryData(KEY_YAMCS, PROP_USE_AS_ARCHIVING_PARTITION));
        }
    }

    /**
     * @deprecated use {@link #useAsPacketName()}
     */
    @Deprecated
    public boolean useAsArchivePartition() {
        return useAsPacketName();
    }

    /**
     * @deprecated use {@link #useAsPacketName(boolean)}
     */
    @Deprecated
    public void useAsArchivePartition(boolean useAsArchivePartition) {
        useAsPacketName(useAsArchivePartition);
    }

    public void print(PrintStream out) {
        out.print("SequenceContainer name: " + name + ((sizeInBits > -1) ? ", sizeInBits: " + sizeInBits : ""));
        if (getAliasSet() != null) {
            out.print(", aliases: " + getAliasSet());
        }
        out.print(", useAsPacketName:" + useAsPacketName);
        if (rate != null) {
            out.print(", rateInStream: " + rate);
        }
        out.println();
        if (baseContainer != null) {
            out.print("\tbaseContainer: '" + baseContainer.getQualifiedName());
            out.println("', restrictionCriteria: " + restrictionCriteria);
        }
        for (SequenceEntry se : getEntryList()) {
            out.println("\t\t" + se);
        }
    }

    @Override
    public String toString() {
        return "SequenceContainer(name=" + name + ")";
    }

    public boolean isAutoPacketName() {
        return autoPacketName;
    }

    public void setAutoPacketName(boolean autoPacketName) {
        this.autoPacketName = autoPacketName;
    }

    /**
     * @deprecated use {@link #isAutoPacketName()}
     */
    @Deprecated
    public boolean isAutoPartition() {
        return isAutoPacketName();
    }

    /**
     * @deprecated use {@link #setAutoPacketName(boolean)}
     */
    @Deprecated
    public void setAutoPartition(boolean autoPartition) {
        setAutoPacketName(autoPartition);
    }
}
