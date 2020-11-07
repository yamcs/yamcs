package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.LimitExceededException;

public class CompiledAggregateList implements CompiledAggregateExpression {
    final static int MAX_LENGTH = 1000;
    
    List<Tuple> list=new ArrayList<Tuple>();
    @Override
    public void clear() {
      list=new ArrayList<Tuple>();
    }

    @Override
    public Object getValue() {
        return list;
    }

    @Override
    public void newData(Tuple tuple) {
        if(list.size()>=MAX_LENGTH) {
            throw new LimitExceededException("To many elements in the aggregate list");
        }
        list.add(tuple);
    }

}
