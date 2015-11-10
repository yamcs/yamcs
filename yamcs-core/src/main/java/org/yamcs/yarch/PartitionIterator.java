package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.yarch.PartitionManager.Interval;
import org.yamcs.yarch.PartitioningSpec._type;

public class PartitionIterator implements Iterator<List<Partition>> {
    final PartitioningSpec partitioningSpec;
    final Iterator<Entry<Long,Interval>> it;
    final Set<Object> partitionValueFilter;
    List<Partition> next;
    boolean reverse;
    long start;
    boolean jumpToStart=false;

    PartitionIterator(PartitioningSpec partitioningSpec, Iterator<Entry<Long,Interval>> it, Set<Object> partitionFilter, boolean reverse){
        this.partitioningSpec=partitioningSpec;
        this.it=it;
        this.partitionValueFilter=partitionFilter;
        this.reverse=reverse;
    }
    
    public void jumpToStart(long startInstant) {
        this.start=startInstant;
        jumpToStart=true;
    }


    @Override
    public boolean hasNext() {
        if(next!=null) return true;
        next=new ArrayList<Partition>();
    
        while(it.hasNext()) {
            Entry<Long,Interval> entry=it.next();
            Interval intv=entry.getValue();
            if(!reverse && jumpToStart && (intv.getEnd()<=start)) {
                continue;
            } else if(reverse && jumpToStart && (intv.getStart()>=start)) {
                continue;
            } else {
                jumpToStart=false;
            }
            if(partitioningSpec.type==_type.TIME) { 
                next.add(intv.partitions.values().iterator().next());
            } else {
                for(Partition p:intv.partitions.values()) {
                    if((partitionValueFilter==null) || (partitionValueFilter.contains(p.getValue()))) {
                        next.add(p);
                    }
                }
            }
            if(!next.isEmpty()) {
                break;
            }
        }
        if(next.isEmpty()) {
            next=null;
            return false;
        } else {
            return true;
        }
    }
    
    @Override
    public List<Partition> next() {
        List<Partition> ret=next;
        next=null;
        return ret;
    }
    
    @Override
    public void remove() {
        throw new UnsupportedOperationException("cannot remove partitions like this");
    }
}
