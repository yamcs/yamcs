package org.yamcs.syspp;

import java.util.Collection;

import org.yamcs.protobuf.Pvalue.ParameterValue;


public interface SystemVariablesProvider {
    public Collection<ParameterValue> getParameters();
}
