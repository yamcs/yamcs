package org.yamcs.yarch;


public class CompiledFirstVal implements CompiledAggregateExpression {
    String[] args;
    boolean star;
    Object firstVal;
    
    public CompiledFirstVal(String[] args, boolean star) {
        this.args=args;
        this.star=star;
    }

    @Override
    public void clear() {
       firstVal=null;
    }

    @Override
    public Object getValue() {
        return firstVal;
    }

    @Override
    public void newData(Tuple tuple) {
        if(firstVal==null) {
            if(star) {
                firstVal=tuple;
            } else if(args.length==1) {
                firstVal=tuple.getColumn(args[0]);
            } else {
                //TODO
            }
        }
    }

}
