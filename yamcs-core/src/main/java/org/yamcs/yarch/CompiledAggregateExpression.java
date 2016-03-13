package org.yamcs.yarch;


public interface CompiledAggregateExpression {
    public void newData(Tuple tuple);

    public Object getValue();
    public void clear();

}
