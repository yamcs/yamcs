package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TableWriter.InsertMode;

/**
 * Tests the selection of the storage engine for the tables created without an explicit engine
 * (tableStorageEngines in yamcs.yarchtest_StorageEngineConfigTest.yaml)
 */
public class StorageEngineConfigTest extends YarchTestCase {
    static final String TEST_ENGINE = "testengine";
    static final TestEngine testEngine = new TestEngine();

    @BeforeAll
    public static void registerEngine() {
        if (YarchDatabase.getStorageEngine(TEST_ENGINE) == null) {
            YarchDatabase.addStorageEngine(TEST_ENGINE, testEngine);
        }
    }

    @Test
    public void testTableEngines() throws Exception {
        execute("create table t1(x int, y string, primary key(x))");
        execute("create table t2(x int, y string, primary key(x))");
        execute("create table t3(x int, y string, primary key(x)) engine rocksdb2");

        assertEquals(TEST_ENGINE, ydb.getTable("t1").getStorageEngineName());
        assertEquals(YarchDatabase.RDB_ENGINE_NAME, ydb.getTable("t2").getStorageEngineName());
        // the engine in the SQL statement takes precedence
        assertEquals(YarchDatabase.RDB_ENGINE_NAME, ydb.getTable("t3").getStorageEngineName());

        assertTrue(testEngine.tables.get(instance).containsKey("t1"));
        assertFalse(testEngine.tables.get(instance).containsKey("t2"));

        assertTrue(ydb.usesStorageEngine(TEST_ENGINE));
        assertTrue(ydb.usesStorageEngine(YarchDatabase.RDB_ENGINE_NAME));
        assertFalse(ydb.usesStorageEngine("bogus"));

        reloadDb();
        assertNotNull(ydb.getTable("t1"));
        assertEquals(TEST_ENGINE, ydb.getTable("t1").getStorageEngineName());
        assertEquals(YarchDatabase.RDB_ENGINE_NAME, ydb.getTable("t2").getStorageEngineName());
    }

    @Test
    public void testDuplicateRegistration() {
        assertThrows(IllegalArgumentException.class, () -> YarchDatabase.addStorageEngine(TEST_ENGINE, testEngine));
    }

    /**
     * Keeps only the table definitions
     */
    static class TestEngine implements StorageEngine {
        Map<String, Map<String, TableDefinition>> tables = new HashMap<>();

        @Override
        public synchronized List<TableDefinition> loadTables(YarchDatabaseInstance ydb) {
            return new ArrayList<>(tables.computeIfAbsent(ydb.getName(), k -> new HashMap<>()).values());
        }

        @Override
        public synchronized void createTable(YarchDatabaseInstance ydb, TableDefinition tblDef) {
            tables.computeIfAbsent(ydb.getName(), k -> new HashMap<>()).put(tblDef.getName(), tblDef);
        }

        @Override
        public void saveTableDefinition(YarchDatabaseInstance ydb, TableDefinition tblDef,
                List<TableColumnDefinition> keyColumns, List<TableColumnDefinition> valueColumns) {
        }

        @Override
        public synchronized void dropTable(YarchDatabaseInstance ydb, TableDefinition tblDef) {
            tables.get(ydb.getName()).remove(tblDef.getName());
        }

        @Override
        public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef,
                InsertMode insertMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TableWalker newTableWalker(ExecutionContext ctx, TableDefinition tblDef, boolean ascending,
                boolean follow) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HistogramIterator getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef,
                String columnName, TimeInterval interval) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BucketDatabase getBucketDatabase(YarchDatabaseInstance ydb) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProtobufDatabase getProtobufDatabase(YarchDatabaseInstance ydb) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PartitionManager getPartitionManager(YarchDatabaseInstance ydb, TableDefinition tblDef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Sequence getSequence(YarchDatabaseInstance ydb, String name, boolean create) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TableWalker newSecondaryIndexTableWalker(YarchDatabaseInstance ydb, TableDefinition tableDefinition,
                boolean ascending, boolean follow) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SequenceInfo> getSequencesInfo(YarchDatabaseInstance ydb) {
            return List.of();
        }

        @Override
        public void renameTable(YarchDatabaseInstance ydb, TableDefinition tblDef, String newName) {
            throw new UnsupportedOperationException();
        }
    }
}
