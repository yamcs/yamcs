package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.archive.ArchiveHelper;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractTableApi;
import org.yamcs.protobuf.Table.ExecuteSqlRequest;
import org.yamcs.protobuf.Table.ExecuteSqlResponse;
import org.yamcs.protobuf.Table.GetStreamRequest;
import org.yamcs.protobuf.Table.GetTableDataRequest;
import org.yamcs.protobuf.Table.GetTableRequest;
import org.yamcs.protobuf.Table.ListStreamsRequest;
import org.yamcs.protobuf.Table.ListStreamsResponse;
import org.yamcs.protobuf.Table.ListTablesRequest;
import org.yamcs.protobuf.Table.ListTablesResponse;
import org.yamcs.protobuf.Table.ReadRowsRequest;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.Row.Cell;
import org.yamcs.protobuf.Table.Row.ColumnInfo;
import org.yamcs.protobuf.Table.StreamInfo;
import org.yamcs.protobuf.Table.TableData;
import org.yamcs.protobuf.Table.TableData.TableRecord;
import org.yamcs.protobuf.Table.TableInfo;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DataType._type;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import com.google.protobuf.ByteString;

public class TableApi extends AbstractTableApi<Context> {

    private static final Log log = new Log(TableApi.class);

    @Override
    public void listStreams(Context ctx, ListStreamsRequest request, Observer<ListStreamsResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        List<Stream> streams = new ArrayList<>(ydb.getStreams());
        streams.sort((s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
        for (Stream stream : streams) {
            if (!RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.Stream, stream.getName())) {
                continue;
            }
            responseb.addStreams(ArchiveHelper.toStreamInfo(stream));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getStream(Context ctx, GetStreamRequest request, Observer<StreamInfo> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(ctx.user, ydb, request.getName());

        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        observer.complete(response);
    }

    @Override
    public void listTables(Context ctx, ListTablesRequest request, Observer<ListTablesResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadTables);

        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListTablesResponse.Builder responseb = ListTablesResponse.newBuilder();
        List<TableDefinition> defs = new ArrayList<>(ydb.getTableDefinitions());
        defs.sort((d1, d2) -> d1.getName().compareToIgnoreCase(d2.getName()));
        for (TableDefinition def : defs) {
            responseb.addTables(ArchiveHelper.toTableInfo(def));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTable(Context ctx, GetTableRequest request, Observer<TableInfo> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadTables);

        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(ydb, request.getName());

        TableInfo response = ArchiveHelper.toTableInfo(table);
        observer.complete(response);
    }

    @Override
    public void getTableData(Context ctx, GetTableDataRequest request, Observer<TableData> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadTables);

        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(ydb, request.getName());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;

        List<Object> args = new ArrayList<>();
        SqlBuilder sqlb = new SqlBuilder(table.getName());

        if (request.getColsCount() > 0) {
            request.getColsList().forEach(col -> {
                sqlb.select("?");
                args.add(col);
            });
        }

        sqlb.descend(!request.getOrder().equals("asc"));
        sqlb.limit(pos, limit);

        String sql = sqlb.toString();
        TableData.Builder responseb = TableData.newBuilder();
        RestStreams.stream(instance, sql, args, new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                responseb.addRecord(rec); // TODO estimate byte size
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void readRows(Context ctx, ReadRowsRequest request, Observer<Row> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadTables);

        TableDefinition table = verifyTable(ydb, request.getName());

        SqlBuilder sqlb = new SqlBuilder(table.getName());
        request.getColsList().forEach(col -> sqlb.select(col));
        String sql = sqlb.toString();

        RestStreams.stream(instance, sql, new RowStreamer(observer));
    }

    @Override
    public void executeSql(Context ctx, ExecuteSqlRequest request, Observer<ExecuteSqlResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ControlArchiving);

        String instance = RestHandler.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ExecuteSqlResponse.Builder responseb = ExecuteSqlResponse.newBuilder();
        if (request.hasStatement()) {
            try {
                StreamSqlResult result = ydb.execute(request.getStatement());
                String stringOutput = result.toString();
                if (stringOutput != null) {
                    responseb.setResult(stringOutput);
                }
            } catch (ParseException e) {
                throw new BadRequestException(e);
            } catch (StreamSqlException e) {
                throw new InternalServerErrorException(e);
            }
        }
        observer.complete(responseb.build());
    }

    private Stream verifyStream(User user, YarchDatabaseInstance ydb, String streamName) {
        Stream stream = ydb.getStream(streamName);

        if (stream != null && !RestHandler.hasObjectPrivilege(user, ObjectPrivilegeType.Stream, streamName)) {
            log.warn("Stream {} found, but withheld due to insufficient privileges. Returning 404 instead",
                    streamName);
            stream = null;
        }

        if (stream == null) {
            throw new NotFoundException("No stream named '" + streamName + "' (instance: '" + ydb.getName() + "')");
        } else {
            return stream;
        }
    }

    private TableDefinition verifyTable(YarchDatabaseInstance ydb, String tableName) {
        TableDefinition table = ydb.getTable(tableName);
        if (table == null) {
            throw new NotFoundException("No table named '" + tableName + "' (instance: '" + ydb.getName() + "')");
        } else {
            return table;
        }
    }

    private static class RowStreamer implements StreamSubscriber {

        Observer<Row> observer;
        TupleDefinition completeTuple = new TupleDefinition();

        RowStreamer(Observer<Row> observer) {
            this.observer = observer;
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void onTuple(Stream stream, Tuple tuple) {
            if (observer.isCancelled()) {
                stream.close();
                return;
            }

            Row.Builder rowb = Row.newBuilder();
            for (int i = 0; i < tuple.size(); i++) {
                ColumnDefinition cd = tuple.getColumnDefinition(i);
                Object v = tuple.getColumn(i);
                int colId = completeTuple.getColumnIndex(cd.getName());
                if (colId == -1) {
                    completeTuple.addColumn(cd);
                    colId = completeTuple.getColumnIndex(cd.getName());
                    rowb.addColumn(ColumnInfo.newBuilder().setId(colId).setName(cd.getName())
                            .setType(cd.getType().toString()).build());
                }
                DataType type = cd.getType();

                ColumnSerializer cs;
                if (type.val == _type.ENUM) {
                    cs = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);
                } else if (type.val == _type.PROTOBUF) {
                    cs = ColumnSerializerFactory.getProtobufSerializer(cd);
                } else {
                    cs = ColumnSerializerFactory.getBasicColumnSerializer(cd.getType());
                }
                rowb.addCell(Cell.newBuilder()
                        .setColumnId(colId)
                        .setData(ByteString.copyFrom(cs.toByteArray(v)))
                        .build());
            }

            observer.next(rowb.build());
        }

        @Override
        public void streamClosed(Stream stream) {
            observer.complete();
        }
    }
}
