package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TimeBasedPartition;

import com.google.protobuf.ByteString;

public class TablespaceTest {
    static Path testDir = Path.of(System.getProperty("java.io.tmpdir"), "TablespaceTest");

    @BeforeEach
    public void cleanup() throws Exception {
        FileUtils.deleteRecursivelyIfExists(testDir);
    }

    @Test
    public void test1() throws Exception {
        String dir = testDir + File.separator + "tablespace1";
        Tablespace tablespace = new Tablespace("tablespace1");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);

        createTablePartitionRecord(tablespace, "inst", "tbl1", null, null);
        createTablePartitionRecord(tablespace, "tablespace1", "tbl2", "/tmp2/", null);

        byte[] tbl3p1 = new byte[10];
        new Random().nextBytes(tbl3p1);
        createTablePartitionRecord(tablespace, "inst", "tbl3", "/tmp", tbl3p1);
        byte[] tbl3p2 = new byte[10];
        new Random().nextBytes(tbl3p2);
        createTablePartitionRecord(tablespace, "inst", "tbl3", "/tmp", tbl3p2);

        verify1(tablespace, tbl3p1, tbl3p2);
        tablespace.close();

        Tablespace tablespace2 = new Tablespace("tablespace2");
        tablespace2.setCustomDataDir(dir);
        tablespace2.loadDb(false);
        verify1(tablespace2, tbl3p1, tbl3p2);
        assertEquals(5, tablespace2.maxTbsIndex);
        tablespace2.close();
    }

    private void verify1(Tablespace tablespace, byte[] tbl3p1, byte[] tbl3p2) throws RocksDBException, IOException {
        List<TablespaceRecord> l = tablespace.getTablePartitions("inst", "tbl1");
        assertEquals(1, l.size());
        assertTrEquals1("inst", "tbl1", null, null, l.get(0));

        l = tablespace.getTablePartitions("inst", "tbl3");
        assertEquals(2, l.size());
        assertTrEquals1("inst", "tbl3", "/tmp", tbl3p1, l.get(0));
        assertTrEquals1("inst", "tbl3", "/tmp", tbl3p2, l.get(1));

        l = tablespace.getTablePartitions(tablespace.getName(), "tbl2");
        assertEquals(1, l.size());
        assertTrEquals1(tablespace.getName(), "tbl2", "/tmp2/", null, l.get(0));

    }

    private void assertTrEquals1(String expectedInstance, String expectedTable, String expectedDir,
            byte[] expectedValue, TablespaceRecord tr) {
        assertEquals(Type.TABLE_PARTITION, tr.getType());
        assertEquals(expectedInstance, tr.getInstanceName());
        assertEquals(expectedTable, tr.getTableName());
        if (expectedDir == null) {
            assertFalse(tr.hasPartition());
        } else {
            assertTrue(tr.hasPartition());
            assertEquals(expectedDir, tr.getPartition().getPartitionDir());
        }
        if (expectedValue == null) {
            assertFalse(tr.hasPartitionValue());
        } else {
            assertTrue(tr.hasPartitionValue());
            assertEquals(StringConverter.arrayToHexString(expectedValue),
                    StringConverter.arrayToHexString(tr.getPartitionValue().toByteArray()));
        }
    }

    @Test
    public void test2() throws Exception {
        String dir = testDir + File.separator + "tablespace2";
        Tablespace tablespace = new Tablespace("tablespace2");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);

        createHistogramRecord(tablespace, "inst", "tbl1", "col1", null);
        createHistogramRecord(tablespace, "inst", "tbl2", "col2", "/tmp");
        verify2(tablespace);

        tablespace.close();

        Tablespace tablespace2 = new Tablespace("tablespace2");
        tablespace2.setCustomDataDir(dir);
        tablespace2.loadDb(false);
        tablespace2.close();
    }

    private void verify2(Tablespace tablespace) throws Exception {
        List<TablespaceRecord> l = tablespace.getTableHistograms("inst", "tbl1");
        assertEquals(1, l.size());
        assertTrEquals2("inst", "tbl1", "col1", null, l.get(0));

        l = tablespace.getTableHistograms("inst", "tbl2");
        assertEquals(1, l.size());
        assertTrEquals2("inst", "tbl2", "col2", "/tmp", l.get(0));
    }

    private void assertTrEquals2(String expectedInstance, String expectedTable, String expectedColumnName,
            String expectedDir, TablespaceRecord tr) {
        assertEquals(Type.HISTOGRAM, tr.getType());
        assertEquals(expectedInstance, tr.getInstanceName());
        assertEquals(expectedTable, tr.getTableName());
        assertEquals(expectedColumnName, tr.getHistogramColumnName());
        if (expectedDir == null) {
            assertFalse(tr.hasPartition());
        } else {
            assertTrue(tr.hasPartition());
            assertEquals(expectedDir, tr.getPartition().getPartitionDir());
        }
    }

    @Test
    public void testRenameTable() throws Exception {
        String dir = testDir + File.separator + "tablespace3";
        Tablespace tablespace = new Tablespace("tablespace3");
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        TupleDefinition tupleDef = new TupleDefinition();
        tupleDef.addColumn("x", DataType.INT);
        TableDefinition tblDef = new TableDefinition("tbl1", tupleDef, Arrays.asList("x"));
        tablespace.createTable("inst", tblDef);
        createTablePartitionRecord(tablespace, "inst", "tbl1", null, null);

        tablespace.renameTable("inst", tblDef, "tbl2");
        assertEquals("tbl2", tblDef.getName());
        tablespace.close();

        Tablespace tablespace2 = new Tablespace("tablespace4");
        tablespace2.setCustomDataDir(dir);
        tablespace2.loadDb(false);

        Collection<TableDefinition> tdefs = tablespace2.loadTables("inst");
        assertEquals(1, tdefs.size());
        TableDefinition tblDef2 = tdefs.iterator().next();
        assertEquals("tbl2", tblDef2.getName());

        List<TablespaceRecord> trList = tablespace2.getTablePartitions("inst", "tbl2");
        assertEquals(1, trList.size());
        tablespace2.close();
    }

    private TablespaceRecord createTablePartitionRecord(Tablespace tablespace, String yamcsInstance, String tblName,
            String dir, byte[] bvalue) throws RocksDBException {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.TABLE_PARTITION)
                .setTableName(tblName);
        if (dir != null) {
            trb.setPartition(TimeBasedPartition.newBuilder().setPartitionDir(dir));
        }
        if (bvalue != null) {
            trb.setPartitionValue(ByteString.copyFrom(bvalue));
        }
        TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        return tr;
    }

    public TablespaceRecord createHistogramRecord(Tablespace tablespace, String yamcsInstance, String tblName,
            String columnName, String partitionDir) throws RocksDBException {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.HISTOGRAM)
                .setTableName(tblName);

        trb.setHistogramColumnName(columnName);

        if (partitionDir != null) {
            trb.setPartition(TimeBasedPartition.newBuilder().setPartitionDir(partitionDir));
        }

        TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        return tr;
    }
}
