package org.yamcs.http.api.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.http.HttpException;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.api.archive.ParameterRanger.Range;
import org.yamcs.http.api.archive.RestDownsampler.Sample;
import org.yamcs.http.api.processor.ProcessorHelper;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Alarms.AlarmType;
import org.yamcs.protobuf.Alarms.ParameterAlarmData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.RocksDbDatabaseInfo;
import org.yamcs.protobuf.Table.ColumnData;
import org.yamcs.protobuf.Table.ColumnInfo;
import org.yamcs.protobuf.Table.EnumValue;
import org.yamcs.protobuf.Table.PartitioningInfo;
import org.yamcs.protobuf.Table.PartitioningInfo.PartitioningType;
import org.yamcs.protobuf.Table.StreamData;
import org.yamcs.protobuf.Table.StreamInfo;
import org.yamcs.protobuf.Table.TableInfo;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.rocksdb.Tablespace;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

/**
 * Collects all archive-related conversions performed in the web api (x towards archive.proto)
 */
public final class ArchiveHelper {

    public final static RocksDbDatabaseInfo toRocksDbDatabaseInfo(Tablespace tablespace, String dbPath) {
        RocksDbDatabaseInfo.Builder databaseb = RocksDbDatabaseInfo.newBuilder()
                .setTablespace(tablespace.getName())
                .setDataDir(tablespace.getDataDir())
                .setDbPath(dbPath);
        return databaseb.build();
    }

    public final static TableInfo toTableInfo(TableDefinition def) {
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
            if (spec.type == _type.TIME || spec.type == _type.TIME_AND_VALUE) {
                if (spec.timeColumn != null) {
                    partb.setTimeColumn(spec.timeColumn);
                    partb.setTimePartitionSchema(spec.getTimePartitioningSchema().getName());
                }
            }
            if (spec.type == _type.VALUE || spec.type == _type.TIME_AND_VALUE) {
                if (spec.valueColumn != null) {
                    partb.setValueColumn(spec.valueColumn);
                    partb.setValueColumnType(spec.getValueColumnType().toString());
                }
            }

            if (spec.type != _type.NONE) {
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
            if (spec.type == _type.TIME) {
                scriptb.append(" partition by time(").append(spec.timeColumn)
                        .append("('").append(spec.getTimePartitioningSchema().getName()).append("'))");
            } else if (spec.type == _type.VALUE) {
                scriptb.append(" partition by value(").append(spec.valueColumn).append(")");
            } else if (spec.type == _type.TIME_AND_VALUE) {
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

    public final static StreamInfo toStreamInfo(Stream stream) {
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

    public static StreamData toStreamData(Stream stream, Tuple tuple) {
        StreamData.Builder builder = StreamData.newBuilder();
        builder.setStream(stream.getName());
        builder.addAllColumn(toColumnDataList(tuple));
        return builder.build();
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
            case PARAMETER_VALUE:
                org.yamcs.parameter.ParameterValue pv = (org.yamcs.parameter.ParameterValue) column;
                v = ValueUtility.toGbp(pv.getEngValue()).toBuilder();
                break;
            case PROTOBUF:

                // Perhaps we could be a bit smarter here. Proto3 will have an
                // any-type
                // String messageClassname = protoType.substring(9,
                // protoType.length() - 1);
                // String schemaClassname =
                // messageClassname.replace("org.yamcs.protobuf.",
                // "org.yamcs.protobuf.Schema") + "$BuilderSchema";
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

    final static Tuple toTuple(TableDefinition tblDef, List<ColumnData> columnList) {
        List<Object> cvalues = new ArrayList<>();
        TupleDefinition tdef = new TupleDefinition();

        for (ColumnData cdata : columnList) {
            String cname = cdata.getName();
            ColumnDefinition cdef = tblDef.getColumnDefinition(cname);
            Object v = ValueUtility.getYarchValue(cdata.getValue());

            if (cdef == null) {
                cdef = new ColumnDefinition(cname, DataType.typeOf(v));
            } else {
                v = DataType.castAs(cdef.getType(), v);
            }
            tdef.addColumn(cdef);
            cvalues.add(v);
        }
        Tuple tuple = new Tuple(tdef, cvalues);

        return tuple;
    }

    public final static ReplayRequest toParameterReplayRequest(NamedObjectId parameterId, long start, long stop,
            boolean descend) throws HttpException {
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));

        if (start != TimeEncoding.INVALID_INSTANT) {
            rrb.setStart(start);
        }
        if (stop != TimeEncoding.INVALID_INSTANT) {
            rrb.setStop(stop);
        }
        rrb.setEndAction(EndAction.QUIT);
        rrb.setReverse(descend);
        rrb.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(parameterId));
        return rrb.build();
    }

    public final static TimeSeries.Sample toGPBSample(Sample sample) {
        TimeSeries.Sample.Builder b = TimeSeries.Sample.newBuilder();
        b.setTime(TimeEncoding.toString(sample.t));
        b.setN(sample.n);

        if (sample.n > 0) {
            b.setAvg(sample.avg);
            b.setMin(sample.min);
            b.setMax(sample.max);
        }

        return b.build();
    }

    public final static Ranges.Range toGPBRange(Range r) {
        Ranges.Range.Builder b = Ranges.Range.newBuilder();
        b.setTimeStart(TimeEncoding.toString(r.start));
        b.setTimeStop(TimeEncoding.toString(r.stop));
        b.setEngValue(ValueUtility.toGbp(r.v));
        b.setCount(r.count);
        return b.build();
    }

    public final static Event tupleToEvent(Tuple tuple, ProtobufRegistry protobufRegistry) {
        Event incoming = (Event) tuple.getColumn("body");
        Event event;
        try {
            event = Event.parseFrom(incoming.toByteArray(), protobufRegistry.getExtensionRegistry());
        } catch (InvalidProtocolBufferException e) {
            throw new UnsupportedOperationException(e);
        }

        Event.Builder eventb = Event.newBuilder(event);
        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
        return eventb.build();
    }

    final static ParameterAlarmData tupleToParameterAlarmData(Tuple tuple) {
        ParameterAlarmData.Builder alarmb = ParameterAlarmData.newBuilder();

        ParameterValue pval = (ParameterValue) tuple.getColumn("triggerPV");
        alarmb.setTriggerValue(pval);

        if (tuple.hasColumn("severityIncreasedPV")) {
            pval = (ParameterValue) tuple.getColumn("severityIncreasedPV");
            alarmb.setMostSevereValue(pval);
        }
        if (tuple.hasColumn("updatedPV")) {
            pval = (ParameterValue) tuple.getColumn("updatedPV");
            alarmb.setCurrentValue(pval);
        }

        return alarmb.build();
    }

    public final static AlarmData tupleToAlarmData(Tuple tuple, boolean detail) {
        AlarmData.Builder alarmb = AlarmData.newBuilder();
        alarmb.setSeqNum((int) tuple.getColumn("seqNum"));
        setAckInfo(alarmb, tuple);

        if (tuple.hasColumn("parameter")) {
            alarmb.setType(AlarmType.PARAMETER);
            ParameterValue pval = (ParameterValue) tuple.getColumn("triggerPV");
            alarmb.setId(pval.getId());
            alarmb.setTriggerTime(TimeEncoding.toProtobufTimestamp(pval.getGenerationTime()));

            if (tuple.hasColumn("severityIncreasedPV")) {
                pval = (ParameterValue) tuple.getColumn("severityIncreasedPV");
            }
            alarmb.setSeverity(ProcessorHelper.getParameterAlarmSeverity(pval.getMonitoringResult()));

            if (detail) {
                ParameterAlarmData parameterAlarmData = tupleToParameterAlarmData(tuple);
                alarmb.setParameterDetail(parameterAlarmData);
            }
        } else {
            alarmb.setType(AlarmType.EVENT);
            Event ev = (Event) tuple.getColumn("triggerEvent");
            alarmb.setTriggerTime(TimeEncoding.toProtobufTimestamp(ev.getGenerationTime()));
            alarmb.setId(ProcessorHelper.getAlarmId(ev));

            if (tuple.hasColumn("severityIncreasedEvent")) {
                ev = (Event) tuple.getColumn("severityIncreasedEvent");
            }
            alarmb.setSeverity(ProcessorHelper.getEventAlarmSeverity(ev.getSeverity()));
        }

        return alarmb.build();
    }

    static void setAckInfo(AlarmData.Builder alarmb, Tuple tuple) {
        if (tuple.hasColumn("acknowledgedBy")) {
            AcknowledgeInfo.Builder ackb = AcknowledgeInfo.newBuilder();
            ackb.setAcknowledgedBy((String) tuple.getColumn("acknowledgedBy"));
            if (tuple.hasColumn("acknowledgeMessage")) {
                ackb.setAcknowledgeMessage((String) tuple.getColumn("acknowledgeMessage"));
            }
            long acknowledgeTime = (Long) tuple.getColumn("acknowledgeTime");
            ackb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(acknowledgeTime));
            alarmb.setAcknowledgeInfo(ackb);
        }
    }
}
