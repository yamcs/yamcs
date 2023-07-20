package org.yamcs.yfe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

//import org.objectweb.asm.Type;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.Member;
import org.yamcs.yfe.protobuf.Yfe.AggregateValue;
import org.yamcs.yfe.protobuf.Yfe.AggregateValueOrBuilder;
import org.yamcs.yfe.protobuf.Yfe.ArrayValue;
import org.yamcs.yfe.protobuf.Yfe.CommandAssignment;
import org.yamcs.yfe.protobuf.Yfe.EnumeratedValue;
import org.yamcs.yfe.protobuf.Yfe.Timestamp;
import org.yamcs.yfe.protobuf.Yfe.Value;
import org.yamcs.parameter.*;
import org.yamcs.parameterarchive.*;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.util.AggregateMemberNames;
import org.yamcs.protobuf.Yamcs.Value.Type;
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

    static org.yamcs.parameter.Value fromProto(Value protoValue) {
        org.yamcs.parameter.Value value;
    
        switch (protoValue.getVCase()) {
            case FLOATVALUE:
                value = new FloatValue(protoValue.getFloatValue());
                break;
            case DOUBLEVALUE:
                value = new DoubleValue(protoValue.getDoubleValue());
                break;
            case SINT32VALUE:
                value = new SInt32Value(protoValue.getSint32Value());
                break;
            case UINT32VALUE:
                value = new UInt32Value(protoValue.getUint32Value());
                break;
            case BINARYVALUE:
                value = new BinaryValue(protoValue.getBinaryValue().toByteArray());
                break;
            case STRINGVALUE:
                value = new StringValue(protoValue.getStringValue());
                break;
            case TIMESTAMPVALUE:
                value = fromProto(protoValue.getTimestampValue());
                break;
            case UINT64VALUE:
                value = new UInt64Value(protoValue.getUint64Value());
                break;
            case SINT64VALUE:
                value = new SInt64Value(protoValue.getSint64Value());
                break;
            case BOOLEANVALUE:
                value = new BooleanValue(protoValue.getBooleanValue());
                break;
            case AGGREGATEVALUE:
                value = fromProto(protoValue.getAggregateValue());
                break;
            case ENUMERATEDVALUE:
                value = fromProto(protoValue.getEnumeratedValue());
                break;
            case ARRAYVALUE:
                value = fromProto(protoValue.getArrayValue());
                break;
            case V_NOT_SET:
                throw new IllegalArgumentException("Value field not set in the protobuf.");
            default:
                throw new IllegalArgumentException("Unknown value type in the protobuf: " + protoValue.getVCase());
        }
    
        return value;
    }

    static org.yamcs.parameter.Value fromProto(AggregateValue protoAggregateValue) {
        AggregateParameterType.Builder aggrTypeBuilder = new AggregateParameterType.Builder();

        protoAggregateValue.getNameList().forEach(
            (name)-> aggrTypeBuilder.addMember(new Member(name)));
        AggregateParameterType aggtype = aggrTypeBuilder.build();
        org.yamcs.parameter.AggregateValue agv = new org.yamcs.parameter.AggregateValue(aggtype.getMemberNames());
        
        for (int i = 0; i < protoAggregateValue.getValueCount(); i++) {
            agv.setMemberValue(i, fromProto(protoAggregateValue.getValue(i)));
        }

        return agv;
    }
    
    static org.yamcs.parameter.Value fromProto(EnumeratedValue protoEnumeratedValue) {
        long longValue = protoEnumeratedValue.getSint64Value();
        String stringValue = protoEnumeratedValue.getStringValue();
        org.yamcs.parameter.EnumeratedValue enumeratedValue = new org.yamcs.parameter.EnumeratedValue(longValue, stringValue);
        return enumeratedValue;
    }
    
    static org.yamcs.parameter.Value fromProto(ArrayValue protoArrayValue) {
        int[] dimensions = {protoArrayValue.getValueCount()};
        org.yamcs.parameter.ArrayValue arrval = 
        new org.yamcs.parameter.ArrayValue(dimensions, convertType(protoArrayValue.getValue(0)));
        for (int i = 0; i < protoArrayValue.getValueCount(); i++) {
            arrval.setElementValue(i, fromProto(protoArrayValue.getValue(i)));
        }
    
        return arrval;
    }
    
    static org.yamcs.parameter.Value fromProto(Timestamp protoTimestamp) {
        long millis = protoTimestamp.getMillis();
        int nanos = (int)protoTimestamp.getNanos();
    
        return new TimestampValue(millis, nanos);
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


    private static org.yamcs.protobuf.Yamcs.Value.Type convertType(Value protoType) {
        switch (protoType.getVCase()) {
            case FLOATVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.FLOAT;
            case DOUBLEVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.DOUBLE;
            case SINT32VALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.SINT32;
            case UINT32VALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.UINT32;
            case BINARYVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.BINARY;
            case STRINGVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.STRING;
            case TIMESTAMPVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.TIMESTAMP;
            case UINT64VALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.UINT64;
            case SINT64VALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.SINT64;
            case BOOLEANVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.BOOLEAN;
            case AGGREGATEVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.AGGREGATE;
            case ENUMERATEDVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.ENUMERATED;
            case ARRAYVALUE:
                return org.yamcs.protobuf.Yamcs.Value.Type.ARRAY;
            default:
                throw new IllegalArgumentException("Unknown value type in the protobuf: " + protoType);
        }
    }

}
