package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.ColumnData;
import org.yamcs.protobuf.Archive.ColumnInfo;
import org.yamcs.protobuf.Archive.EnumValue;
import org.yamcs.protobuf.Archive.PartitioningInfo;
import org.yamcs.protobuf.Archive.PartitioningInfo.PartitioningType;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
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
import org.yamcs.web.GpbExtensionRegistry;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.archive.ParameterRanger.Range;
import org.yamcs.web.rest.archive.RestDownsampler.Sample;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import com.google.protobuf.MessageLite;

/**
 * Collects all archive-related conversions performed in the web api (x towards archive.proto)
 */
public final class ArchiveHelper {

    final static TableInfo toTableInfo(TableDefinition def) {
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

    final static StreamInfo toStreamInfo(Stream stream) {
        StreamInfo.Builder infob = StreamInfo.newBuilder();
        infob.setName(stream.getName());
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

    final static List<ColumnData> toColumnDataList(Tuple tuple) {
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

    final static ReplayRequest toParameterReplayRequest(RestRequest req, NamedObjectId parameterId,
            boolean descendByDefault)
            throws HttpException {
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            rrb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            rrb.setStop(ir.getStop());
        }
        rrb.setEndAction(EndAction.QUIT);
        rrb.setReverse(req.asksDescending(descendByDefault));
        rrb.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(parameterId));
        return rrb.build();
    }

    final static TimeSeries.Sample toGPBSample(Sample sample) {
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

    final static Ranges.Range toGPBRange(Range r) {
        Ranges.Range.Builder b = Ranges.Range.newBuilder();
        b.setTimeStart(TimeEncoding.toString(r.start));
        b.setTimeStop(TimeEncoding.toString(r.stop));
        b.setEngValue(ValueUtility.toGbp(r.v));
        b.setCount(r.count);
        return b.build();
    }

    final static String[] getEventCSVHeader(GpbExtensionRegistry extensionRegistry) {
        List<ExtensionInfo> extensionFields = extensionRegistry.getExtensions(Event.getDescriptor());
        String[] rec = new String[5 + extensionFields.size()];
        int i = 0;
        rec[i++] = "Source";
        rec[i++] = "Generation Time";
        rec[i++] = "Reception Time";
        rec[i++] = "Event Type";
        rec[i++] = "Event Text";
        for (ExtensionInfo extension : extensionFields) {
            rec[i++] = "" + extension.descriptor.getName();
        }
        return rec;
    }

    final static String[] tupleToCSVEvent(Tuple tuple, GpbExtensionRegistry extensionRegistry) {
        Event event = tupleToEvent(tuple, extensionRegistry);

        List<ExtensionInfo> extensionFields = extensionRegistry.getExtensions(Event.getDescriptor());

        String[] rec = new String[5 + extensionFields.size()];
        int i = 0;
        rec[i++] = event.getSource();
        rec[i++] = event.getGenerationTimeUTC();
        rec[i++] = event.getReceptionTimeUTC();
        rec[i++] = event.getType();
        rec[i++] = event.getMessage();
        for (ExtensionInfo extension : extensionFields) {
            rec[i++] = "" + event.getField(extension.descriptor);
        }
        return rec;
    }

    final static Event tupleToEvent(Tuple tuple, GpbExtensionRegistry extensionRegistry) {
        Event incoming = (Event) tuple.getColumn("body");
        Event event = extensionRegistry.getExtendedEvent(incoming);

        Event.Builder eventb = Event.newBuilder(event);
        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
        return eventb.build();
    }

    final static AlarmData tupleToAlarmData(Tuple tuple) {
        AlarmData.Builder alarmb = AlarmData.newBuilder();
        alarmb.setSeqNum((int) tuple.getColumn("seqNum"));

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
        if (tuple.hasColumn("acknowledgedBy")) {
            AcknowledgeInfo.Builder ackb = AcknowledgeInfo.newBuilder();
            ackb.setAcknowledgedBy((String) tuple.getColumn("acknowledgedBy"));
            if (tuple.hasColumn("acknowledgeMessage")) {
                ackb.setAcknowledgeMessage((String) tuple.getColumn("acknowledgeMessage"));
            }
            long acknowledgeTime = (Long) tuple.getColumn("acknowledgeTime");
            ackb.setAcknowledgeTime(acknowledgeTime);
            ackb.setAcknowledgeTimeUTC(TimeEncoding.toString(acknowledgeTime));
            alarmb.setAcknowledgeInfo(ackb);
        }

        return alarmb.build();
    }
}
