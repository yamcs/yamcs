package org.yamcs.parameterarchive;

import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.parameter.Value;

public class TimedValue {
    final long instant;
    final Value engValue;
    final Value rawValue;
    final ParameterStatus paramStatus;
    
    public TimedValue(long instant, Value engValue, Value rawValue, ParameterStatus paramStatus) {
        this.instant = instant;
        this.engValue = engValue;
        this.rawValue = rawValue;
        this.paramStatus = paramStatus;
    }
}
