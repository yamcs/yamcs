package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
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
import org.yamcs.protobuf.Table.WriteRowsRequest;
import org.yamcs.protobuf.Table.WriteRowsResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
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
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        List<Stream> streams = new ArrayList<>(ydb.getStreams());
        streams.sort((s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
        for (Stream stream : streams) {
            if (!ctx.user.hasObjectPrivilege(ObjectPrivilegeType.Stream, stream.getName())) {
                continue;
            }
            responseb.addStreams(ArchiveHelper.toStreamInfo(stream));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getStream(Context ctx, GetStreamRequest request, Observer<StreamInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(ctx, ydb, request.getName());

        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        observer.complete(response);
    }

    @Override
    public void listTables(Context ctx, ListTablesRequest request, Observer<ListTablesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTables);

        String instance = ManagementApi.verifyInstance(request.getInstance());
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
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTables);

        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(ydb, request.getName());

        TableInfo response = ArchiveHelper.toTableInfo(table);
        observer.complete(response);
    }

    @Override
    public void getTableData(Context ctx, GetTableDataRequest request, Observer<TableData> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTables);

        String instance = ManagementApi.verifyInstance(request.getInstance());
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
        StreamFactory.stream(instance, sql, args, new StreamSubscriber() {

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
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTables);

        TableDefinition table = verifyTable(ydb, request.getTable());

        SqlBuilder sqlb = new SqlBuilder(table.getName());
        request.getColsList().forEach(col -> sqlb.select(col));
        String sql = sqlb.toString();

        StreamFactory.stream(instance, sql, new RowReader(observer));
    }

    @Override
    public Observer<WriteRowsRequest> writeRows(Context ctx, Observer<WriteRowsResponse> observer) {
        if (!ctx.user.hasSystemPrivilege(SystemPrivilege.WriteTables)) {
            throw new ForbiddenException("Insufficient privileges");
        }

        return new Observer<WriteRowsRequest>() {

            Map<Integer, ColumnSerializer<?>> serializers = new HashMap<>();
            Map<Integer, ColumnDefinition> colDefinitions = new HashMap<>();
            static final int MAX_COLUMNS = 65535;

            boolean errorState = false;
            Stream inputStream;
            int count = 0;

            @Override
            public void next(WriteRowsRequest request) {
                if (errorState) {
                    return;
                }

                if (count == 0) {
                    String instance = request.getInstance();
                    if (!YamcsServer.hasInstance(instance)) {
                        completeExceptionally(new NotFoundException("No instance named '" + instance + "'"));
                        return;
                    }
                    YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

                    String tableName = request.getTable();
                    TableDefinition table = ydb.getTable(tableName);
                    if (table == null) {
                        throw new NotFoundException(
                                "No table named '" + tableName + "' (instance: '" + instance + "')");
                    }
                    inputStream = StreamFactory.insertStream(instance, table);
                }

                if (request.hasRow()) {
                    try {
                        Tuple t = null; /// rowToTuple(request.getRow());
                        inputStream.emitTuple(t);
                        count++;
                    } catch (IllegalArgumentException e) {
                        completeExceptionally(e);
                    }
                }
            }

            @Override
            public void completeExceptionally(Throwable t) {
                errorState = true;
                /// sendErrorAndCloseAfter2Seconds(ctx, HttpResponseStatus.BAD_REQUEST, t.toString());
                inputStream.close();
            }

            @Override
            public void complete() {
                log.debug("Wrote {} rows", count);
                WriteRowsResponse.Builder responseb = WriteRowsResponse.newBuilder()
                        .setCount(count);
                observer.complete(responseb.build());
            }

            private Tuple rowToTuple(Row row) throws IOException {
                for (ColumnInfo cinfo : row.getColumnList()) {
                    if (!cinfo.hasId() || !cinfo.hasName() || !cinfo.hasType()) {
                        throw new IllegalArgumentException(
                                "Invalid row provided, no id or name  or type in the column info");
                    }
                    int colId = cinfo.getId();
                    String cname = cinfo.getName();
                    String ctype = cinfo.getType();
                    DataType type = DataType.byName(ctype);
                    ColumnDefinition cd = new ColumnDefinition(cname, type);
                    ColumnSerializer<?> cs;
                    if (type.val == _type.PROTOBUF) {
                        cs = ColumnSerializerFactory.getProtobufSerializer(cd);
                    } else if (type.val == _type.ENUM) {
                        cs = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);
                    } else {
                        cs = ColumnSerializerFactory.getBasicColumnSerializer(type);
                    }
                    serializers.put(colId, cs);
                    colDefinitions.put(colId, cd);
                    if (serializers.size() > MAX_COLUMNS) {
                        throw new IllegalArgumentException("Too many columns specified");
                    }
                }
                TupleDefinition tdef = new TupleDefinition();
                List<Object> values = new ArrayList<>(row.getCellCount());
                for (Cell cell : row.getCellList()) {
                    if (!cell.hasColumnId() || !cell.hasData()) {
                        throw new IllegalArgumentException("Invalid cell provided, no id or no data");
                    }
                    int colId = cell.getColumnId();
                    ColumnDefinition cd = colDefinitions.get(colId);
                    if (cd == null) {
                        throw new IllegalArgumentException("Invalid column id " + colId
                                + " specified. It has to be defined  by a the ColumnInfo message");
                    }
                    tdef.addColumn(cd);
                    ColumnSerializer<?> cs = serializers.get(colId);
                    Object v = cs.fromByteArray(cell.getData().toByteArray(), cd);
                    values.add(v);
                }
                return new Tuple(tdef, values);
            }
        };
    }

    @Override
    public void executeSql(Context ctx, ExecuteSqlRequest request, Observer<ExecuteSqlResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        String instance = ManagementApi.verifyInstance(request.getInstance());
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

    private Stream verifyStream(Context ctx, YarchDatabaseInstance ydb, String streamName) {
        Stream stream = ydb.getStream(streamName);

        if (stream != null && !ctx.user.hasObjectPrivilege(ObjectPrivilegeType.Stream, streamName)) {
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

    private static class RowReader implements StreamSubscriber {

        Observer<Row> observer;
        TupleDefinition completeTuple = new TupleDefinition();

        RowReader(Observer<Row> observer) {
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
