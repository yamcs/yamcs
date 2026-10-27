package org.yamcs.archive;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.SecondaryIndexWriter;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex.State;

/** TM-specific provisioning; persistence and recovery belong to the storage engine. */
public final class TmReceptionTimeIndex {
    public static final List<String> COLUMNS = List.of("rectime", "pname");

    private TmReceptionTimeIndex() {
    }

    public static SecondaryIndexWriter get(YarchDatabaseInstance database) {
        TableDefinition definition = database.getTable(XtceTmRecorder.TABLE_NAME);
        if (definition == null || !COLUMNS.equals(definition.getSecondaryIndex())
                || !(database.getStorageEngine(definition) instanceof RdbStorageEngine engine)) {
            return null;
        }
        return engine.getTablespace(database).getTable(definition).getSecondaryIndexWriter();
    }

    public static void start(YarchDatabaseInstance database, boolean provision) throws Exception {
        SecondaryIndexWriter writer = get(database);
        if (writer == null && !provision) {
            return;
        }
        validate(database);
        if (writer == null) {
            writer = install(database);
        }
        if (!writer.getInfo().getManaged()) {
            writer.rebuild(true);
        } else if (writer.getInfo().getState() != State.READY) {
            writer.rebuild(false);
        }
    }

    public static CompletableFuture<Void> rebuild(YarchDatabaseInstance database) throws Exception {
        validate(database);
        SecondaryIndexWriter writer = get(database);
        if (writer == null) {
            writer = install(database);
        }
        return writer.rebuild(true);
    }

    private static SecondaryIndexWriter install(YarchDatabaseInstance database) throws Exception {
        validate(database);
        TableDefinition definition = database.getTable(XtceTmRecorder.TABLE_NAME);
        RdbStorageEngine engine = (RdbStorageEngine) database.getStorageEngine(definition);
        return engine.getTablespace(database).ensureSecondaryIndex(database, definition, COLUMNS);
    }

    private static void validate(YarchDatabaseInstance database) {
        TableDefinition definition = database.getTable(XtceTmRecorder.TABLE_NAME);
        if (definition == null) {
            throw new IllegalArgumentException("No TM archive table exists");
        }
        if (!(database.getStorageEngine(definition) instanceof RdbStorageEngine)
                || definition.getColumnDefinition("rectime") == null
                || definition.getColumnDefinition("rectime").getType() != DataType.TIMESTAMP
                || definition.getColumnDefinition("pname") == null
                || definition.getColumnDefinition("pname").getType() != DataType.ENUM
                || !definition.getKeyDefinition().stream().map(c -> c.getName()).toList()
                        .equals(List.of("gentime", "seqNum"))) {
            throw new IllegalArgumentException("Reception-time indexing requires the standard RocksDB TM schema");
        }
    }
}
