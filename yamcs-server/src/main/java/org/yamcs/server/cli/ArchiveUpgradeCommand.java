package org.yamcs.server.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.yamcs.YConfiguration;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.SegmentKey;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.oldrocksdb.HistogramRebuilder;
import org.yamcs.yarch.oldrocksdb.RdbPartition;
import org.yamcs.yarch.oldrocksdb.RdbPartitionManager;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Upgrade tables to latest format. It can only be done when Yamcs is not running.")
public class ArchiveUpgradeCommand extends Command {

    @Parameter(names = "--instance", description = "yamcs instance", required = true)
    String yamcsInstance;
    FileWriter filesToRemove;
    int filesToRemoveCount;

    public ArchiveUpgradeCommand(ArchiveCli archiveCli) {
        super("upgrade", archiveCli);
    }

    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance().setIgnoreVersionIncompatibility(true);
        if (yamcsInstance != null) {
            if (!YarchDatabase.instanceExistsOnDisk(yamcsInstance)) {
                throw new ParameterException("Archive instance '" + yamcsInstance + "' does not exist");
            }
            upgradeInstance(yamcsInstance);
        } else {
            List<String> instances = YConfiguration.getConfiguration("yamcs").getList("instances");
            for (String instance : instances) {
                upgradeInstance(instance);
            }
        }
    }

    private void upgradeInstance(String instance) throws Exception {
        String f = "/tmp/" + instance + "-cleanup.sh";
        filesToRemove = new FileWriter(f);
        upgradeYarchTables(instance);
        upgradeParameterArchive(instance);
        upgradeTagsDb(instance);
        console.println("\n*************************************\n");
        console.println("Instance " + instance + " has been upgraded");
        if (filesToRemoveCount > 0) {
            filesToRemove
                    .write("find '" + YarchDatabase.getInstance(instance).getRoot() + "' -type d -empty -delete\n");
            console.println(
                    "A number of files are not required anymore after upgrade and a script to remove them has been created in "
                            + f);
            console.println("Please execute the script after making sure that eveything is ok");
        }
        filesToRemove.close();
    }

    private void upgradeYarchTables(String instance) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        for (TableDefinition tblDef : ydb.getTableDefinitions()) {
            if (tblDef.getFormatVersion() == 0) {
                upgrade0_1(ydb, tblDef);
                tblDef.changeFormatDefinition(1);
            }
            if (tblDef.getFormatVersion() == 1) {
                upgrade1_2(ydb, tblDef);
                tblDef.changeFormatDefinition(TableDefinition.CURRENT_FORMAT_VERSION);
            }
            if (tblDef.getStorageEngineName().equals(YarchDatabase.OLD_RDB_ENGINE_OLD_NAME)) {
                upgradeRocksDBTable(ydb, tblDef);
                tblDef.changeStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);
            }
        }
    }

    private void upgrade0_1(YarchDatabaseInstance ydb, TableDefinition tblDef) throws Exception {
        console.println("upgrading table " + ydb.getName() + "/" + tblDef.getName() + " from version 0 to version 1");
        if ("pp".equals(tblDef.getName())) {
            changePpGroup(ydb, tblDef);
        }

        if (tblDef.hasHistogram()) {
            rebuildHistogram(ydb, tblDef);
        }
    }

    private void upgrade1_2(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        console.println("upgrading table " + ydb.getName() + "/" + tblDef.getName() + " from version 1 to version 2");
        if ("pp".equals(tblDef.getName())) {
            changeParaValueType(tblDef);
        }
    }

    static void changeParaValueType(TableDefinition tblDef) {
        TupleDefinition valueDef = tblDef.getValueDefinition();
        List<ColumnDefinition> l = valueDef.getColumnDefinitions();
        for (int i = 0; i < l.size(); i++) {
            ColumnDefinition cd = l.get(i);
            if ("org.yamcs.protobuf.Pvalue$ParameterValue".equals(cd.getType().name())) {
                ColumnDefinition cd1 = new ColumnDefinition(cd.getName(), DataType.PARAMETER_VALUE);
                l.set(i, cd1);
            }
        }
    }

    private void changePpGroup(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        if (tblDef.getColumnDefinition("ppgroup") == null) {
            console.println(String.format("Table %s/%s has no ppgroup column", ydb.getName(), tblDef.getName()));
            return;
        }
        console.println(
                String.format("Renaming ppgroup -> group column in table {}/{}", ydb.getName(), tblDef.getName()));
        tblDef.renameColumn("ppgroup", "group");
    }

    private void rebuildHistogram(YarchDatabaseInstance ydb, TableDefinition tblDef)
            throws InterruptedException, ExecutionException, YarchException {
        HistogramRebuilder hrb = new HistogramRebuilder(ydb, tblDef.getName());
        hrb.rebuild(new TimeInterval()).get();
    }

    private void upgradeRocksDBTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws Exception {
        console.println("upgrading table " + ydb.getName() + "/" + tblDef.getName() + "to new RocksDB storage engine");
        RdbStorageEngine newRse = RdbStorageEngine.getInstance();
        newRse.createTable(ydb, tblDef);

        org.yamcs.yarch.oldrocksdb.RdbStorageEngine oldRse = org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance();
        Stream stream = oldRse.newTableReaderStream(ydb, tblDef, true, false);

        TableWriter tw = newRse.newTableWriter(ydb, tblDef, InsertMode.LOAD);
        stream.addSubscriber(tw);
        Semaphore semaphore = new Semaphore(0);
        AtomicInteger count = new AtomicInteger();
        stream.addSubscriber(new StreamSubscriber() {
            int c = 0;

            @Override
            public void streamClosed(Stream stream) {
                count.set(c);
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                c++;
                if (c % 100000 == 0) {
                    console.println(tblDef.getName() + " saved " + c + " tuples");
                }
            }
        });
        AtomicReference<Throwable> exception = new AtomicReference<>();
        stream.exceptionHandler((s, tuple, t1) -> {
            console.print("Error saving tuple " + tuple);
            exception.set(t1);
        });
        stream.start();
        semaphore.acquire();
        Throwable t = exception.get();
        if (t != null) {
            if (t instanceof Exception) {
                throw (Exception) t;
            } else {
                throw new Exception(t);
            }
        }
        console.println(ydb.getName() + "/" + tblDef.getName() + " upgrade finished: converted " + count + " tuples");
        RdbPartitionManager pm = oldRse.getPartitionManager(tblDef);
        for (Partition p : pm.getPartitions()) {
            RdbPartition rp = (RdbPartition) p;
            filesToRemove.write("rm -rf '" + ydb.getRoot() + "/" + rp.getDir() + "'\n");
            filesToRemoveCount++;
        }
    }

    private void upgradeParameterArchive(String instance) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        File f = new File(ydb.getRoot() + "/ParameterArchive");
        if (!f.exists()) {
            return;
        }
        console.println(instance + ": upgrading parameter archive");
        ParameterArchiveV2 newparch = new ParameterArchiveV2(instance);
        org.yamcs.oldparchive.ParameterArchive oldparch = new org.yamcs.oldparchive.ParameterArchive(instance);
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        Tablespace tablespace = rse.getTablespace(ydb.getTablespaceName());
        org.yamcs.oldparchive.ParameterIdDb oldParaIdDb = oldparch.getParameterIdDb();
        ParameterIdDb newParaIdDb = newparch.getParameterIdDb();

        console.println("creating parameter ids in the tablespace " + tablespace.getName());
        Map<Integer, Integer> oldToNewParaId = new HashMap<>();

        oldToNewParaId.put(org.yamcs.oldparchive.ParameterIdDb.TIMESTAMP_PARA_ID, newParaIdDb.getTimeParameterId());

        for (Map.Entry<String, Map<Integer, Integer>> e : oldParaIdDb.getMap().entrySet()) {
            String fqn = e.getKey();
            for (Map.Entry<Integer, Integer> e1 : e.getValue().entrySet()) {
                int paraType = e1.getKey();
                int oldParamId = e1.getValue();
                int newParamId = newParaIdDb.createAndGet(fqn, org.yamcs.oldparchive.ParameterIdDb.getEngType(paraType),
                        org.yamcs.oldparchive.ParameterIdDb.getRawType(paraType));
                oldToNewParaId.put(oldParamId, newParamId);
            }
        }
        console.println("creating parameter groups in the tablespace " + tablespace.getName());
        org.yamcs.oldparchive.ParameterGroupIdDb oldParaGroupIdDb = oldparch.getParameterGroupIdDb();
        ParameterGroupIdDb newParaGroupIdDb = newparch.getParameterGroupIdDb();
        Map<SortedIntArray, Integer> oldGroups = oldParaGroupIdDb.getMap();
        Map<Integer, Integer> oldToNewGroupId = new HashMap<>();
        for (Map.Entry<SortedIntArray, Integer> e : oldGroups.entrySet()) {
            SortedIntArray s = new SortedIntArray();
            e.getKey().forEach(x -> s.insert(oldToNewParaId.get(x)));
            int newGroupId = newParaGroupIdDb.createAndGet(s);
            oldToNewGroupId.put(e.getValue(), newGroupId);
        }
        console.println(instance + ": migrating the ParameterArchive data");
        int segCount = 0;
        for (org.yamcs.oldparchive.ParameterArchive.Partition oldpart : oldparch.getPartitions()) {
            try (RocksIterator it = oldparch.getIterator(oldpart)) {
                it.seekToFirst();
                while (it.isValid()) {
                    org.yamcs.oldparchive.SegmentKey oldkey = org.yamcs.oldparchive.SegmentKey.decode(it.key());
                    int newparaid = oldToNewParaId.get(oldkey.getParameterId());
                    int newgroupid = oldToNewGroupId.get(oldkey.getParameterGroupId());
                    SegmentKey newkey = new SegmentKey(newparaid, newgroupid, oldkey.getSegmentStart(),
                            oldkey.getType());
                    byte[] val = it.value();
                    org.yamcs.parameterarchive.ParameterArchiveV2.Partition newpart = newparch
                            .createAndGetPartition(oldkey.getSegmentStart());
                    tablespace.getRdb(newpart.getPartitionDir(), false).put(newkey.encode(), val);
                    segCount++;
                    if (segCount % 1000 == 0) {
                        console.println(instance + ": ParameterArchive migrated " + segCount + " segments");
                    }
                    it.next();
                }
            }
        }
        console.println(instance + ": ParameterArchive migration finished, migrated " + segCount + " segments");

        File f1 = new File(ydb.getRoot() + "/ParameterArchive.old");
        if (!f.renameTo(f1)) {
            throw new IOException("Could not rename " + f + " to " + f1);
        }
        filesToRemoveCount++;
        filesToRemove.write("rm -rf " + f1.getAbsolutePath() + "\n");
    }

    private void upgradeTagsDb(String instance) throws Exception {
        console.println(instance + ": Migrating TagDB");
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        File f = new File(ydb.getRoot() + "/tags");
        if (!f.exists()) {
            return;
        }
        RdbStorageEngine newRse = RdbStorageEngine.getInstance();
        org.yamcs.yarch.oldrocksdb.RdbStorageEngine oldRse = org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance();
        TagDb oldTagDb = oldRse.getTagDb(ydb);
        TagDb newTagDb = newRse.getTagDb(ydb);
        Semaphore s = new Semaphore(0);
        AtomicInteger count = new AtomicInteger();
        oldTagDb.getTags(new TimeInterval(), new TagReceiver() {
            @Override
            public void onTag(ArchiveTag tag) {
                try {
                    newTagDb.insertTag(tag);
                    count.incrementAndGet();
                } catch (IOException e) {
                    throw new RuntimeException("Error when inserting tag", e);
                }
            }

            @Override
            public void finished() {
                s.release();
            }
        });
        s.acquire();
        File f1 = new File(ydb.getRoot() + "/tags.old");
        if (!f.renameTo(f1)) {
            throw new IOException("Could not rename " + f + " to " + f1);
        }
        filesToRemove.write("rm -rf " + f1.getAbsolutePath() + "\n");
        filesToRemoveCount++;
        console.println(instance + ": TagDB migration finished, migrated " + count + " tags");

    }
}
