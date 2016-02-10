package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.streamsql.StreamSqlException;


public class MergeStream extends AbstractStream implements StreamSubscriber, Runnable{
    private Map<AbstractStream, LinkedBlockingQueue<Tuple>> tupleQueues;
    private PriorityQueue<TupleQueuePair> orderedQueue;
    Stream[] streams;
    private Tuple queueEndMark=new Tuple(new TupleDefinition(), new ArrayList<Object>());
    static AtomicInteger counter=new AtomicInteger();
    private volatile boolean quitting=false;
    private final String mergeColumn;

    public MergeStream(YarchDatabase ydb, AbstractStream[] streams, String mergeColumn, boolean ascending) throws StreamSqlException {
        //TODO check that the streams columns have compatible names and types
        super(ydb, getStreamName(streams), streams[0].getDefinition());
        this.streams=streams;
        this.mergeColumn=mergeColumn;

        if(ascending) {
            orderedQueue=new PriorityQueue<>();
        } else {
            orderedQueue=new PriorityQueue<>(REVERSE_COMPARATOR);
        }

        Map<AbstractStream, LinkedBlockingQueue<Tuple>> t=new HashMap<AbstractStream, LinkedBlockingQueue<Tuple>>();

        for(AbstractStream s:streams) {
            t.put(s, new LinkedBlockingQueue<Tuple>(50));
        }
        tupleQueues=Collections.unmodifiableMap(t);

        for(Stream s:streams) {
            s.addSubscriber(this);
        }
    }

    private static String getStreamName(Stream[] streams) {
        StringBuilder sb=new StringBuilder();
        sb.append("merge").append(counter.getAndIncrement());
        /*		for(Stream s:streams) {
			sb.append("_").append(s.getName());
		}*/
        return sb.toString();
    }


    @Override
    public void onTuple(Stream s, Tuple tuple) {
        try {
            tupleQueues.get(s).put(tuple);
        } catch (InterruptedException e) {
            log.info("got InterruptedException when writing data to the queue");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void streamClosed(Stream s) {
        if(state==QUITTING)return;
        log.debug("Got stream closed for "+s);
        try {
            tupleQueues.get(s).put(queueEndMark);
        } catch (InterruptedException e) {
            log.info("got InterruptedException when writing the end mark to the queue");
            Thread.currentThread().interrupt();
        }
        //	tupleQueues.remove(s);
    }

    @Override
    public void start() {
        log.info("starting the merge stream");
        //first start all the substreams
        for(Stream s:streams) {
            s.start();
        }
        //now start the thread that collects data from the substreams
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            //first wait for all the queues to have at least a tuple
            log.debug("waiting for at least one tuple in each queue");
            for(LinkedBlockingQueue<Tuple>q :tupleQueues.values()) {
                Tuple t=q.take();
                if(t==queueEndMark) continue;//this queue is finished, ignore it
                orderedQueue.add(new TupleQueuePair(q,t));
            }
            log.debug("got one tuple from each stream, starting the business");

            //now continue publishing the first element from the priority queue till it becomes empty
            while(orderedQueue.size()>0){
                TupleQueuePair tq=orderedQueue.poll();
                if(state==QUITTING) break; 
                emitTuple(tq.t);
                //get a new tuple from the queue from which the previous one has been sent 
                Tuple t=tq.q.take();
                if(t==queueEndMark) continue;//this queue is finished, ignore it
                if(!t.hasColumn(mergeColumn)) {
                    log.warn("Ignoring tuple because it does not have column {}", mergeColumn);
                }

                orderedQueue.add(new TupleQueuePair(tq.q,t));
            }
            close();
        } catch (InterruptedException e) {
            log.info("Got interrupted exception, quitting");
            return;
        }
    }

    @Override
    protected void doClose() {
        quitting=true;
        for(Stream s:streams) {
            s.close();
        }
    }

    private static final Comparator<TupleQueuePair> REVERSE_COMPARATOR=new Comparator<TupleQueuePair>() {
        @Override
        public int compare(TupleQueuePair o1, TupleQueuePair o2) {
            return -o1.compareTo(o2);
        }
    };

    class TupleQueuePair implements Comparable<TupleQueuePair> {

        LinkedBlockingQueue<Tuple> q;
        Tuple t;

        TupleQueuePair(LinkedBlockingQueue<Tuple> q, Tuple t) {
            this.q=q;
            this.t=t;
        }

        @Override
        public int compareTo(TupleQueuePair o) {
            return DataType.compare(t.getColumn(mergeColumn),o.t.getColumn(mergeColumn));
        }
    }
}
