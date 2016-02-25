package org.yamcs.web;

import java.util.Map;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public interface Computation {

    public abstract String getExpression();

    public abstract String getName();

    public abstract org.yamcs.protobuf.Pvalue.ParameterValue evaluate(
            Map<NamedObjectId, ParameterValue> parameters);

}