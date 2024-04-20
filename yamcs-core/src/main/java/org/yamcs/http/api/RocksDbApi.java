package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.rocksdb.RocksDBException;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractRocksDbApi;
import org.yamcs.protobuf.BackupDatabaseRequest;
import org.yamcs.protobuf.CompactDatabaseRequest;
import org.yamcs.protobuf.DescribeDatabaseRequest;
import org.yamcs.protobuf.ListRocksDbDatabasesResponse;
import org.yamcs.protobuf.ListRocksDbTablespacesResponse;
import org.yamcs.protobuf.RocksDbDatabaseInfo;
import org.yamcs.protobuf.RocksDbTablespaceInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.yarch.BackupUtils;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

public class RocksDbApi extends AbstractRocksDbApi<Context> {

    private static final Log log = new Log(RocksDbApi.class);

    public RocksDbApi(AuditLog auditLog) {
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ControlArchiving);
        });
    }

    @Override
    public void listTablespaces(Context ctx, Empty request, Observer<ListRocksDbTablespacesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        List<RocksDbTablespaceInfo> unsorted = new ArrayList<>();
        RdbStorageEngine storageEngine = RdbStorageEngine.getInstance();
        for (Tablespace tblsp : storageEngine.getTablespaces().values()) {
            RocksDbTablespaceInfo.Builder tablespaceb = RocksDbTablespaceInfo.newBuilder()
                    .setName(tblsp.getName())
                    .setDataDir(tblsp.getDataDir());
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String dbPath : rdbf.getOpenDbPaths()) {
                RocksDbDatabaseInfo database = toRocksDbDatabaseInfo(tblsp, dbPath);
                tablespaceb.addDatabases(database);
            }
            unsorted.add(tablespaceb.build());
        }

        ListRocksDbTablespacesResponse.Builder responseb = ListRocksDbTablespacesResponse.newBuilder();
        Collections.sort(unsorted, (t1, t2) -> t1.getName().compareTo(t2.getName()));
        responseb.addAllTablespaces(unsorted);
        observer.complete(responseb.build());
    }

    @Override
    public void backupDatabase(Context ctx, BackupDatabaseRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        Tablespace tablespace = verifyTablespace(request.getTablespace());
        String dbpath = request.hasDbpath() ? request.getDbpath() : null;

        if (!request.hasBackupDir()) {
            throw new BadRequestException("No backup directory specified");
        }

        String backupDir = request.getBackupDir();
        try {
            BackupUtils.verifyBackupDirectory(backupDir, false);
        } catch (Exception e1) {
            throw new BadRequestException(e1.toString());
        }

        RDBFactory rdbFactory = tablespace.getRdbFactory();

        CompletableFuture<Void> cf = (dbpath == null) ? rdbFactory.doBackup(backupDir)
                : rdbFactory.doBackup(dbpath, backupDir);

        cf.whenComplete((r, e) -> {
            if (e != null) {
                observer.completeExceptionally(e);
            } else {
                observer.complete(Empty.getDefaultInstance());
            }
        });
    }

    @Override
    public void listDatabases(Context ctx, Empty request, Observer<ListRocksDbDatabasesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        List<RocksDbDatabaseInfo> unsorted = new ArrayList<>();
        RdbStorageEngine storageEngine = RdbStorageEngine.getInstance();
        for (Tablespace tblsp : storageEngine.getTablespaces().values()) {
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String dbPath : rdbf.getOpenDbPaths()) {
                RocksDbDatabaseInfo database = toRocksDbDatabaseInfo(tblsp, dbPath);
                unsorted.add(database);
            }
        }

        ListRocksDbDatabasesResponse.Builder responseb = ListRocksDbDatabasesResponse.newBuilder();
        Collections.sort(unsorted, (db1, db2) -> {
            if (db1.getTablespace().equals(db2.getTablespace())) {
                return db1.getDbPath().compareTo(db2.getDbPath());
            } else {
                return db1.getTablespace().compareTo(db2.getTablespace());
            }
        });
        responseb.addAllDatabases(unsorted);
        observer.complete(responseb.build());
    }

    @Override
    public void compactDatabase(Context ctx, CompactDatabaseRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        Tablespace tablespace = verifyTablespace(request.getTablespace());
        String dbpath = request.hasDbpath() ? request.getDbpath() : null;

        RDBFactory rdbFactory = tablespace.getRdbFactory();
        YRDB yrdb;
        if (dbpath == null) {
            yrdb = rdbFactory.getOpenRdb();
        } else {
            yrdb = rdbFactory.getOpenRdb(dbpath);
            if (yrdb == null) {
                yrdb = rdbFactory.getOpenRdb("/" + dbpath);
            }
        }

        if (yrdb == null) {
            if (dbpath == null) {
                throw new BadRequestException("Root database not open for tablespace " + tablespace.getName());
            } else {
                throw new BadRequestException("No open database " + dbpath + " for tablespace " + tablespace.getName());
            }
        }

        try {
            String cfName = request.hasCfname() ? request.getCfname() : null;
            yrdb.compactRange(cfName, null, null);
            observer.complete(Empty.getDefaultInstance());
        } catch (RocksDBException e) {
            log.error("Error when compacting database", e);
            observer.completeExceptionally(new InternalServerErrorException(e));
        } finally {
            rdbFactory.dispose(yrdb);
        }
    }

    @Override
    public void describeRocksDb(Context ctx, Empty request, Observer<HttpBody> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        StringBuilder sb = new StringBuilder();
        for (Tablespace tblsp : rse.getTablespaces().values()) {
            sb.append("Tablespace: ").append(tblsp.getName()).append("\n");
            sb.append("  dataDir: ").append(tblsp.getDataDir()).append("\n");
            sb.append("  open databases: ").append("\n");
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String s : rdbf.getOpenDbPaths()) {
                if (s.isEmpty()) {
                    s = "<root>";
                }
                sb.append("    ").append(s).append("\n");
            }
        }

        HttpBody body = HttpBody.newBuilder()
                .setContentType(MediaType.PLAIN_TEXT.toString())
                .setData(ByteString.copyFrom(sb.toString().getBytes()))
                .build();

        observer.complete(body);
    }

    @Override
    public void describeDatabase(Context ctx, DescribeDatabaseRequest request, Observer<HttpBody> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        Tablespace tablespace = verifyTablespace(request.getTablespace());
        String dbpath = request.getDbpath();

        RDBFactory rdbFactory = tablespace.getRdbFactory();
        YRDB yrdb;
        if (dbpath == null) {
            yrdb = rdbFactory.getOpenRdb();
        } else {
            yrdb = rdbFactory.getOpenRdb(dbpath);
            if (yrdb == null) {
                yrdb = rdbFactory.getOpenRdb("/" + dbpath);
            }
        }
        if (yrdb == null) {
            if (dbpath == null) {
                throw new BadRequestException("Root database not open for tablespace " + tablespace.getName());
            } else {
                throw new BadRequestException("No open database " + dbpath + " for tablespace " + tablespace.getName());
            }
        }

        try {
            String s = yrdb.getProperties();

            HttpBody body = HttpBody.newBuilder()
                    .setContentType(MediaType.PLAIN_TEXT.toString())
                    .setData(ByteString.copyFrom(s.getBytes()))
                    .build();

            observer.complete(body);
        } catch (RocksDBException e) {
            log.error("Error when getting database properties", e);
            observer.completeExceptionally(e);
        } finally {
            rdbFactory.dispose(yrdb);
        }
    }

    private Tablespace verifyTablespace(String tablespaceName) throws HttpException {
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        Tablespace tablespace = rse.getTablespace(tablespaceName);
        if (tablespace == null) {
            throw new BadRequestException("No tablespace by name '" + tablespaceName + "'");
        }
        return tablespace;
    }

    private static RocksDbDatabaseInfo toRocksDbDatabaseInfo(Tablespace tablespace, String dbPath) {
        RocksDbDatabaseInfo.Builder databaseb = RocksDbDatabaseInfo.newBuilder()
                .setTablespace(tablespace.getName())
                .setDataDir(tablespace.getDataDir())
                .setDbPath(dbPath);
        return databaseb.build();
    }
}
