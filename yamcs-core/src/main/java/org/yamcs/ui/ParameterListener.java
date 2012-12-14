package org.yamcs.ui;

import java.util.List;

import org.yamcs.protobuf.Pvalue.ParameterValue;

public interface ParameterListener {
	public boolean isCanceled();
	public void exception(final Exception e);
	public void updateParameters(List<ParameterValue> paramList);
	public void replayFinished();
}
