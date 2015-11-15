package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Archive.ColumnData;
import org.yamcs.protobuf.Archive.ColumnInfo;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Pvalue.SampleSeries;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.TmProviderAdapter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.RestParameterSampler.Sample;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;

/**
 * Collects all archive-related conversions performed in the web api
 * (x towards archive.proto)
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
            default:
                throw new IllegalArgumentException("Tuple column type " + cdef.getType().val + " is currently not supported");
            }
            
            ColumnData.Builder colData = ColumnData.newBuilder();
            colData.setName(cdef.getName());
            colData.setValue(v);
            result.add(colData.build());
            i++;
        }
        return result;
    }
    
    final static ReplayRequest toParameterReplayRequest(RestRequest req, NamedObjectId id, boolean descendByDefault) throws RestException {
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasStart()) {
            rrb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            rrb.setStop(ir.getStop());
        }
        rrb.setEndAction(EndAction.QUIT);
        rrb.setReverse(RestUtils.asksDescending(req, descendByDefault));
        rrb.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id));
        return rrb.build();
    }
    
    final static SampleSeries.Sample toGPBSample(Sample sample) {
        SampleSeries.Sample.Builder b = SampleSeries.Sample.newBuilder();
        b.setAverageGenerationTime(sample.avgt);
        b.setAverageGenerationTimeUTC(TimeEncoding.toString(sample.avgt));
        b.setAverageValue(sample.avg);
        b.setLowValue(sample.low);
        b.setHighValue(sample.high);
        b.setN(sample.n);
        return b.build();
    }
    
    final static String[] EVENT_CSV_HEADER = new String[] {"Source","Generation Time","Reception Time","Event Type","Event Text"};
    
    final static String[] tupleToCSVEvent(Tuple tuple) {
        Event event = tupleToEvent(tuple);
        return new String[] {
                event.getSource(),
                event.getGenerationTimeUTC(),
                event.getReceptionTimeUTC(),
                event.getType(),
                event.getMessage()
        };
    }
    
    final static Event tupleToEvent(Tuple tuple) {
        Event.Builder event = Event.newBuilder((Event) tuple.getColumn("body"));
        event.setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()));
        event.setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()));
        return event.build();
    }
    
    final static TmPacketData tupleToPacketData(Tuple tuple) {
        TmPacketData.Builder pdatab = TmPacketData.newBuilder();
        pdatab.setGenerationTime((Long) tuple.getColumn(TmProviderAdapter.GENTIME_COLUMN));
        pdatab.setReceptionTime((Long) tuple.getColumn(TmProviderAdapter.RECTIME_COLUMN));
        pdatab.setSequenceNumber((Integer) tuple.getColumn(TmProviderAdapter.SEQNUM_COLUMN));
        byte[] tmbody = (byte[]) tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
        pdatab.setPacket(ByteString.copyFrom(tmbody));
        return pdatab.build();
    }
}
