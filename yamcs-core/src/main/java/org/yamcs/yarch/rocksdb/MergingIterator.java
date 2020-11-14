package org.yamcs.yarch.rocksdb;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


/**
 * Merges data from multiple iterators sorting the output
 * 
 *
 * @author nm
 *
 */
class MergingIterator implements DbIterator {
    final Comparator<byte[]> keyComparator;
    PriorityQueue <DbIterator> priorityQueue;
    final List<DbIterator> itList;
    public MergingIterator(List<DbIterator> itList, Comparator<byte[]> keyComparator) {
        this.keyComparator = keyComparator;
        this.itList = itList;
        priorityQueue = new PriorityQueue<>(new IteratorComparator(keyComparator));
        init(itList);
        
    }
    
    private void init(List<DbIterator> itList) {
        for(DbIterator it:itList) {
            if(it.isValid()) {
                priorityQueue.add(it);
            } else {
                it.close();
            }
        }
    }
    
    @Override
    public boolean isValid() {
        return !priorityQueue.isEmpty();
    }

    @Override
    public void next() {
        DbIterator it  = priorityQueue.poll();
        it.next();
        if(it.isValid()) {
            priorityQueue.add(it);
        } else {
            it.close();
        }
        
    }

    @Override
    public void prev() {
        DbIterator it  = priorityQueue.poll();
        it.prev();
        if(it.isValid()) {
            priorityQueue.add(it);
        } else {
            it.close();
        }
    }

    /**
     * closes all subiterators
     */
    @Override
    public void close() {
        while(!priorityQueue.isEmpty()) {
            DbIterator it  = priorityQueue.poll();
            it.close();
        }
    }
    

    @Override
    public byte[] key() {
        DbIterator it  = priorityQueue.peek();
        return it.key();
    }

    @Override
    public byte[] value() {
        DbIterator it  = priorityQueue.peek();
        return it.value();
    }
    
    
    class IteratorComparator implements Comparator<DbIterator> {
        final Comparator<byte[]> keyComparator;
        public IteratorComparator(Comparator<byte[]> keyComparator) {
            this.keyComparator = keyComparator;
        }

        @Override
        public int compare(DbIterator it1, DbIterator it2) {
            return keyComparator.compare(it1.key(), it2.key());
        }
    }
}
