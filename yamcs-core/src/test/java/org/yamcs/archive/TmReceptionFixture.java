package org.yamcs.archive;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

/** Common late-packet and timestamp-tie dataset for query and performance tests. */
public final class TmReceptionFixture {
    public static final long BASE = TimeEncoding.parse("2024-01-01T00:00:00Z");

    private TmReceptionFixture() {
    }

    public static TableDefinition create(YarchDatabaseInstance database) throws Exception {
        database.executeDiscardingResult("create table tm(gentime timestamp,seqNum int,rectime timestamp,pname enum,"
                + "packet binary,link enum,primary key(gentime,seqNum))"
                + " partition by time_and_value(gentime('YYYY/MM'),pname)"
                + " table_format=compressed,column_family:rt_data");
        return database.getTable("tm");
    }

    public static void seed(YarchDatabaseInstance database) {
        put(database, BASE, 1, BASE + 30, "a", "live");
        put(database, BASE, 1, BASE + 30, "b", "backup");
        put(database, BASE + 40L * 86400000, 2, BASE + 10, "a", "live");
        put(database, BASE + 1, 3, BASE + 20, "a", "backup");
    }

    public static void put(YarchDatabaseInstance database, long generation, int seq, long reception,
            String name, String link) {
        var definition = database.getTable("tm");
        var writer = RdbStorageEngine.getInstance().getTablespace(database)
                .newTableWriter(database, definition, InsertMode.INSERT);
        try {
            writer.onTuple(null, new Tuple(definition.getTupleDefinition(),
                    new Object[] { generation, seq, reception, name, new byte[] { (byte) seq }, link }));
        } finally {
            writer.close();
        }
    }
}
