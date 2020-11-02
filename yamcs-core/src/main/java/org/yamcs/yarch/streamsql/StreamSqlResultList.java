package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.yamcs.yarch.Tuple;

/**
 * StreamSqlResult which stores a list of results
 * @author nm
 *
 */
public class StreamSqlResultList implements StreamSqlResult {

    List<Tuple> list = new ArrayList<>();
    Iterator<Tuple> iterator;
    
    StreamSqlResultList init() {
        iterator = list.iterator();
        return this;
    }
    
    public void addTuple(Tuple t) {
        list.add(t);
    }
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Tuple next() {
        return iterator.next();
    }

    @Override
    public void close() {
        //no resource to release (the list will be garbage collected)
    }
    
}
