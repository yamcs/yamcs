package org.yamcs.http.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementService;
import org.yamcs.management.TableStreamListener;
import org.yamcs.protobuf.AbstractTableApi;
import org.yamcs.protobuf.StreamEvent;
import org.yamcs.protobuf.Table.ColumnData;
import org.yamcs.protobuf.Table.ColumnInfo;
import org.yamcs.protobuf.Table.EnumValue;
import org.yamcs.protobuf.Table.ExecuteSqlRequest;
import org.yamcs.protobuf.Table.ExecuteSqlResponse;
import org.yamcs.protobuf.Table.GetStreamRequest;
import org.yamcs.protobuf.Table.GetTableDataRequest;
import org.yamcs.protobuf.Table.GetTableRequest;
import org.yamcs.protobuf.Table.ListStreamsRequest;
import org.yamcs.protobuf.Table.ListStreamsResponse;
import org.yamcs.protobuf.Table.ListTablesRequest;
import org.yamcs.protobuf.Table.ListTablesResponse;
import org.yamcs.protobuf.Table.PartitioningInfo;
import org.yamcs.protobuf.Table.PartitioningInfo.PartitioningType;
import org.yamcs.protobuf.Table.ReadRowsRequest;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.protobuf.Table.Row.Cell;
import org.yamcs.protobuf.Table.StreamData;
import org.yamcs.protobuf.Table.StreamInfo;
import org.yamcs.protobuf.Table.SubscribeStreamRequest;
import org.yamcs.protobuf.Table.SubscribeStreamStatisticsRequest;
import org.yamcs.protobuf.Table.TableData;
import org.yamcs.protobuf.Table.TableData.TableRecord;
import org.yamcs.protobuf.Table.TableInfo;
import org.yamcs.protobuf.Table.WriteRowsExceptionDetail;
import org.yamcs.protobuf.Table.WriteRowsRequest;
import org.yamcs.protobuf.Table.WriteRowsResponse;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

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
            responseb.addStreams(toStreamInfo(stream));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void subscribeStreamStatistics(Context ctx, SubscribeStreamStatisticsRequest request,
            Observer<StreamEvent> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        for (Stream stream : ydb.getStreams()) {
            observer.next(StreamEvent.newBuilder()
                    .setType(StreamEvent.Type.CREATED)
                    .setName(stream.getName())
                    .setDataCount(stream.getDataCount())
                    .build());
        }

        TableStreamListener listener = new TableStreamListener() {
            @Override
            public void streamRegistered(String streamInstance, Stream stream) {
                if (streamInstance.equals(instance)) {
                    observer.next(StreamEvent.newBuilder()
                            .setType(StreamEvent.Type.CREATED)
                            .setName(stream.getName())
                            .setDataCount(stream.getDataCount())
                            .build());
                }
            }

            @Override
            public void streamUpdated(String streamInstance, StreamInfo stream) {
                if (streamInstance.equals(instance)) {
                    observer.next(StreamEvent.newBuilder()
                            .setType(StreamEvent.Type.UPDATED)
                            .setName(stream.getName())
                            .setDataCount(stream.getDataCount())
                            .build());
                }
            }

            @Override
            public void streamUnregistered(String streamInstance, String name) {
                if (streamInstance.equals(instance)) {
                    observer.next(StreamEvent.newBuilder()
                            .setType(StreamEvent.Type.DELETED)
                            .setName(name)
                            .build());
                }
            }
        };
        observer.setCancelHandler(() -> ManagementService.getInstance().removeTableStreamListener(listener));
        ManagementService.getInstance().addTableStreamListener(listener);
    }

    @Override
    public void getStream(Context ctx, GetStreamRequest request, Observer<StreamInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(ctx, ydb, request.getName());

        StreamInfo response = toStreamInfo(stream);
        observer.complete(response);
    }

    @Override
    public void subscribeStream(Context ctx, SubscribeStreamRequest request, Observer<StreamData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(ctx, ydb, request.getStream());

        StreamSubscriber listener = new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                observer.next(StreamData.newBuilder()
                        .setStream(stream.getName())
                        .addAllColumn(TableApi.toColumnDataList(tuple))
                        .build());
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        };
        observer.setCancelHandler(() -> stream.removeSubscriber(listener));
        stream.addSubscriber(listener);

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
            responseb.addTables(toTableInfo(def));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getTable(Context ctx, GetTableRequest request, Observer<TableInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTables);

        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(ydb, request.getName());

        TableInfo response = toTableInfo(table);
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
                rec.addAllColumn(toColumnDataList(tuple));
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

            Stream inputStream;
            int count = 0;

            @Override
            public void next(WriteRowsRequest request) {
                if (count == 0) {
                    String instance = request.getInstance();
                    if (!YamcsServer.hasInstance(instance)) {
                        throw new NotFoundException("No instance named '" + instance + "'");
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

                try {
                    if (request.hasRow()) {
                        Tuple t = rowToTuple(request.getRow());
                        inputStream.emitTuple(t);
                        count++;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void completeExceptionally(Throwable t) {
                if (inputStream != null) {
                    inputStream.close();
                }

                HttpException e;
                if (t instanceof HttpException) {
                    e = (HttpException) t;
                } else {
                    e = new InternalServerErrorException(t);
                }

                e.setDetail(WriteRowsExceptionDetail.newBuilder()
                        .setCount(count)
                        .build());

                observer.completeExceptionally(e);
            }

            @Override
            public void complete() {
                if (inputStream != null) {
                    inputStream.close();
                }

                log.debug("Wrote {} rows", count);
                WriteRowsResponse.Builder responseb = WriteRowsResponse.newBuilder()
                        .setCount(count);
                observer.complete(responseb.build());
            }

            private Tuple rowToTuple(Row row) throws IOException {
                for (Row.ColumnInfo cinfo : row.getColumnsList()) {
                    if (!cinfo.hasId() || !cinfo.hasName() || !cinfo.hasType()) {
                        throw new IllegalArgumentException(
                                "Invalid row provided, no id or name or type in the column info");
                    }
                    int colId = cinfo.getId();
                    String cname = cinfo.getName();
                    String ctype = cinfo.getType();
                    DataType type = DataType.byName(ctype);
                    ColumnDefinition cd = new ColumnDefinition(cname, type);
                    ColumnSerializer<?> cs;
                    if (type.val == DataType._type.PROTOBUF) {
                        cs = ColumnSerializerFactory.getProtobufSerializer(cd);
                    } else if (type.val == DataType._type.ENUM) {
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
                List<Object> values = new ArrayList<>(row.getCellsCount());
                for (Cell cell : row.getCellsList()) {
                    if (!cell.hasColumnId() || !cell.hasData()) {
                        throw new IllegalArgumentException("Invalid cell provided, no id or no data");
                    }
                    int colId = cell.getColumnId();
                    ColumnDefinition cd = colDefinitions.get(colId);
                    if (cd == null) {
                        throw new IllegalArgumentException("Invalid column id " + colId
                                + " specified. It has to be defined by the ColumnInfo message");
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
                    rowb.addColumns(Row.ColumnInfo.newBuilder().setId(colId).setName(cd.getName())
                            .setType(cd.getType().toString()).build());
                }
                DataType type = cd.getType();

                ColumnSerializer cs;
                if (type.val == DataType._type.ENUM) {
                    cs = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);
                } else if (type.val == DataType._type.PROTOBUF) {
                    cs = ColumnSerializerFactory.getProtobufSerializer(cd);
                } else {
                    cs = ColumnSerializerFactory.getBasicColumnSerializer(cd.getType());
                }
                rowb.addCells(Cell.newBuilder()
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

    private static TableInfo toTableInfo(TableDefinition def) {
        TableInfo.Builder infob = TableInfo.newBuilder();
        infob.setName(def.getName());
        infob.setCompressed(def.isCompressed());
        infob.setFormatVersion(def.getFormatVersion());
        infob.setStorageEngine(def.getStorageEngineName());
        if (def.getTablespaceName() != null) {
            infob.setTablespace(def.getTablespaceName());
        }
        if (def.hasHistogram()) {
            infob.addAllHistogramColumn(def.getHistogramColumns());
        }
        if (def.hasPartitioning()) {
            PartitioningInfo.Builder partb = PartitioningInfo.newBuilder();
            PartitioningSpec spec = def.getPartitioningSpec();
            switch (spec.type) {
            case TIME:
                partb.setType(PartitioningType.TIME);
                break;
            case VALUE:
                partb.setType(PartitioningType.VALUE);
                break;
            case TIME_AND_VALUE:
                partb.setType(PartitioningType.TIME_AND_VALUE);
                break;
            case NONE:
                break;
            default:
                throw new IllegalStateException("Unexpected partitioning type " + spec.type);
            }
            if (spec.type == PartitioningSpec._type.TIME || spec.type == PartitioningSpec._type.TIME_AND_VALUE) {
                if (spec.timeColumn != null) {
                    partb.setTimeColumn(spec.timeColumn);
                    partb.setTimePartitionSchema(spec.getTimePartitioningSchema().getName());
                }
            }
            if (spec.type == PartitioningSpec._type.VALUE || spec.type == PartitioningSpec._type.TIME_AND_VALUE) {
                if (spec.valueColumn != null) {
                    partb.setValueColumn(spec.valueColumn);
                    partb.setValueColumnType(spec.getValueColumnType().toString());
                }
            }

            if (spec.type != PartitioningSpec._type.NONE) {
                infob.setPartitioningInfo(partb);
            }
        }
        StringBuilder scriptb = new StringBuilder("create table ").append(def.toString());
        scriptb.append(" engine ").append(def.getStorageEngineName());
        if (def.hasHistogram()) {
            scriptb.append(" histogram(").append(String.join(", ", def.getHistogramColumns())).append(")");
        }
        if (def.hasPartitioning()) {
            PartitioningSpec spec = def.getPartitioningSpec();
            if (spec.type == PartitioningSpec._type.TIME) {
                scriptb.append(" partition by time(").append(spec.timeColumn)
                        .append("('").append(spec.getTimePartitioningSchema().getName()).append("'))");
            } else if (spec.type == PartitioningSpec._type.VALUE) {
                scriptb.append(" partition by value(").append(spec.valueColumn).append(")");
            } else if (spec.type == PartitioningSpec._type.TIME_AND_VALUE) {
                scriptb.append(" partition by time_and_value(").append(spec.timeColumn)
                        .append("('").append(spec.getTimePartitioningSchema().getName()).append("')")
                        .append(", ").append(spec.valueColumn).append(")");
            }
        }
        if (def.isCompressed()) {
            scriptb.append(" table_format=compressed");
        }
        infob.setScript(scriptb.toString());
        for (ColumnDefinition cdef : def.getKeyDefinition().getColumnDefinitions()) {
            infob.addKeyColumn(toColumnInfo(cdef, def));
        }
        for (ColumnDefinition cdef : def.getValueDefinition().getColumnDefinitions()) {
            infob.addValueColumn(toColumnInfo(cdef, def));
        }
        return infob.build();
    }

    private static StreamInfo toStreamInfo(Stream stream) {
        StreamInfo.Builder infob = StreamInfo.newBuilder();
        infob.setName(stream.getName());
        infob.setDataCount(stream.getDataCount());
        infob.setScript("create stream " + stream.getName() + stream.getDefinition().getStringDefinition());
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            infob.addColumn(toColumnInfo(cdef, null));
        }
        return infob.build();
    }

    private static ColumnInfo toColumnInfo(ColumnDefinition cdef, TableDefinition tableDefinition) {
        ColumnInfo.Builder infob = ColumnInfo.newBuilder();
        infob.setName(cdef.getName());
        infob.setType(cdef.getType().toString());
        if (tableDefinition != null && cdef.getType() == DataType.ENUM) {
            BiMap<String, Short> enumValues = tableDefinition.getEnumValues(cdef.getName());
            if (enumValues != null) {
                List<EnumValue> enumValueList = new ArrayList<>();
                for (Entry<String, Short> entry : enumValues.entrySet()) {
                    EnumValue val = EnumValue.newBuilder().setValue(entry.getValue()).setLabel(entry.getKey()).build();
                    enumValueList.add(val);
                }
                Collections.sort(enumValueList, (v1, v2) -> Integer.compare(v1.getValue(), v2.getValue()));
                infob.addAllEnumValue(enumValueList);
            }
        }
        return infob.build();
    }

    public final static List<ColumnData> toColumnDataList(Tuple tuple) {
        List<ColumnData> result = new ArrayList<>();
        int i = 0;
        for (Object column : tuple.getColumns()) {
            ColumnDefinition cdef = tuple.getColumnDefinition(i);

            Value.Builder v = Value.newBuilder();
            switch (cdef.getType().val) {
            case SHORT:
                v.setType(Type.SINT32);
                v.setSint32Value((Short) column);
                break;
            case DOUBLE:
                v.setType(Type.DOUBLE);
                v.setDoubleValue((Double) column);
                break;
            case BINARY:
                v.setType(Type.BINARY);
                v.setBinaryValue(ByteString.copyFrom((byte[]) column));
                break;
            case INT:
                v.setType(Type.SINT32);
                v.setSint32Value((Integer) column);
                break;
            case TIMESTAMP:
                v.setType(Type.TIMESTAMP);
                v.setTimestampValue((Long) column);
                v.setStringValue(TimeEncoding.toString((Long) column));
                break;
            case ENUM:
            case STRING:
                v.setType(Type.STRING);
                v.setStringValue((String) column);
                break;
            case BOOLEAN:
                v.setType(Type.BOOLEAN);
                v.setBooleanValue((Boolean) column);
                break;
            case LONG:
                v.setType(Type.SINT64);
                v.setSint64Value((Long) column);
                break;
            case PARAMETER_VALUE:
                org.yamcs.parameter.ParameterValue pv = (org.yamcs.parameter.ParameterValue) column;
                v = ValueUtility.toGbp(pv.getEngValue()).toBuilder();
                break;
            case PROTOBUF:
                MessageLite message = (MessageLite) column;
                v.setType(Type.BINARY);
                v.setBinaryValue(message.toByteString());
                break;
            default:
                throw new IllegalArgumentException(
                        "Tuple column type " + cdef.getType().val + " is currently not supported");
            }

            ColumnData.Builder colData = ColumnData.newBuilder();
            colData.setName(cdef.getName());
            colData.setValue(v);
            result.add(colData.build());
            i++;
        }
        return result;
    }
}
