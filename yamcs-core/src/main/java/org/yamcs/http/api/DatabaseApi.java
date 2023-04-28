package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractDatabaseApi;
import org.yamcs.protobuf.DatabaseInfo;
import org.yamcs.protobuf.GetDatabaseRequest;
import org.yamcs.protobuf.ListDatabasesResponse;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.Empty;

public class DatabaseApi extends AbstractDatabaseApi<Context> {

    @Override
    public void listDatabases(Context ctx, Empty request, Observer<ListDatabasesResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ControlArchiving, SystemPrivilege.ReadTables);
        List<String> databases = new ArrayList<>(YarchDatabase.getDatabases());
        Collections.sort(databases);
        ListDatabasesResponse.Builder responseb = ListDatabasesResponse.newBuilder();
        for (String database : databases) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(database);
            responseb.addDatabases(toDatabaseInfo(ydb));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getDatabase(Context ctx, GetDatabaseRequest request, Observer<DatabaseInfo> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ControlArchiving, SystemPrivilege.ReadTables);
        YarchDatabaseInstance ydb = verifyDatabase(request.getName());
        observer.complete(toDatabaseInfo(ydb));
    }

    private static DatabaseInfo toDatabaseInfo(YarchDatabaseInstance ydb) {
        DatabaseInfo.Builder b = DatabaseInfo.newBuilder()
                .setName(ydb.getName())
                .setTablespace(ydb.getTablespaceName())
                .setPath(ydb.getRoot());

        ydb.getTableDefinitions().stream()
                .map(tdef -> tdef.getName())
                .sorted()
                .forEach(b::addTables);

        ydb.getStreams().stream()
                .map(stream -> stream.getName())
                .sorted()
                .forEach(b::addStreams);

        return b.build();
    }

    public static YarchDatabaseInstance verifyDatabase(String name) {
        String instance = InstancesApi.verifyInstance(name, true);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb == null) {
            throw new NotFoundException("No database named '" + instance + "'");
        }
        return ydb;
    }
}
