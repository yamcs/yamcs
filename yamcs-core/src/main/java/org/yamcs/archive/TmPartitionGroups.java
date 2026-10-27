package org.yamcs.archive;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

/**
 * Assigns the packets recorded in the tm table to partition groups, as configured with the {@code partitionGroups}
 * option of the {@link XtceTmRecorder}.
 * <p>
 * The group of a packet is determined from its packet name (pname): starting with the container having that name and
 * going up in the XTCE hierarchy, the first container listed in a group determines the group. If no container is
 * listed, the packet goes into the {@link #DEFAULT_GROUP}.
 * <p>
 * The groups are computed from the current configuration and MDB. If the configuration is changed such that a packet
 * moves to another group, the retrievals filtering on that packet name will not find the data recorded before the
 * change.
 */
public class TmPartitionGroups {
    public static final String PGROUP_COLUMN = "pgroup";
    public static final String DEFAULT_GROUP = "default";

    final Mdb mdb;
    // container qualified name -> group
    final Map<String, String> containerGroups = new HashMap<>();
    final Set<String> groups = new HashSet<>();
    // pname -> group cache
    final Map<String, String> pnameGroups = new ConcurrentHashMap<>();

    /**
     *
     * @param yamcsInstance
     *            used for logging
     * @param mdb
     *            used to navigate the container hierarchy
     * @param groupDefinitions
     *            group name -> list of container qualified names
     */
    public TmPartitionGroups(String yamcsInstance, Mdb mdb, Map<String, List<String>> groupDefinitions) {
        Log log = new Log(TmPartitionGroups.class, yamcsInstance);
        this.mdb = mdb;
        groups.add(DEFAULT_GROUP);
        for (var me : groupDefinitions.entrySet()) {
            String group = me.getKey();
            groups.add(group);
            for (String containerName : me.getValue()) {
                String old = containerGroups.put(containerName, group);
                if (old != null) {
                    throw new ConfigurationException("Container '" + containerName
                            + "' appears in two partition groups: '" + old + "' and '" + group + "'");
                }
                SequenceContainer sc = mdb.getSequenceContainer(containerName);
                if (sc == null) {
                    log.warn("Partition group '{}': container '{}' not found in the MDB", group, containerName);
                } else if (!canBePacketName(sc)) {
                    log.warn("Partition group '{}': container '{}' has no effect because neither it nor any of its "
                            + "inheriting containers is used as packet name", group, containerName);
                }
            }
        }
    }

    /**
     * Parses the {@code partitionGroups} configuration: a map of group name -> list of container names.
     */
    static Map<String, List<String>> parseConfig(YConfiguration config) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String group : config.getKeys()) {
            if (config.isList(group)) {
                result.put(group, config.getList(group));
            } else {
                result.put(group, List.of(config.getString(group)));
            }
        }
        return result;
    }

    /**
     * The groups are determined from the packet names, so a container listed in a group has an effect only if it, or
     * one of its inheriting containers, can be a packet name: it has the useAsPacketName flag or it is a root
     * container.
     */
    boolean canBePacketName(SequenceContainer sc) {
        if (sc.useAsPacketName() || sc.getBaseContainer() == null) {
            return true;
        }
        List<SequenceContainer> children = mdb.getInheritingContainers(sc);
        if (children != null) {
            for (SequenceContainer child : children) {
                if (canBePacketName(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the group in which the packets with the given name are recorded.
     */
    public String getGroup(String pname) {
        return pnameGroups.computeIfAbsent(pname, pn -> {
            String group = resolveGroup(pn);
            return group == null ? DEFAULT_GROUP : group;
        });
    }

    /**
     * Returns the group of the packet name or null if the packet name does not correspond to a container in the MDB.
     */
    String resolveGroup(String pname) {
        SequenceContainer sc = mdb.getSequenceContainer(pname);
        if (sc == null) {
            return null;
        }
        while (sc != null) {
            String group = containerGroups.get(sc.getQualifiedName());
            if (group != null) {
                return group;
            }
            sc = sc.getBaseContainer();
        }
        return DEFAULT_GROUP;
    }

    /**
     * Returns the groups containing the given packet names or null if all groups have to be read (because the groups
     * of some packet names cannot be determined or because all groups are concerned).
     */
    public Set<String> getGroups(Collection<String> pnames) {
        if (pnames.isEmpty()) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String pname : pnames) {
            String group = resolveGroup(pname);
            if (group == null) {
                return null;
            }
            result.add(group);
        }
        if (result.containsAll(groups)) {
            return null;
        }
        return result;
    }

    /**
     * If the tm table of the instance is partitioned by group, adds a condition on the pgroup column such that only the
     * partitions which may contain the given packet names are read.
     * <p>
     * This only limits which partitions are read; the caller has to filter on the packet names itself.
     */
    public static void addGroupFilter(String yamcsInstance, SqlBuilder sqlb, Collection<String> pnames) {
        TmPartitionGroups tpg = getInstance(yamcsInstance);
        if (tpg == null) {
            return;
        }
        Set<String> groups = tpg.getGroups(pnames);
        if (groups != null) {
            sqlb.whereColIn(PGROUP_COLUMN, groups);
        }
    }

    /**
     * Returns the partition groups of the given instance, or null if the tm table is not partitioned by group or if
     * no {@link XtceTmRecorder} is running in the instance.
     */
    public static TmPartitionGroups getInstance(String yamcsInstance) {
        TableDefinition tblDef = YarchDatabase.getInstance(yamcsInstance).getTable(XtceTmRecorder.TABLE_NAME);
        if (tblDef == null || !isPartitionedByGroup(tblDef)) {
            return null;
        }
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        if (ysi == null) {
            return null;
        }
        for (XtceTmRecorder recorder : ysi.getServices(XtceTmRecorder.class)) {
            if (recorder.getPartitionGroups() != null) {
                return recorder.getPartitionGroups();
            }
        }
        return null;
    }

    static boolean isPartitionedByGroup(TableDefinition tblDef) {
        return PGROUP_COLUMN.equals(tblDef.getPartitioningSpec().valueColumn);
    }
}
