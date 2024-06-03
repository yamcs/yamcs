package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;

public class RdbPartitionManagerTest {

    @BeforeAll
    static public void init() {
        TimeEncoding.setUp();
    }

    TableDefinition getTableDefTimeAndValue() throws Exception {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec = PartitioningSpec.timeAndValueSpec("gentime", "packetid", "YYYY/DOY");

        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);

        return tblDef;
    }

    TableDefinition getTableDefValue() throws Exception {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec = PartitioningSpec.valueSpec("packetid");

        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);

        return tblDef;
    }

    TableDefinition getTableDefTime() throws Exception {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec = PartitioningSpec.timeSpec("gentime", "YYYYY/DOY");

        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);

        return tblDef;
    }

    @Test
    public void createAndIteratePartitions() throws Exception {
        Tablespace tablespace = new Tablespace("test");
        String tmpdir = Files.createTempDirectory("RdbPartitionManagerTest").toString();
        tablespace.setCustomDataDir(tmpdir);

        tablespace.loadDb(false);

        TableDefinition tblDef = getTableDefTimeAndValue();
        RdbTable table = new RdbTable("test", tablespace, tblDef, 1, "default");

        RdbPartitionManager pm = table.getPartitionManager();
        RdbPartition part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-01-01T00:00:00"), 1);
        assertEquals("2011/001", part.dir);

        part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"), 1);
        assertEquals("2011/060", part.dir);

        part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"), 2);
        assertEquals("2011/032", part.dir);

        part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"), 3);
        assertEquals("2011/032", part.dir);

        part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"), 3);
        assertEquals("2011/060", part.dir);

        Set<Object> filter = new HashSet<>();
        filter.add(1);
        filter.add(3);
        Iterator<PartitionManager.Interval> it = pm.iterator(TimeEncoding.parse("2011-02-01T00:00:00"), filter);
        assertTrue(it.hasNext());
        PartitionManager.Interval parts = it.next();
        assertEquals(1, parts.size());

        assertTrue(it.hasNext());
        parts = it.next();
        Iterator<Partition> pit = parts.iterator();
        assertEquals("2011/060", ((RdbPartition) pit.next()).dir);
        assertEquals("2011/060", ((RdbPartition) pit.next()).dir);

        tablespace.close();

        tablespace = new Tablespace("test");
        tablespace.setCustomDataDir(tmpdir);

        tablespace.loadDb(true);

        table = new RdbTable("test", tablespace, tblDef, 1, "default");
        pm = table.getPartitionManager();
        pm.readPartitions();
        List<Partition> plist = pm.getPartitions();
        assertEquals(5, plist.size());
        tablespace.close();

        Path path = Paths.get(tmpdir);
        FileUtils.deleteRecursivelyIfExists(path);
    }

    @Test
    public void createAndIteratePartitions1() throws Exception {
        Tablespace tablespace = new Tablespace("test");
        String tmpdir = Files.createTempDirectory("RdbPartitionManagerTest").toString();
        tablespace.setCustomDataDir(tmpdir);

        tablespace.loadDb(false);

        TableDefinition tblDef = getTableDefTimeAndValue();

        RdbTable table = new RdbTable("test", tablespace, tblDef, 1, "default");

        RdbPartitionManager pm = table.getPartitionManager();
        RdbPartition part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("0001-01-01T00:00:00"), 1);
        assertEquals("0001/001", part.dir);
    }
}
