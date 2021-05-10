package org.yamcs.parameterarchive;

/**
 * Iterator over values of one (parameterId, group id)
 */
public interface ParameterIterator extends ParchiveIterator<TimedValue> {
    public TimedValue value();

    public ParameterId getParameterId();

    public int getParameterGroupId();
}
