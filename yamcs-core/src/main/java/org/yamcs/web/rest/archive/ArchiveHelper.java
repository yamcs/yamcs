package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.ColumnData;
import org.yamcs.protobuf.Archive.ColumnInfo;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
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
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.archive.RestDownsampler.Sample;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

/**
 * Collects all archive-related conversions performed in the web api (x towards
 * archive.proto)
 */
public final class ArchiveHelper {

    final static TableInfo toTableInfo(TableDefinition def) {
        TableInfo.Builder infob = TableInfo.newBuilder();
        infob.setName(def.getName());
        for (ColumnDefinition cdef : def.getKeyDefinition().getColumnDefinitions()) {
            infob.addKeyColumn(toColumnInfo(cdef));
        }
        for (ColumnDefinition cdef : def.getValueDefinition().getColumnDefinitions()) {
            infob.addValueColumn(toColumnInfo(cdef));
        }
        return infob.build();
    }

    final static StreamInfo toStreamInfo(Stream stream) {
        StreamInfo.Builder infob = StreamInfo.newBuilder();
        infob.setName(stream.getName());
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            infob.addColumn(toColumnInfo(cdef));
        }
        return infob.build();
    }

    private static ColumnInfo toColumnInfo(ColumnDefinition cdef) {
        ColumnInfo.Builder infob = ColumnInfo.newBuilder();
        infob.setName(cdef.getName());
        infob.setType(cdef.getType().toString());
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
                break;
            case ENUM:
            case STRING:
                v.setType(Type.STRING);
                v.setStringValue((String) column);
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

    final static ReplayRequest toParameterReplayRequest(RestRequest req, Parameter p, boolean descendByDefault)
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
        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        rrb.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id));
        return rrb.build();
    }

    final static TimeSeries.Sample toGPBSample(Sample sample) {
        TimeSeries.Sample.Builder b = TimeSeries.Sample.newBuilder();
        b.setTime(TimeEncoding.toString(sample.avgt));
        b.setAvg(sample.avg);
        b.setMin(sample.min);
        b.setMax(sample.max);
        b.setN(sample.n);
        return b.build();
    }

    final static String[] EVENT_CSV_HEADER = new String[] { "Source", "Generation Time", "Reception Time", "Event Type",
            "Event Text" };

    final static String[] tupleToCSVEvent(Tuple tuple) {
        Event event = tupleToEvent(tuple);
        return new String[] { event.getSource(), event.getGenerationTimeUTC(), event.getReceptionTimeUTC(),
                event.getType(), event.getMessage() };
    }

    final static Event tupleToEvent(Tuple tuple) {
        Event.Builder event = Event.newBuilder((Event) tuple.getColumn("body"));
        event.setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()));
        event.setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()));
        return event.build();
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
