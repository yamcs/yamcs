package org.yamcs.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;

public class TmPartitionGroupsTest {
    static Mdb mdb;

    @BeforeAll
    public static void beforeClass() {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("tm-partition-groups");
    }

    TmPartitionGroups create(Map<String, List<String>> groups) {
        return new TmPartitionGroups("test", mdb, groups);
    }

    @Test
    public void testGetGroup() {
        var tpg = create(Map.of(
                "events", List.of("/tpg/event"),
                "hk2", List.of("/tpg/hk2")));

        // inherited from the parent
        assertEquals("events", tpg.getGroup("/tpg/event1"));
        assertEquals("events", tpg.getGroup("/tpg/event"));
        assertEquals("hk2", tpg.getGroup("/tpg/hk2"));

        assertEquals(TmPartitionGroups.DEFAULT_GROUP, tpg.getGroup("/tpg/hk1"));
        assertEquals(TmPartitionGroups.DEFAULT_GROUP, tpg.getGroup("/tpg/hk"));
        assertEquals(TmPartitionGroups.DEFAULT_GROUP, tpg.getGroup("/tpg/pkt"));

        // not in the MDB
        assertEquals(TmPartitionGroups.DEFAULT_GROUP, tpg.getGroup("/tpg/unknown"));
    }

    @Test
    public void testMostSpecificWins() {
        var tpg = create(Map.of(
                "hk", List.of("/tpg/hk"),
                "hk1", List.of("/tpg/hk1")));
        assertEquals("hk1", tpg.getGroup("/tpg/hk1"));
        assertEquals("hk", tpg.getGroup("/tpg/hk2"));
    }

    @Test
    public void testGetGroups() {
        var tpg = create(Map.of("events", List.of("/tpg/event")));

        assertEquals(Set.of("events"), tpg.getGroups(List.of("/tpg/event1")));
        assertEquals(Set.of(TmPartitionGroups.DEFAULT_GROUP), tpg.getGroups(List.of("/tpg/hk1", "/tpg/hk2")));

        // all the groups -> no filter
        assertNull(tpg.getGroups(List.of("/tpg/hk1", "/tpg/event1")));
        // unknown packet name -> no filter
        assertNull(tpg.getGroups(List.of("/tpg/event1", "/tpg/unknown")));
        assertNull(tpg.getGroups(List.of()));
    }

    @Test
    public void testNoGroups() {
        var tpg = create(Map.of());
        assertEquals(TmPartitionGroups.DEFAULT_GROUP, tpg.getGroup("/tpg/event1"));
        assertNull(tpg.getGroups(List.of("/tpg/event1")));
    }

    @Test
    public void testCanBePacketName() {
        // in refmdb, PKT1_1 is used as packet name, PKT1_3 is not and its packets are named after the root container
        Mdb refmdb = MdbFactory.createInstanceByConfig("refmdb");
        var tpg = new TmPartitionGroups("test", refmdb, Map.of());
        assertTrue(tpg.canBePacketName(refmdb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1_1")));
        assertTrue(tpg.canBePacketName(refmdb.getSequenceContainer("/REFMDB/ccsds-default")));
        assertFalse(tpg.canBePacketName(refmdb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1_3")));
    }

    @Test
    public void testDuplicateContainer() {
        assertThrows(ConfigurationException.class, () -> create(Map.of(
                "g1", List.of("/tpg/event"),
                "g2", List.of("/tpg/event"))));
    }
}
