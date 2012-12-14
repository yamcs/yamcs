package org.yamcs.yarch;


public interface CompiledAggregateExpression {
    public void newData(Tuple tuple);
    /**
     * New tuple to consider
     * @param tuple
     */
    public Object getValue();
    public void clear();

}
