package org.yamcs.commanding;

import org.yamcs.parameter.RawEngValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;

public class ArgumentValue extends RawEngValue {
    private final Argument argument;

    public ArgumentValue(Argument argument) {
        this.argument = argument;
    }

    public ArgumentValue(Argument argument, Value engValue) {
        this.argument = argument;
        this.engValue = engValue;
    }

    public Argument getArgument() {
        return argument;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ");
        sb.append(argument.getName());

        if (rawValue != null) {
            sb.append(" rawValue: {").append(rawValue.toString()).append("}");
        }
        if (engValue != null) {
            sb.append(" engValue: {").append(engValue.toString()).append("}");
        }
        return sb.toString();
    }

    public ParameterValue toGpb() {
        ParameterValue.Builder gpvb = ParameterValue.newBuilder()
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(generationTime));
        if (engValue != null) {
            gpvb.setEngValue(ValueUtility.toGbp(engValue));
        }
        if (rawValue != null) {
            gpvb.setEngValue(ValueUtility.toGbp(rawValue));
        }

        return gpvb.build();
    }
}
