package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.List;

public class CompiledAggregateList implements CompiledAggregateExpression {

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
        list.add(tuple);
    }

}
