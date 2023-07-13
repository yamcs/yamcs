package org.yamcs.yfe;

import java.util.Map.Entry;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.xtce.Argument;
import org.yamcs.yfe.protobuf.Yfe.AggregateValue;
import org.yamcs.yfe.protobuf.Yfe.ArrayValue;
import org.yamcs.yfe.protobuf.Yfe.CommandAssignment;
import org.yamcs.yfe.protobuf.Yfe.EnumeratedValue;
import org.yamcs.yfe.protobuf.Yfe.Timestamp;
import org.yamcs.yfe.protobuf.Yfe.Value;

import com.google.protobuf.ByteString;

public class ProtoConverter {
    static org.yamcs.yfe.protobuf.Yfe.PreparedCommand toProto(PreparedCommand pc) {
        org.yamcs.yfe.protobuf.Yfe.PreparedCommand.Builder ypcb = org.yamcs.yfe.protobuf.Yfe.PreparedCommand
                .newBuilder()
                .setCommandId(pc.getCommandId());

        if (pc.getBinary() != null) {
            ypcb.setBinary(ByteString.copyFrom(pc.getBinary()));
        }
        if (pc.getArgAssignment() != null) {
            for (Entry<Argument, ArgumentValue> entry : pc.getArgAssignment().entrySet()) {
                ypcb.addAssignments(toProto(entry.getKey().getName(), entry.getValue()));
            }
        }

        return ypcb.build();

    }

    static CommandAssignment toProto(String argName, ArgumentValue argValue) {
        var cab = CommandAssignment.newBuilder().setName(argName);
        if (argValue.getEngValue() != null) {
            cab.setEngValue(toProto(argValue.getEngValue()));
        }
        if (argValue.getRawValue() != null) {
            cab.setEngValue(toProto(argValue.getRawValue()));
        }
        return cab.build();

    }

    static Value toProto(org.yamcs.parameter.Value v) {
        Value.Builder valueBuilder = Value.newBuilder();

        if (v instanceof org.yamcs.parameter.AggregateValue) {
            valueBuilder.setAggregateValue(toProto((org.yamcs.parameter.AggregateValue) v));
        } else if (v instanceof org.yamcs.parameter.ArrayValue) {
            valueBuilder.setArrayValue(toProto((org.yamcs.parameter.ArrayValue) v));
        } else if (v instanceof org.yamcs.parameter.BinaryValue) {
            valueBuilder.setBinaryValue(ByteString.copyFrom(v.getBinaryValue()));
        } else if (v instanceof org.yamcs.parameter.BooleanValue) {
            valueBuilder.setBooleanValue(v.getBooleanValue());
        } else if (v instanceof org.yamcs.parameter.DoubleValue) {
            valueBuilder.setDoubleValue(v.getDoubleValue());
        } else if (v instanceof org.yamcs.parameter.EnumeratedValue) {
            valueBuilder.setEnumeratedValue(toProto((org.yamcs.parameter.EnumeratedValue) v));
        } else if (v instanceof org.yamcs.parameter.FloatValue) {
            valueBuilder.setFloatValue(v.getFloatValue());
        } else if (v instanceof org.yamcs.parameter.SInt32Value) {
            valueBuilder.setSint32Value(v.getSint32Value());
        } else if (v instanceof org.yamcs.parameter.SInt64Value) {
            valueBuilder.setSint64Value(v.getSint64Value());
        } else if (v instanceof org.yamcs.parameter.StringValue) {
            valueBuilder.setStringValue(v.getStringValue());
        } else if (v instanceof org.yamcs.parameter.TimestampValue) {
            valueBuilder.setTimestampValue(toProto((org.yamcs.parameter.TimestampValue) v));
        } else if (v instanceof org.yamcs.parameter.UInt32Value) {
            valueBuilder.setUint32Value(v.getUint32Value());
        } else if (v instanceof org.yamcs.parameter.UInt64Value) {
            valueBuilder.setUint64Value(v.getUint64Value());
        } else {
            throw new IllegalStateException("Unknown value type " + v.getClass());
        }

        return valueBuilder.build();
    }

    static AggregateValue toProto(org.yamcs.parameter.AggregateValue av) {
        var avb = AggregateValue.newBuilder();
        int n = av.numMembers();
        for (int i = 0; i < n; i++) {
            var mv = av.getMemberValue(i);
            if (mv != null) {
                avb.addName(av.getMemberName(i));
                avb.addValue(toProto(mv));
            }
        }
        return avb.build();
    }

    static EnumeratedValue toProto(org.yamcs.parameter.EnumeratedValue ev) {
        return EnumeratedValue.newBuilder().setSint64Value(ev.getSint64Value()).setStringValue(ev.getStringValue())
                .build();
    }

    static ArrayValue toProto(org.yamcs.parameter.ArrayValue av) {
        var avb = ArrayValue.newBuilder();

        int n = av.flatLength();
        for (int i = 0; i < n; i++) {
            avb.addValue(toProto(av.getElementValue(i)));
        }
        return avb.build();
    }

    static Timestamp toProto(org.yamcs.parameter.TimestampValue tv) {
        return Timestamp.newBuilder().setMillis(tv.millis()).setNanos(tv.nanos()).build();

    }
}
